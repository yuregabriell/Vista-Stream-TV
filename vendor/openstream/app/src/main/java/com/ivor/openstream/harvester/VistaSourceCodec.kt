package com.ivor.openstream.harvester

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Portable, offline-first Vista Source snapshot.
 *
 * Only direct, unauthenticated media URLs may be exported. This JSON file is
 * metadata, not a video and not an API endpoint. Later adapters may publish
 * paginated catalog/streams endpoints using the same stable identifiers.
 */
@Serializable
data class VistaSourceManifest(
    val schemaVersion: Int = 1,
    val id: String = "vista-crawler",
    val name: String = "Minha biblioteca descoberta",
    val type: String = "vista-source",
    val generatedAt: Long = 0L,
    val items: List<NativeSavedStream> = emptyList()
)

object VistaSourceCodec {
    const val MAX_BYTES = 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(items: List<NativeSavedStream>, now: Long = System.currentTimeMillis()): String {
        val safe = items.asSequence()
            .filter { !it.webOnly && NativeHarvesterStore.safeDirect(it.url) }
            .map { item ->
                item.copy(
                    pageUrl = item.pageUrl.substringBefore('?').substringBefore('#')
                        .takeIf(NativeHarvesterStore::safeWebPage).orEmpty(),
                    catalogSourceId = item.catalogSourceId?.take(120)
                )
            }
            .distinctBy { Triple(it.url, it.season, it.episode) }
            .take(250)
            .toList()
        return json.encodeToString(VistaSourceManifest(generatedAt = now, items = safe))
    }

    fun parse(raw: String): List<NativeSavedStream> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Manifesto demasiado grande" }
        val manifest = json.decodeFromString<VistaSourceManifest>(raw)
        require(manifest.schemaVersion == 1 && manifest.type == "vista-source") {
            "Formato Vista Source não suportado"
        }
        require(manifest.id.length in 3..120 && manifest.name.length in 1..160) {
            "Fonte sem identificação válida"
        }
        require(manifest.items.size <= 250) { "Demasiadas fontes" }
        return manifest.items.asSequence()
            .filter { !it.webOnly && NativeHarvesterStore.safeDirect(it.url) }
            .filter { it.title.isNotBlank() && it.title.length <= 180 }
            .filter { it.season in 1..999 && it.episode in 1..9999 }
            .map { item ->
                item.copy(
                    pageUrl = item.pageUrl.substringBefore('?').substringBefore('#')
                        .takeIf(NativeHarvesterStore::safeWebPage).orEmpty(),
                    catalogSourceId = manifest.id
                )
            }
            .distinctBy { Triple(it.url, it.season, it.episode) }
            .toList()
    }
}
