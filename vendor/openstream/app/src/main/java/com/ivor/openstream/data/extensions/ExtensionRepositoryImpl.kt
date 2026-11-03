package com.ivor.openstream.data.extensions

import android.content.Context
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.ExtensionCatalog
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.ExtensionRepo
import com.ivor.openstream.domain.model.ExtensionUsage
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.repository.ExtensionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marketplace backed by remote repository indexes.
 *
 * The official repository is bundled in the APK and refreshed over HTTP; users can add more
 * repositories by URL. Nothing here downloads code — a manifest only selects and configures an
 * engine that already ships with the app, which is what keeps third-party repositories safe.
 */
@Singleton
class ExtensionRepositoryImpl @Inject constructor(
    private val client: ExtensionRepoClient,
    private val parser: ExtensionIndexParser,
    private val cache: ExtensionCacheStore,
    private val store: ExtensionStateStore,
    private val bundled: BundledExtensionCatalog,
    @ApplicationContext private val context: Context
) : ExtensionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val lock = Any()

    private val snapshots = LinkedHashMap<String, CachedRepoSnapshot>()
    private val repoErrors = mutableMapOf<String, String?>()
    @Volatile
    private var manifests: List<ExtensionManifest> = emptyList()

    @Volatile
    private var loaded = false

    private val _catalog = MutableStateFlow(ExtensionCatalog())
    override val catalog: StateFlow<ExtensionCatalog> = _catalog.asStateFlow()

    init {
        scope.launch {
            ensureLoaded()
            runCatching { refresh(force = false) }
        }
    }

    override suspend fun refresh(force: Boolean) {
        ensureLoaded()
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            val lastSync = store.lastSyncedAt()
            val isStale = now - lastSync >= STALE_WINDOW_MS
            if (!force && !isStale && manifests.isNotEmpty()) return

            publish(isSyncing = true)
            val targets = synchronized(lock) { snapshots.map { (id, snapshot) -> id to snapshot.url } }
            targets.forEach { (repoId, url) ->
                runCatching { client.fetch(url) }
                    .onSuccess { fetched ->
                        cache.write(repoId, fetched)
                        val snapshot = if (repoId == BundledExtensionCatalog.OFFICIAL_REPO_ID) {
                            bundled.reconcile(fetched)
                        } else {
                            fetched
                        }
                        synchronized(lock) {
                            snapshots[repoId] = snapshot
                            repoErrors[repoId] = null
                        }
                    }
                    .onFailure { error ->
                        synchronized(lock) {
                            repoErrors[repoId] = error.message ?: context.getString(R.string.st_could_not_reach_repo)
                        }
                    }
            }
            store.setLastSyncedAt(System.currentTimeMillis())
            synchronized(lock) { rebuildManifests() }
            publish(isSyncing = false)
        }
    }

    override suspend fun addRepo(url: String): Result<ExtensionRepo> {
        ensureLoaded()
        val normalized = RepoUrlNormalizer.normalize(url)
            ?: return Result.failure(IllegalArgumentException(context.getString(R.string.st_bad_repo_link)))
        val repoId = RepoUrlNormalizer.repoId(normalized)
        if (synchronized(lock) { snapshots.containsKey(repoId) }) {
            return Result.failure(IllegalStateException(context.getString(R.string.st_repo_already_added)))
        }

        return runCatching { client.fetch(normalized) }
            .mapCatching { snapshot ->
                if (snapshot.extensions.isEmpty()) {
                    throw IllegalStateException(context.getString(R.string.st_repo_no_extensions))
                }
                cache.write(repoId, snapshot)
                store.addCustomRepo(CustomRepoRecord(id = repoId, url = normalized, name = snapshot.name))
                val repo = synchronized(lock) {
                    snapshots[repoId] = snapshot
                    repoErrors[repoId] = null
                    rebuildManifests()
                    toRepo(repoId, snapshot)
                }
                // Adding a repository only imports its catalog. Playback extensions require
                // explicit installation or a deliberate preset selection.
                publish(isSyncing = false)
                repo
            }
    }

    override fun exportBackup(): String {
        ensureLoaded()
        val current = _catalog.value
        val installed = current.installed.map {
            ExtensionBackupEntry(key = it.key, enabled = it.isEnabled)
        }
        return ExtensionBatchTransfer.encode(
            ExtensionBackup(
                exportedAt = System.currentTimeMillis(),
                repositories = current.repos.filterNot { it.isBuiltIn }.map { it.url },
                installed = installed,
                sourceOrder = store.sourceOrder().filter { key ->
                    current.extensions.any { it.key == key }
                }
            )
        )
    }

    override suspend fun importBackup(raw: String): Result<ExtensionImportSummary> = runCatching {
        ensureLoaded()
        val backup = ExtensionBatchTransfer.decode(raw)
        var repositoriesAdded = 0
        var extensionsInstalled = 0
        var extensionsRestored = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val knownUrls = _catalog.value.repos.map { it.url }.toMutableSet()
        backup.repositories.forEach { untrustedUrl ->
            val normalized = RepoUrlNormalizer.normalize(untrustedUrl)
            if (normalized == null) {
                skipped++
                errors.add("Link de repositório inválido")
            } else if (normalized !in knownUrls) {
                addRepo(normalized).onSuccess {
                    knownUrls.add(normalized)
                    repositoriesAdded++
                }.onFailure {
                    skipped++
                    errors.add(it.message ?: "Não foi possível adicionar um repositório")
                }
            }
        }

        val available = _catalog.value.extensions.associateBy { it.key }
        val existing = store.installs()
        val changes = linkedMapOf<String, InstallRecord>()
        backup.installed.forEach { entry ->
            val extension = available[entry.key]
            if (extension == null || !extension.manifest.isSupported) {
                skipped++
                errors.add("Extensão indisponível: ${entry.key.take(100)}")
            } else {
                val previous = existing[entry.key]
                if (previous == null) extensionsInstalled++ else extensionsRestored++
                changes[entry.key] = InstallRecord(
                    versionCode = previous?.versionCode ?: extension.manifest.versionCode,
                    enabled = entry.enabled,
                    installedAt = previous?.installedAt ?: System.currentTimeMillis()
                )
            }
        }
        // One persistent write for all the imported entries; do not refresh the UI per item.
        if (changes.isNotEmpty()) store.putInstalls(changes)
        val ordered = (backup.sourceOrder + backup.installed.map { it.key })
            .filter { it in available && (it in changes || it in existing) }
            .distinct()
        if (ordered.isNotEmpty()) {
            val rest = store.sourceOrder().filterNot { it in ordered }
            store.setSourceOrder(ordered + rest)
        }
        publish(isSyncing = _catalog.value.isSyncing)

        ExtensionImportSummary(
            repositoriesAdded = repositoriesAdded,
            extensionsInstalled = extensionsInstalled,
            extensionsRestored = extensionsRestored,
            skipped = skipped,
            errors = errors.take(8)
        )
    }

    override suspend fun removeRepo(repoId: String): Result<Unit> {
        ensureLoaded()
        if (repoId in VistaRemoteRepositories.builtinIds) {
            return Result.failure(IllegalStateException(context.getString(R.string.st_official_cannot_remove)))
        }
        store.removeCustomRepo(repoId)
        store.removeInstallsForRepo(repoId)
        cache.delete(repoId)
        synchronized(lock) {
            snapshots.remove(repoId)
            repoErrors.remove(repoId)
            rebuildManifests()
        }
        publish(isSyncing = false)
        return Result.success(Unit)
    }


    /**
     * User-triggered PT-BR preset; remote definition can be updated without a new APK.
     * A bundled copy is available offline. Existing installs are never cleared.
     */
    override suspend fun applyPortuguesePreset(): Result<ExtensionImportSummary> {
        val raw = runCatching { client.fetchPreset(PT_BR_PRESET_URL) }
            .getOrElse {
                runCatching {
                    context.assets.open(PT_BR_PRESET_ASSET).bufferedReader().use { reader ->
                        reader.readText()
                    }
                }.getOrElse { error -> return Result.failure(error) }
            }
        return importBackup(raw)
    }

    override fun install(key: String) {
        ensureLoaded()
        val manifest = manifestFor(key) ?: return
        if (!manifest.isSupported) return
        store.putInstall(
            key,
            InstallRecord(
                versionCode = manifest.versionCode,
                enabled = true,
                installedAt = System.currentTimeMillis()
            )
        )
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun uninstall(key: String) {
        ensureLoaded()
        store.removeInstall(key)
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun setEnabled(key: String, enabled: Boolean) {
        ensureLoaded()
        val record = store.installs()[key] ?: return
        store.putInstall(key, record.copy(enabled = enabled))
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun update(key: String) {
        ensureLoaded()
        val manifest = manifestFor(key) ?: return
        val record = store.installs()[key] ?: return
        store.putInstall(key, record.copy(versionCode = manifest.versionCode))
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun updateAll(): Int {
        ensureLoaded()
        val installs = store.installs()
        val updates = manifests
            .filter { manifest ->
                val record = installs[manifest.key]
                record != null && manifest.versionCode > record.versionCode
            }
            .associate { manifest ->
                manifest.key to installs.getValue(manifest.key).copy(versionCode = manifest.versionCode)
            }
        if (updates.isEmpty()) return 0
        store.putInstalls(updates)
        publish(isSyncing = _catalog.value.isSyncing)
        return updates.size
    }

    /** Already in source order: [publish] sorts the catalog that way. */
    override fun activeExtensions(): List<MarketplaceExtension> {
        ensureLoaded()
        return _catalog.value.extensions.filter { it.isActive }
    }

    override fun setSourceOrder(keys: List<String>) {
        ensureLoaded()
        // Keep ranks for sources not in this list (uninstalled ones the user may reinstall).
        val rest = store.sourceOrder().filterNot { it in keys }
        store.setSourceOrder(keys + rest)
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun recordOutcome(key: String, success: Boolean) {
        if (!loaded) return
        // Called once per provider on every stream resolve, from whichever thread collects the
        // resolution flow — keep the write off it.
        scope.launch {
            store.recordOutcome(key, success)
            publish(isSyncing = _catalog.value.isSyncing)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val officialId = BundledExtensionCatalog.OFFICIAL_REPO_ID
            val official = cache.read(officialId)?.let(bundled::reconcile)
                ?: bundled.load()
                ?: CachedRepoSnapshot(url = BundledExtensionCatalog.OFFICIAL_REPO_URL, name = "OpenStream Official")
            snapshots[officialId] = official
            // The Vista and public Stremio directories are first-class repositories.
            // They are seeded on every existing installation, not just fresh APK installs.
            VistaRemoteRepositories.builtinUrls.forEach { url ->
                val id = RepoUrlNormalizer.repoId(url)
                snapshots[id] = cache.read(id) ?: CachedRepoSnapshot(
                    url = url,
                    name = when (id) {
                        VistaRemoteRepositories.VISTA_ID -> "Vista · Catálogo"
                        VistaRemoteRepositories.GOANIME_ID -> "GoAnime · Fontes PT-BR"
                        else -> "Stremio · Community Addons"
                    },
                    description = when (id) {
                        VistaRemoteRepositories.VISTA_ID -> "Manifestos aprovados do GitHub Vista, sem atualizar o APK"
                        VistaRemoteRepositories.GOANIME_ID -> "Adaptadores Android experimentais inspirados no GoAnime"
                        else -> "Catálogo público de addons da comunidade Stremio"
                    }
                )
            }

            store.customRepos().forEach { record ->
                snapshots[record.id] = cache.read(record.id)
                    ?: CachedRepoSnapshot(url = record.url, name = record.name.ifEmpty { record.url })
            }
            rebuildManifests()
            loaded = true
        }
        migrateLegacyInstalls()
        publish(isSyncing = _catalog.value.isSyncing)
    }

    /** Carries over the pre-marketplace on/off state so upgrading users keep their line-up. */
    private fun migrateLegacyInstalls() {
        val officialId = BundledExtensionCatalog.OFFICIAL_REPO_ID
        val official = manifests.filter { it.repoId == officialId }
        if (official.isEmpty()) return

        val installs = store.installs()
        val migrated = official.mapNotNull { manifest ->
            if (installs.containsKey(manifest.key)) return@mapNotNull null
            val legacyEnabled = store.legacyInstallState(manifest.id) ?: return@mapNotNull null
            manifest.key to InstallRecord(
                versionCode = manifest.versionCode,
                enabled = legacyEnabled,
                installedAt = System.currentTimeMillis()
            )
        }.toMap()

        if (migrated.isNotEmpty()) store.putInstalls(migrated)
        store.clearLegacyState(official.map { it.id })
    }

    // A fresh installation intentionally has no enabled sources. The full catalogs
    // remain browsable; installation happens through manual selection or a preset.

    private fun rebuildManifests() {
        manifests = snapshots.entries
            .flatMap { (repoId, snapshot) ->
                snapshot.extensions.mapNotNull { parser.toManifest(it, repoId) }
            }
            .distinctBy { it.key }
    }

    private fun manifestFor(key: String): ExtensionManifest? = manifests.firstOrNull { it.key == key }

    private fun publish(isSyncing: Boolean) {
        val installs = store.installs()
        val usage = store.usage()
        val order = store.sourceOrder().withIndex().associate { (index, key) -> key to index }
        // The user's ranking first; the rest by the catalog's priority, as before ranking existed.
        val ranked = manifests.sortedWith(
            compareBy<ExtensionManifest> { order[it.key] ?: Int.MAX_VALUE }
                .thenBy { it.engine.priority }
                .thenBy { it.name.lowercase() }
        )
        val extensions = ranked.map { manifest ->
            val record = installs[manifest.key]
            val stats = usage[manifest.key]
            MarketplaceExtension(
                manifest = manifest,
                isInstalled = record != null,
                isEnabled = record?.enabled == true,
                installedVersionCode = record?.versionCode ?: 0,
                installedAt = record?.installedAt ?: 0L,
                usage = ExtensionUsage(
                    successes = stats?.successes ?: 0,
                    failures = stats?.failures ?: 0
                )
            )
        }
        val repos = synchronized(lock) {
            snapshots.map { (repoId, snapshot) -> toRepo(repoId, snapshot) }
        }
        _catalog.value = ExtensionCatalog(
            repos = repos,
            extensions = extensions,
            isSyncing = isSyncing,
            lastSyncedAt = store.lastSyncedAt(),
            syncError = repos.firstNotNullOfOrNull { it.error }
        )
    }

    private fun toRepo(repoId: String, snapshot: CachedRepoSnapshot) = ExtensionRepo(
        id = repoId,
        url = snapshot.url,
        name = snapshot.name.ifEmpty { snapshot.url },
        description = snapshot.description,
        iconUrl = snapshot.iconUrl,
        website = snapshot.website,
        isBuiltIn = repoId in VistaRemoteRepositories.builtinIds,
        lastSyncedAt = snapshot.fetchedAt,
        extensionCount = snapshot.extensions.size,
        error = repoErrors[repoId]
    )

    private companion object {
        const val PT_BR_PRESET_URL = "https://raw.githubusercontent.com/yuregabriell/Vista-Stream-TV/main/presets/pt-br.json"
        const val PT_BR_PRESET_ASSET = "presets/pt-br.json"
        val STALE_WINDOW_MS = 5 * 60 * 1000L
    }
}


/**
 * A safe, deliberately small set of default playback sources.
 *
 * Only the bundled OFFICIAL repository is eligible for automatic PT selection;
 * an arbitrary community index cannot force its entries into a fresh install.
 * A language claim is metadata, not proof of an available Portuguese stream.
 */
internal object PortugueseExtensionPreset {
    private val core = setOf("yoru", "cypher")

    fun includes(manifest: ExtensionManifest): Boolean {
        if (manifest.repoId != BundledExtensionCatalog.OFFICIAL_REPO_ID ||
            !manifest.isSupported || manifest.isNsfw ||
            manifest.status == com.ivor.openstream.domain.model.ExtensionStatus.DOWN
        ) return false
        if (manifest.id in core) return true

        // Experimental native scrapers require user opt-in, even if labelled PT-BR.
        if ("experimental" in manifest.tags ||
            manifest.engine.type.key.startsWith("native-")
        ) return false
        val language = manifest.language.lowercase(java.util.Locale.ROOT)
        return "portuguese" in manifest.tags ||
            "portuguese" in language || "português" in language ||
            language == "pt" || language == "pt-br" || language == "pt-pt"
    }

    fun rank(manifest: ExtensionManifest): Int = when (manifest.id) {
        // Prefer Portuguese results; direct core routes remain available.
        "zeus" -> 0
        "froststream" -> 1
        "fenixflix" -> 2
        "yoru" -> 100
        "cypher" -> 101
        else -> 10 + manifest.engine.priority
    }
}
