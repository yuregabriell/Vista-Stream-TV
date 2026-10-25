package com.ivor.openstream.harvester

import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.net.URI

/**
 * Limited opt-in public HTML anime adapter. Uses the documented provider shapes
 * from LietRepo / GoAnime. No execution of CloudStream binary modules or scripts,
 * no access-gate automation and no extraction from restricted player embeds.
 * Content access and licensing remain the user's/provider's responsibility.
 */
class NativeAnimeHtmlProvider(
    override val id: String,
    override val displayName: String,
    private val base: String,
    private val style: String
) : StreamProvider {
    override val priority = 35
    override val isEnabled = true
    override val isFallback = true

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = identity.originalTitle?.takeIf { it.isNotBlank() } ?: identity.title
            val encoded = URLEncoder.encode(query.take(100), "UTF-8")
            val searchUrl = if(style=="animefire") base+"/pesquisar/"+
                query.trim().lowercase().replace(Regex("[^\\p{L}\\p{N} ]"),"")
                    .replace(Regex("\\s+"),"-") else base+"/?s="+encoded
            fun read(url: String) = Jsoup.connect(url).userAgent("Mozilla/5.0 (Android; VS)")
                .timeout(7500).maxBodySize(2_000_000).followRedirects(true).get()
            val host = URI(base).host
            val page = read(searchUrl)
            val select = if(style=="animefire")
                "a[href*='/animes/'],a[href*='/filmes/']" else
                "article a,.boxAN a,a[href*='/anime/']"
            val normalized=NativeHarvesterStore.fold(query)
            val card=page.select(select).firstOrNull { a ->
                val text=a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                    .ifBlank { a.text() }
                NativeHarvesterStore.fold(text)==normalized
            } ?: return@runCatching emptyList()
            val detailUrl=card.absUrl("href")
            if (URI(detailUrl).host != host) return@runCatching emptyList()
            val detail=read(detailUrl)
            val episodeUrl=if(identity.tmdbType=="movie") detailUrl else {
                detail.select("a.lEp.epT,a.lEp,.divListaEps a,a[href*='/video/'],a[href*='/episodio/'],a[href*='/episode/']")
                    .firstOrNull { link ->
                        val text=link.text()+" "+link.attr("href")
                        Regex("(?:epis[oó]dio|ep|episode)[\\s#/_-]*0*(\\d{1,4})",RegexOption.IGNORE_CASE)
                            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()==identity.episode
                    }?.absUrl("href") ?: return@runCatching emptyList()
            }
            if(URI(episodeUrl).host!=host)return@runCatching emptyList()
            val videoPage=if(episodeUrl==detailUrl) detail else read(episodeUrl)
            videoPage.select("video[src],video source[src],source[src]")
                .mapNotNull { element ->
                    val url=element.absUrl("src").ifBlank { element.absUrl("data-src") }
                    if(!NativeHarvesterStore.safeDirect(url))return@mapNotNull null
                    val mime=when {
                        url.substringBefore('?').endsWith(".m3u8",true)->"application/vnd.apple.mpegurl"
                        url.substringBefore('?').endsWith(".mpd",true)->"application/dash+xml"
                        else->"video/mp4"
                    }
                    VideoServer(id=id+":"+url.hashCode(),providerId=id,providerName=displayName,
                        name=displayName+" · direto",url=url,quality=StreamQuality.UNKNOWN,
                        audio=StreamAudio.UNKNOWN,mimeType=mime,
                        headers=mapOf("Referer" to episodeUrl))
                }.distinctBy { it.url }.take(10)
        }
    }
}
