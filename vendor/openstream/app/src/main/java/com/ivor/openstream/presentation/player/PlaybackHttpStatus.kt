package com.ivor.openstream.presentation.player

import androidx.media3.datasource.HttpDataSource

/** HTTP response status without displaying the provider's secret stream URL or tokens. */
internal fun playbackHttpStatus(error: Throwable?): Int? =
    generateSequence(error) { it.cause }
        .take(10)
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode

/** Useful diagnostic, not a claim that one specific upstream service is blocked. */
internal fun playbackHttpAdvice(status: Int): String = when (status) {
    401 -> "HTTP 401 · sessão ou autorização necessária"
    403 -> "HTTP 403 · acesso recusado pelo servidor; verificar autorização e ligação"
    404, 410 -> "HTTP $status · ligação do vídeo indisponível ou expirada"
    429 -> "HTTP 429 · excesso de pedidos; aguardar antes de repetir"
    in 500..599 -> "HTTP $status · falha temporária do fornecedor"
    else -> "HTTP $status · resposta recusada pelo servidor do vídeo"
}
