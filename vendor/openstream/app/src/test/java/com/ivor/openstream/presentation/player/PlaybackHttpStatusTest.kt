package com.ivor.openstream.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHttpStatusTest {
    @Test fun forbiddenIsNotMistakenForADeadEndpoint() {
        assertEquals(
            "HTTP 403 · acesso recusado pelo servidor; verificar autorização e ligação",
            playbackHttpAdvice(403)
        )
    }

    @Test fun throttlingIsNotMistakenForAnIpBan() {
        assertEquals(
            "HTTP 429 · excesso de pedidos; aguardar antes de repetir",
            playbackHttpAdvice(429)
        )
    }

    @Test fun missingStreamAndServerErrorsHaveDistinctDetails() {
        assertEquals(
            "HTTP 404 · ligação do vídeo indisponível ou expirada",
            playbackHttpAdvice(404)
        )
        assertEquals(
            "HTTP 503 · falha temporária do fornecedor",
            playbackHttpAdvice(503)
        )
    }
}
