package com.ivor.openstream.data.extensions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The official repository ships inside the APK as well as being served over HTTP, so a fresh
 * install has a working catalog before it ever reaches the network. The bundled copy is the same
 * document published at [OFFICIAL_REPO_URL].
 */
@Singleton
class BundledExtensionCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: ExtensionIndexParser
) {
    fun load(): CachedRepoSnapshot? = runCatching {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val repo = parser.parseRepo(raw)
        val included = repo.extensions + nativeEntries()
        CachedRepoSnapshot(
            url = OFFICIAL_REPO_URL,
            name = repo.name?.trim().orEmpty().ifEmpty { "OpenStream Official" },
            description = repo.description?.trim().orEmpty(),
            iconUrl = repo.iconUrl,
            website = repo.website,
            fetchedAt = 0L,
            extensions = included
        )
    }.getOrNull()

    /**
     * Reconciles a fetched or cached copy of the official repository with the one in this APK.
     * Per entry the higher `versionCode` wins, and entries only this APK knows about are kept, so a
     * new build's sources work before the published index catches up. Retiring a bundled entry
     * therefore means publishing it with a higher `versionCode` (for example `status: 0`).
     */
    fun reconcile(remote: CachedRepoSnapshot): CachedRepoSnapshot {
        val local = load() ?: return remote
        val merged = LinkedHashMap<String, ExtensionEntryDto>()
        remote.extensions.forEach { merged[it.id] = it }
        local.extensions.forEach { entry ->
            val current = merged[entry.id]
            if (current == null || entry.versionCode > current.versionCode) merged[entry.id] = entry
        }
        return remote.copy(extensions = merged.values.toList())
    }

    /** Kotlin-native engines bundled in the APK, disabled until users enable them. */
    private fun nativeEntries(): List<ExtensionEntryDto> = listOf(
        ExtensionEntryDto(
            id="vista-native-harvester", name="Harvester Browser · Nativa",
            description="Fontes aprovadas do Harvester, relacionadas com os títulos e episódios no player VS.",
            version="1.0.0", versionCode=30, authors=listOf("Vista Stream"),
            language="Português / Multi", tags=listOf("native","harvester","movies","series","anime"),
            status=1, installedByDefault=true,
            engine=ExtensionEngineDto(type="native-harvester",priority=30)
        ),
        ExtensionEntryDto(
            id="vista-native-animefire",name="AnimeFire PT-BR · Experimental",
            description="Busca páginas públicas e vídeos diretos; não contorna players protegidos, DRM ou autenticação.",
            version="1.0.0",versionCode=30,authors=listOf("Vista Stream"),
            language="Português (Brasil)",tags=listOf("native","anime","portuguese","experimental"),
            status=3,installedByDefault=true,
            engine=ExtensionEngineDto(type="native-anime-html",endpoint="https://animefire.io",
                qualityFilter="animefire",priority=35)
        ),
        ExtensionEntryDto(
            id="vista-native-goyabu",name="Goyabu PT-BR · Experimental",
            description="Identifica episódios e vídeos diretos em HTML público; reprodução pode não estar disponível.",
            version="1.0.0",versionCode=30,authors=listOf("Vista Stream"),
            language="Português (Brasil)",tags=listOf("native","anime","portuguese","experimental"),
            status=3,installedByDefault=true,
            engine=ExtensionEngineDto(type="native-anime-html",endpoint="https://goyabu.io",
                qualityFilter="goyabu",priority=36)
        )
    )

    companion object {
        const val ASSET_PATH = "extensions/official-repo.json"
        const val OFFICIAL_REPO_URL =
            "https://raw.githubusercontent.com/Ivorisnoob/OpenStream/main/extensions/index.json"
        val OFFICIAL_REPO_ID: String = RepoUrlNormalizer.repoId(OFFICIAL_REPO_URL)
    }
}
