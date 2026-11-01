package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaLiveDirectoryTest {
    private val parser = ExtensionIndexParser()

    @Test
    fun vistaCatalogReadsMetadataAndKeepsAllEntries() {
        val raw = """
            {
              "manifestVersion": 1,
              "name": "Vista Catálogo",
              "extensions": [
                {
                  "id": "stream-a", "name": "Fonte PT",
                  "language": "pt-BR",
                  "manifestUrl": "https://example.org/manifest.json",
                  "titleExamples": ["Exemplo A", "Exemplo B"],
                  "contentTypes": ["movie", "series"],
                  "engine": {"type": "stremio", "endpoint": "https://example.org"}
                },
                {
                  "id": "stream-b", "name": "Fonte B",
                  "engine": {"type": "unsupported"}
                }
              ]
            }
        """.trimIndent()
        val repo = parser.parseRepo(raw)
        assertEquals(2, repo.extensions.size)
        val entry = repo.extensions.first()
        assertEquals("pt-BR", entry.language)
        assertEquals(listOf("Exemplo A", "Exemplo B"), entry.titleExamples)
        val manifest = parser.toManifest(entry, VistaRemoteRepositories.VISTA_ID)!!
        assertEquals(ExtensionEngineType.STREMIO, manifest.engine.type)
        assertEquals(listOf("movie", "series"), manifest.contentTypes)
        assertEquals("https://example.org/manifest.json", manifest.manifestUrl)
    }

    @Test
    fun stremioPublicCatalogOnlyEnablesHttpsStreamProvidersAndNeverInstallsAutomatically() {
        val raw = """
          [
            {
              "transportUrl": "https://good.example.org/manifest.json",
              "transportName": "http",
              "manifest": {
                "id": "org.test.stream", "name": "Sample Stream",
                "version": "1.1.2",
                "resources": ["stream", "meta"],
                "types": ["movie", "series"]
              }
            },
            {
              "transportUrl": "https://meta.example.org/manifest.json",
              "manifest": {
                "id": "org.test.metadata", "name": "Catalog Only",
                "resources": ["meta", "catalog"], "types": ["movie"]
              }
            },
            {
              "transportUrl": "http://insecure.example.org/manifest.json",
              "manifest": {
                "id": "org.test.bad", "name": "HTTP Only",
                "resources": ["stream"]
              }
            }
          ]
        """.trimIndent()
        val entries = parser.parseStremioCollection(raw).extensions
        assertEquals(2, entries.size)
        val streaming = entries.first { it.name == "Sample Stream" }
        assertEquals("stremio", streaming.engine.type)
        assertFalse(streaming.installedByDefault)
        assertEquals("https://good.example.org", streaming.engine.endpoint)
        val metadataOnly = entries.first { it.name == "Catalog Only" }
        assertEquals("unsupported", metadataOnly.engine.type)
        assertFalse(parser.toManifest(metadataOnly, VistaRemoteRepositories.STREMIO_ID)!!.isSupported)
    }

    @Test
    fun stremioDirectoryDeduplicatesEntriesAndRejectsInvalidInput() {
        val item = """
            {"transportUrl":"https://video.example.org/manifest.json",
             "manifest":{"id":"org.unique","name":"One","resources":["stream"]}}
        """.trimIndent()
        val catalog = parser.parseStremioCollection("[$item, $item]")
        assertEquals(1, catalog.extensions.size)
        assertTrue(catalog.extensions.single().id.startsWith("stremio-"))
    }
}
