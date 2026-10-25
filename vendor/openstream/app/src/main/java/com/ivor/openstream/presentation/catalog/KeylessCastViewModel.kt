package com.ivor.openstream.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.session.PlaybackSession
import com.ivor.openstream.presentation.player.session.NowPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Shares the existing Google Cast session with the keyless Home and player.
 * The Cast connection survives navigation; every selected stream reuses it.
 */
@HiltViewModel
class KeylessCastViewModel @Inject constructor(
    val session: PlaybackSession
) : ViewModel() {
    val status = session.castStatus
    val nowPlaying = session.nowPlaying
    val subtitles = session.castSubtitles
    val routeSelector get() = session.castRouteSelector
    val castPlayer get() = session.castPlayer

    init { session.initCast() }

    fun cast(item: KeylessTitle, server: VideoServer, season: Int, episode: Int, resumeMs: Long) {
        if (status.value.isCasting.not()) return
        session.setRequestHeaders(server.headers)
        session.update(NowPlaying(
            mediaType = if (item.type == "movie") "movie" else "tv",
            tmdbId = item.tmdbId ?: item.sourceId.hashCode().and(Int.MAX_VALUE),
            season = season, episode = episode, downloadId = null,
            mediaUri = server.url, title = item.title,
            episodeTitle = if (item.type == "movie") null else "Temporada $season · Episódio $episode",
            posterPath = item.poster, backdropPath = item.background, stillPath = null,
            next = null, server = server, startPositionMs = resumeMs
        ))
    }

    fun stopCasting() = session.stopCasting()
    fun pauseOrResume() = session.togglePlayback()
    fun selectSubtitle(id: String?) = session.selectCastSubtitle(id)
}
