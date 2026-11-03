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
    val httpCode = ProviderFailureBackoff.httpCode(error)
    return when {
        chain.any { it.message == "P2P_NATIVE_UNAVAILABLE" } ->
            "Motor P2P nativo não encontrou peers ou não conseguiu preparar o vídeo"
        chain.any { it.message == "P2P_ENGINE_NOT_CONFIGURED" } ->
            "Motor P2P não configurado · ativa TorrServer em Definições > Fontes"
        chain.any { it.message == "P2P_METADATA_UNAVAILABLE" } ->
            "Metadados P2P indisponíveis ou episódio não identificado no torrent"
        httpCode == 404 || httpCode == 410 -> "HTTP $httpCode · título ou endpoint não encontrado"
        httpCode == 401 || httpCode == 403 -> "HTTP $httpCode · acesso recusado"
        httpCode == 429 -> "HTTP 429 · limite de pedidos"
        httpCode == 504 -> "HTTP 504 · servidor demorou a responder"
        httpCode == 502 || httpCode == 503 -> "HTTP $httpCode · servidor temporariamente indisponível"
        httpCode != null -> "HTTP $httpCode"
        chain.any { it is UnknownHostException } -> "Falha DNS / ligação"
        chain.any { it is SocketTimeoutException } -> "Tempo de espera esgotado"
        chain.any { it is ConnectException } -> "Falha de ligação"
        chain.any { it is SSLException } -> "Falha TLS / certificado"
        else -> "Erro de comunicação ou resposta inválida"
    }
}
