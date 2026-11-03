package com.ivor.openstream.data.streaming

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * A safe diagnostic shown in the TV source picker.
 * Never expose provider exceptions verbatim: they may contain configured URLs/tokens.
 */
internal fun sourceFailureSummary(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    val httpCode = chain.asSequence().mapNotNull { cause ->
        Regex("""\bHTTP\s+(\d{3})\b""").find(cause.message.orEmpty())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.firstOrNull()
    return when {
        chain.any { it.message == "P2P_NATIVE_UNAVAILABLE" } ->
            "Motor P2P nativo não encontrou peers ou não conseguiu preparar o vídeo"
        chain.any { it.message == "P2P_ENGINE_NOT_CONFIGURED" } ->
            "Motor P2P não configurado · ativa TorrServer em Definições > Fontes"
        httpCode == 404 || httpCode == 410 -> "HTTP $httpCode · endpoint indisponível"
        httpCode == 401 || httpCode == 403 -> "HTTP $httpCode · acesso recusado"
        httpCode == 429 -> "HTTP 429 · limite de pedidos"
        httpCode != null -> "HTTP $httpCode"
        chain.any { it is UnknownHostException } -> "Falha DNS / ligação"
        chain.any { it is SocketTimeoutException } -> "Tempo de espera esgotado"
        chain.any { it is ConnectException } -> "Falha de ligação"
        chain.any { it is SSLException } -> "Falha TLS / certificado"
        else -> "Erro de comunicação ou resposta inválida"
    }
}
