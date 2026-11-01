package com.ivor.openstream.data.extensions

/** Built-in read-only directories: no APK update when their contents change. */
object VistaRemoteRepositories {
    const val VISTA_URL =
        "https://raw.githubusercontent.com/yuregabriell/Vista-Stream-TV/main/extensions/vista-catalog.json"
    /** Stremio's published community-addon collection, not a third-party scraping service. */
    const val STREMIO_URL = "https://api.strem.io/addonscollection.json"

    val VISTA_ID = RepoUrlNormalizer.repoId(VISTA_URL)
    val STREMIO_ID = RepoUrlNormalizer.repoId(STREMIO_URL)

    val builtinUrls = listOf(VISTA_URL, STREMIO_URL)
    val builtinIds = setOf(VISTA_ID, STREMIO_ID, BundledExtensionCatalog.OFFICIAL_REPO_ID)
}
