package com.ivor.openstream.data.streaming

import android.content.Context
import android.content.SharedPreferences
import com.ivor.openstream.presentation.tv.TvDetect
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.StreamingRepository
import android.util.Log
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.SourceSearchMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class StreamingRepositoryImpl @Inject constructor(
    private val providerRegistry: ExtensionProviderRegistry,
    private val idMappingService: IdMappingService,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val preferences: SharedPreferences,
    private val appSettings: AppSettingsStore,
    private val streamCache: ResolvedStreamCache,
    @ApplicationContext private val context: Context
) : StreamingRepository {
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    /** Until when a provider is paused after repeated failures or access restrictions. */
    private val breakerUntilAt = ConcurrentHashMap<String, Long>()

    /**
     * Provider work runs here rather than inside the flow, so a provider stuck in a blocking HTTP
     * call can't hold the flow open past its deadline; it is cancelled when it stops mattering.
     */
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Bound costly native torrent, WebView, and HTTP resolver work on Android TV.
    private val providerSlots = Semaphore(
        if (TvDetect.isTv(context)) TV_MAX_CONCURRENT_PROVIDERS else MAX_CONCURRENT_PROVIDERS
    )

    /**
     * By default every installed provider runs at once and servers are sent as each one answers,
     * so playback can start on the first. This includes torrent/Stremio and backup
     * providers during the initial search rather than hiding them behind Find more.
     * [SourceSearchMode.IN_ORDER] retains its explicit sequential early-stop semantics.
     *
     * With [preferLastWorking] the source that last played something ([rememberWorkingSource]) is
     * asked first, on its own. If it has streams the search ends there; if it has none, or has not
     * answered within [REMEMBERED_HEAD_START_MS], the search above runs as usual.
     *
     * That automatic search (and only that one) answers from [ResolvedStreamCache] when the same
     * sources found streams for this episode a few minutes ago.
     */
    override fun resolveServers(
        identity: MediaIdentity,
        includeFallbacks: Boolean,
        preferLastWorking: Boolean
    ): Flow<ServerResolution> = channelFlow {
        val installedProviders = withContext(Dispatchers.IO) { providerRegistry.activeProviders() }
        val providerPriorities = installedProviders.associate { it.id to it.priority }
        // TVs cannot afford ongoing native-torrent discovery while Media3 buffers.
        // Automatic TV searches stop at the first provider with usable candidates;
        // the explicit Find more action still searches every enabled provider.
        val inOrder = appSettings.current.sourceSearchMode == SourceSearchMode.IN_ORDER ||
            SourceSearchPolicy.firstPlayableOnlyOnTv(TvDetect.isTv(context),
                preferLastWorking, includeFallbacks)
        val cacheable = preferLastWorking && !includeFallbacks
        val sources = sourcesFingerprint(installedProviders, inOrder)
        var partialSeed: ServerResolution? = null
        if (cacheable) {
            streamCache.get(identity, sources)?.let { cached ->
                val ranked = cached.copy(
                    servers = ServerRanker.mergeAndRank(
                        existing = emptyList(),
                        incoming = cached.servers,
                        providerPriorities = providerPriorities,
                        preferredServerId = preferences.getString(preferenceKey(identity), null),
                        providerFirst = inOrder,
                        qualityCapHeight = appSettings.current.streamMaxHeight
                    )
                )
                send(ranked)
                if (ranked.isComplete) return@channelFlow
                partialSeed = ranked
            }
        }

        suspend fun publish(resolution: ServerResolution) {
            // A successful explicit Find more also refreshes the automatic episode cache.
            // Failed/empty searches never delete the last successful set.
            streamCache.put(identity, sources, resolution)
            send(resolution)
        }

        val installedEnabled = installedProviders.filter { it.isEnabled }
        // "Procurar mais" is an explicit retry that bypasses provider backoff.
        // An automatic search must not call all broken endpoints again simply
        // because every provider is temporarily unavailable.
        val pausedProviders = if (includeFallbacks) emptyList() else
            installedEnabled.filter { isBreakerOpen(it.id) }
        val enabledProviders = installedEnabled.filterNot { provider ->
            !includeFallbacks && isBreakerOpen(provider.id)
        }
        // Not when everything is being searched on purpose ("Find more", failover).
        val remembered = if (preferLastWorking && !includeFallbacks && inOrder) {
            lastWorkingSourceId(identity)?.let { id -> enabledProviders.firstOrNull { it.id == id } }
        } else {
            null
        }
        val otherProviders = if (remembered == null) {
            enabledProviders
        } else {
            enabledProviders.filterNot { it.id == remembered.id }
        }
        // ALL_AT_ONCE means *all* installed extensions on the first request:
        // direct HTTP, Stremio, torrents and fallback engines. A fast HTTP answer
        // must not prevent torrents appearing alongside it in the picker.
        val searchEveryProvider = SourceSearchPolicy.includeAllProviders(
            appSettings.current.sourceSearchMode, includeFallbacks
        )
        val directProviders = if (searchEveryProvider) {
            otherProviders
        } else {
            otherProviders.filterNot(ExtensionStreamProvider::isFallback)
        }
        val fallbackProviders = if (searchEveryProvider) {
            emptyList()
        } else {
            otherProviders.filter(ExtensionStreamProvider::isFallback)
        }
        val firstStageProviders = directProviders.ifEmpty { fallbackProviders }
        val deferredFallbackProviders = fallbackProviders.takeIf { directProviders.isNotEmpty() }.orEmpty()
        // Shown straight away, before the id lookup, so the screen says sources are being searched.
        send(
            ServerResolution(
                servers = partialSeed?.servers.orEmpty(),
                totalProviders = when {
                    inOrder -> enabledProviders.size
                    remembered != null -> 1
                    else -> firstStageProviders.size
                }
            )
        )
        if (remembered == null && firstStageProviders.isEmpty()) {
            publish(ServerResolution(
                servers = partialSeed?.servers.orEmpty(),
                failedProviders = pausedProviders.map {
                    "${it.displayName}: temporariamente em pausa após falhas recentes"
                },
                isComplete = true
            ))
            return@channelFlow
        }

        // Providers that only know IMDb ids need this; the rest must not wait long on TMDB for it.
        // A slow IMDb mapping must not block HTTP/native resolvers that only need TMDB.
        // Start the lookup lazily as soon as a Stremio provider requires it.
        val enrichedIdentity by lazy {
            // Instantiate only if an installed Stremio addon needs the IMDb ID.
            async(Dispatchers.IO) {
                withTimeoutOrNull(ID_LOOKUP_TIMEOUT_MS) { idMappingService.enrich(identity) } ?: identity
            }
        }
        suspend fun identityFor(provider: ExtensionStreamProvider): MediaIdentity =
            if (provider.requiresIdEnrichment) enrichedIdentity.await() else identity
        val preferredServerId = preferences.getString(preferenceKey(identity), null)

        var servers = partialSeed?.servers.orEmpty()
        val failedProviders = pausedProviders.mapTo(mutableListOf()) {
            "${it.displayName}: temporariamente em pausa após falhas recentes"
        }

        fun absorb(outcome: ResolutionEvent.Outcome) {
            outcome.result.fold(
                onSuccess = { incoming ->
                    consecutiveFailures.remove(outcome.provider.id)
                    breakerUntilAt.remove(outcome.provider.id)
                    providerRegistry.recordOutcome(outcome.provider, incoming.isNotEmpty())
                    if (incoming.isNotEmpty()) {
                        servers = ServerRanker.mergeAndRank(
                            existing = servers,
                            incoming = incoming,
                            providerPriorities = providerPriorities,
                            preferredServerId = preferredServerId,
                            providerFirst = inOrder,
                            qualityCapHeight = appSettings.current.streamMaxHeight
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "${outcome.provider.displayName} failed: ${error.message}")
                    recordFailure(outcome.provider.id, error)
                    providerRegistry.recordOutcome(outcome.provider, false)
                    // Do not hide an upstream HTTP/DNS failure as “no title found”.
                    // Only report a bounded, safe category; URLs and tokens must never reach UI.
                    failedProviders += "${outcome.provider.displayName}: ${sourceFailureSummary(error)}"
                }
            )
        }

        if (inOrder && !includeFallbacks) {
            // The user's order exactly, fallbacks included wherever they were placed; only the
            // source that played last time jumps the queue.
            val ordered = listOfNotNull(remembered) + otherProviders
            ordered.forEachIndexed { index, provider ->
                absorb(runProvider(provider, identityFor(provider)))
                val found = servers.isNotEmpty()
                publish(progress(servers, index + 1, ordered.size, failedProviders, isComplete = found || index == ordered.lastIndex))
                if (found) return@channelFlow
            }
            return@channelFlow
        }

        val events = Channel<ResolutionEvent>(Channel.UNLIMITED)
        var pending = 0
        var total = 0
        var fallbacksStarted = false
        var headStart: Job? = null
        // False while the remembered source is the only one that has been asked.
        var searchStarted = false

        fun start(providers: List<ExtensionStreamProvider>) {
            pending += providers.size
            total += providers.size
            providers.forEach { provider ->
                launch { events.send(runProvider(provider, identityFor(provider))) }
            }
        }

        fun startFallbacks() {
            if (fallbacksStarted || deferredFallbackProviders.isEmpty()) return
            fallbacksStarted = true
            start(deferredFallbackProviders)
        }

        /** The full search: every direct provider at once, fallbacks after their head start. */
        fun startSearch() {
            searchStarted = true
            start(firstStageProviders)
            if (deferredFallbackProviders.isNotEmpty()) {
                headStart = launch {
                    delay(FALLBACK_HEAD_START_MS)
                    events.send(ResolutionEvent.FallbackDeadline)
                }
            }
        }

        val rememberedDeadline = if (remembered != null) {
            start(listOf(remembered))
            launch {
                delay(REMEMBERED_HEAD_START_MS)
                events.send(ResolutionEvent.RememberedDeadline)
            }
        } else {
            startSearch()
            null
        }

        while (pending > 0) {
            when (val event = events.receive()) {
                // The remembered source is taking its time: stop waiting on it alone.
                ResolutionEvent.RememberedDeadline -> if (!searchStarted) {
                    startSearch()
                    send(progress(servers, total - pending, total, failedProviders, isComplete = false))
                }
                ResolutionEvent.FallbackDeadline -> if (servers.isEmpty()) {
                    startFallbacks()
                    send(progress(servers, total - pending, total, failedProviders, isComplete = false))
                }
                is ResolutionEvent.Outcome -> {
                    pending--
                    absorb(event)
                    if (!searchStarted) {
                        // The remembered source answered first. With streams that is the whole
                        // answer (nothing else is pending); with none, everything else is asked.
                        rememberedDeadline?.cancel()
                        if (servers.isEmpty()) startSearch()
                    }
                    // Direct providers are all done and found nothing: fallbacks are the last hope.
                    if (pending == 0 && servers.isEmpty()) startFallbacks()
                    publish(progress(servers, total - pending, total, failedProviders, isComplete = pending == 0))
                }
            }
        }
        rememberedDeadline?.cancel()
        headStart?.cancel()
        events.close()
    }

    /**
     * One provider's answer, or a timeout failure at [PROVIDER_TIMEOUT_MS] even if it is stuck in
     * blocking I/O (coroutine timeouts can't interrupt OkHttp's execute(), but awaiting can stop).
     * When the caller goes away this throws CancellationException, and nothing is recorded:
     * leaving a screen is not the provider's fault.
     */
    private suspend fun runProvider(provider: ExtensionStreamProvider, identity: MediaIdentity): ResolutionEvent.Outcome {
        val work = providerScope.async {
            providerSlots.withPermit {
                try {
                    // Timeout starts when a real worker slot becomes available, not
                    // while waiting in the bounded queue.
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        provider.resolve(identity)
                    } ?: Result.failure(IOException("Provider timed out"))
                } catch (cancelled: CancellationException) {
                    if (!isActive) throw cancelled
                    Result.failure(IOException("Cancelled", cancelled))
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
        }
        try {
            val result = try {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS + PROVIDER_QUEUE_TIMEOUT_MS) { work.await() }
                    ?: Result.failure(IOException("No answer within ${PROVIDER_TIMEOUT_MS / 1000} s"))
            } catch (cancelled: CancellationException) {
                // Rethrows only if the caller is really gone; otherwise every provider must
                // produce an outcome, or the resolution would wait for it forever.
                currentCoroutineContext().ensureActive()
                Result.failure(IOException("Cancelled", cancelled))
            }
            return ResolutionEvent.Outcome(provider, result)
        } finally {
            work.cancel()
        }
    }

    private fun progress(
        servers: List<VideoServer>,
        completed: Int,
        total: Int,
        failedProviders: List<String>,
        isComplete: Boolean
    ) = ServerResolution(
        servers = servers,
        completedProviders = completed,
        totalProviders = total,
        failedProviders = failedProviders.toList(),
        isComplete = isComplete
    )

    private fun recordFailure(providerId: String, error: Throwable) {
        val count = consecutiveFailures.merge(providerId, 1) { old, added -> old + added } ?: 1
        val cooldown = ProviderFailureBackoff.cooldownMillis(error, count)
        if (cooldown > 0) {
            breakerUntilAt[providerId] = System.currentTimeMillis() + cooldown
        }
    }

    /** A paused provider remains installed. Manual "Procurar mais" can always retry. */
    private fun isBreakerOpen(providerId: String): Boolean {
        val until = breakerUntilAt[providerId] ?: return false
        if (System.currentTimeMillis() < until) return true
        breakerUntilAt.remove(providerId, until)
        return false
    }

    override suspend fun getServers(identity: MediaIdentity): List<VideoServer> =
        resolveServers(identity).last().servers

    override suspend fun refreshServer(server: VideoServer): Result<VideoServer> =
        runCatching {
            val request = Request.Builder()
                .url(server.url)
                .header("Range", "bytes=0-1")
                .apply { server.headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    throw IOException("${server.name} returned HTTP ${response.code}")
                }
            }
            server.copy(resolvedAt = System.currentTimeMillis())
        }

    override fun forgetResolvedServers(identity: MediaIdentity) = streamCache.forget(identity)

    override fun forgetResolvedServer(identity: MediaIdentity, server: VideoServer) =
        streamCache.forgetServer(identity, server.id)

    override fun clearResolvedServers() = streamCache.clear()

    /** What a cached resolution must match: the same sources, versions, order and search mode. */
    private fun sourcesFingerprint(providers: List<ExtensionStreamProvider>, inOrder: Boolean): String =
        providers.joinToString(separator = "|", postfix = if (inOrder) "#ordered" else "#all-providers-v2") { provider ->
            "${provider.id}@${provider.versionCode}@${provider.priority}@${provider.isEnabled}@${provider.isFallback}"
        }

    override fun rememberServer(identity: MediaIdentity, server: VideoServer) {
        preferences.edit().putString(preferenceKey(identity), server.id).apply()
    }

    override fun getRememberedServerId(identity: MediaIdentity): String? =
        preferences.getString(preferenceKey(identity), null)

    private fun preferenceKey(identity: MediaIdentity): String =
        "last_stream_server:${identity.cacheKey}"

    override fun rememberWorkingSource(identity: MediaIdentity, server: VideoServer) {
        if (server.providerId.isBlank()) return
        preferences.edit()
            // The same anime may use different hosts for different episodes.
            // Store only the provider ID, never expiring signed video URLs or auth headers.
            .putString("$WORKING_SOURCE_KEY:${identity.cacheKey}", server.providerId)
            .putString("$WORKING_SOURCE_KEY:${identity.tmdbType}", server.providerId)
            .putString(WORKING_SOURCE_KEY, server.providerId)
            .apply()
    }

    /** The source that last played this kind of title (movie or show), else the last to play anything. */
    private fun lastWorkingSourceId(identity: MediaIdentity): String? =
        preferences.getString("$WORKING_SOURCE_KEY:${identity.cacheKey}", null)
            ?: preferences.getString("$WORKING_SOURCE_KEY:${identity.tmdbType}", null)
            ?: preferences.getString(WORKING_SOURCE_KEY, null)

    private sealed interface ResolutionEvent {
        data class Outcome(val provider: ExtensionStreamProvider, val result: Result<List<VideoServer>>) : ResolutionEvent

        /** Direct providers have had [FALLBACK_HEAD_START_MS] on their own. */
        data object FallbackDeadline : ResolutionEvent

        /** The remembered source has had [REMEMBERED_HEAD_START_MS] on its own. */
        data object RememberedDeadline : ResolutionEvent
    }

    private companion object {
        const val TAG = "StreamingRepository"

        /** Long enough for a WebView source (native hoster pass, then up to 17 s in the WebView). */
        const val MAX_CONCURRENT_PROVIDERS = 8
        const val TV_MAX_CONCURRENT_PROVIDERS = 2
        const val PROVIDER_QUEUE_TIMEOUT_MS = 50_000L
        const val PROVIDER_TIMEOUT_MS = 25_000L
        const val ID_LOOKUP_TIMEOUT_MS = 8_000L
        const val FALLBACK_HEAD_START_MS = 8_000L

        /** How long the source that played last time is waited on before the others start too. */
        const val REMEMBERED_HEAD_START_MS = 10_000L
        const val WORKING_SOURCE_KEY = "last_working_source"
    }
}
