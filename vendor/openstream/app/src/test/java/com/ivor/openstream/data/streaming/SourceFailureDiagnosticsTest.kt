package com.ivor.openstream.data.streaming

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceFailureDiagnosticsTest {
    @Test
    fun http404IsVisibleWithoutExposingPrivateUrl() {
        val text = sourceFailureSummary(IOException("https://example.org/api?token=SECRET returned HTTP 404"))
        assertEquals("HTTP 404 · endpoint indisponível", text)
        assertFalse(text.contains("SECRET"))
    }

    @Test
    fun dnsFailureIsReportedWithoutHostname() {
        val text = sourceFailureSummary(IOException("Bad connection", UnknownHostException("my-private-host")))
        assertEquals("Falha DNS / ligação", text)
        assertFalse(text.contains("my-private-host"))
    }

    @Test
    fun unavailableNativeTorrentReportsPeerOrEngineFailure() {
        assertEquals(
            "Motor P2P nativo não encontrou peers ou não conseguiu preparar o vídeo",
            sourceFailureSummary(IllegalStateException("P2P_NATIVE_UNAVAILABLE"))
        )
    }

    @Test
    fun missingImdbDoesNotMasqueradeAsNoStreamsFound() {
        assertEquals(
            "ID IMDb não encontrado · esta extensão exige identificador IMDb/Kitsu",
            sourceFailureSummary(IllegalStateException("STREMIO_ID_MISSING"))
        )
    }

    @Test
    fun missingTorrentEngineExplainsRequiredConfiguration() {
        assertEquals(
            "Motor P2P não configurado · ativa TorrServer em Definições > Fontes",
            sourceFailureSummary(IllegalStateException("P2P_ENGINE_NOT_CONFIGURED"))
        )
    }

    @Test
    fun otherErrorsDoNotEchoServerBodiesOrTokens() {
        assertEquals("Erro de comunicação ou resposta inválida", sourceFailureSummary(IOException("token=SECRET")))
    }
}
