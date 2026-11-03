package com.ivor.openstream.data.streaming

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderFailureBackoffTest {
    @Test fun forbiddenServerPausesWithoutDisablingExtension() {
        assertEquals(600_000L,
            ProviderFailureBackoff.cooldownMillis(IOException("FrostStream returned HTTP 403"), 1))
    }

    @Test fun singleMissingEpisodeMustNotGloballyPauseProvider() {
        assertEquals(0L,
            ProviderFailureBackoff.cooldownMillis(IOException("Vidking returned HTTP 404"), 1))
        assertEquals(180_000L,
            ProviderFailureBackoff.cooldownMillis(IOException("Vidking returned HTTP 404"), 3))
    }

    @Test fun gatewayTimeoutGetsShortTemporaryCooldown() {
        val error = IOException("Brazuca returned HTTP 504")
        assertEquals(0L, ProviderFailureBackoff.cooldownMillis(error, 1))
        assertEquals(60_000L, ProviderFailureBackoff.cooldownMillis(error, 2))
    }

    @Test fun dnsErrorsDoNotPersistForever() {
        val error = IOException("Connection failed", UnknownHostException("secret-host"))
        assertEquals(0L, ProviderFailureBackoff.cooldownMillis(error, 1))
        assertEquals(60_000L, ProviderFailureBackoff.cooldownMillis(error, 2))
    }

    @Test fun tokenOrPrivateUrlNeverAppearsInClassifier() {
        val error = IOException("https://private.test/?key=supersecret returned HTTP 403")
        assertEquals(403, ProviderFailureBackoff.httpCode(error))
        assertEquals("HTTP 403 · acesso recusado", sourceFailureSummary(error))
    }
}
