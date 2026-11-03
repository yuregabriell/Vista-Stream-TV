package com.ivor.openstream.data.streaming

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Provider-level backoff, not a claim that the source has been permanently disabled.
 *
 * A 404 can be specific to one episode, so do not globally pause a provider on
 * its first missing title. A 403/429 normally affects the entire endpoint and can
 * be retried by the user explicitly at any time.
 */
internal object ProviderFailureBackoff {
    fun httpCode(error: Throwable): Int? = generateSequence(error) { it.cause }
        .take(8)
        .mapNotNull { throwable ->
            Regex("""\bHTTP\s+(\d{3})\b""")
                .find(throwable.message.orEmpty())
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.firstOrNull()

    fun cooldownMillis(error: Throwable, consecutiveFailures: Int): Long {
        val code = httpCode(error)
        return when {
            code == 401 || code == 403 -> 10 * 60_000L
            code == 429 -> 5 * 60_000L
            code == 404 || code == 410 ->
                if (consecutiveFailures >= 3) 3 * 60_000L else 0L
            code in setOf(500, 502, 503, 504) ->
                if (consecutiveFailures >= 2) 60_000L else 0L
            generateSequence(error) { it.cause }.take(8)
                .any { it is UnknownHostException || it is ConnectException } ->
                if (consecutiveFailures >= 2) 60_000L else 0L
            generateSequence(error) { it.cause }.take(8)
                .any { it is SocketTimeoutException } ->
                if (consecutiveFailures >= 2) 45_000L else 0L
            consecutiveFailures >= 5 -> 2 * 60_000L
            else -> 0L
        }
    }
}
