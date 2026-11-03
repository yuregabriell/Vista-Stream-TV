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
        assertEquals("HTTP 404 · título ou endpoint não encontrado", text)
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
    @Test
    fun gatewayTimeoutExplainsSlowUpstream() {
        assertEquals(
            "HTTP 504 · servidor demorou a responder",
            sourceFailureSummary(IOException("Brazuca returned HTTP 504"))
        )
    }

}
