#!/usr/bin/env python3
"""Add app-wide Chromecast controls to Vista's keyless player & mobile catalog.
Reuse the EXISTING shared Media3 Cast player and proxy: never initialize a second
Cast SDK player/receiver. No background video discovery or new network requests.
"""
from pathlib import Path
root=Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
def modify(path, old, new, label):
    content=path.read_text()
    n=content.count(old)
    if n!=1:raise RuntimeError(f"{label}: expected 1, found {n}")
    path.write_text(content.replace(old,new,1))

hub=root/"presentation/player/components/VistaGlobalCast.kt"
hub.write_text(r'''package com.ivor.openstream.presentation.player.components

import android.content.Context
import androidx.compose.runtime.*
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.session.PlaybackSession
import com.ivor.openstream.presentation.player.session.NowPlaying
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** One Cast session for every Vista player, including external Harvester video. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VistaCastEntryPoint {
    fun playbackSession(): PlaybackSession
}

@Composable
fun rememberVistaCastSession(): PlaybackSession {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context, VistaCastEntryPoint::class.java
        ).playbackSession()
    }.also {
        LaunchedEffect(it) { it.initCast() }
    }
}

data class VistaCastVideo(
    val title:String,
    val episodeLabel:String,
    val current:VideoServer,
    val sources:List<VideoServer>
)

/** UI cache for reopening the keyless/Harvester video while it's on the TV. */
object VistaCastVideoStore {
    var current by mutableStateOf<VistaCastVideo?>(null)
        private set
    fun update(video:VistaCastVideo) { current=video }
    fun change(server:VideoServer) {
        current=current?.copy(current=server)
    }
    fun clear() { current=null }
}

fun castNowPlaying(video:VistaCastVideo, positionMs:Long): NowPlaying {
    // Stable content identifier across URL/quality changes, preventing
    // unnecessary media reload when a user returns to the fullscreen dialog.
    val key=(video.title+"|"+video.episodeLabel).hashCode().and(0x7FFFFFFF)
    return NowPlaying(
        mediaType=if(video.episodeLabel.isBlank() ||
            video.episodeLabel=="Harvester Browser")"movie" else "tv",
        tmdbId=key, season=1, episode=1, downloadId=null,
        mediaUri=video.current.url, title=video.title,
        episodeTitle=video.episodeLabel.ifBlank { null },
        posterPath=null, backdropPath=null, stillPath=null,
        next=null, server=video.current,
        startPositionMs=positionMs.coerceAtLeast(0)
    )
}
''')

player=root/"presentation/catalog/VistaFullscreenPlayer.kt"
s=player.read_text()
s=s.replace('import androidx.compose.material.icons.filled.Subtitles',
'''import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv''',1)
anchor='import com.ivor.openstream.domain.model.VideoServer'
assert s.count(anchor)==1
s=s.replace(anchor,anchor+'''
import com.ivor.openstream.presentation.player.components.CastDeviceSheet
import com.ivor.openstream.presentation.player.components.VistaCastVideo
import com.ivor.openstream.presentation.player.components.VistaCastVideoStore
import com.ivor.openstream.presentation.player.components.castNowPlaying
import com.ivor.openstream.presentation.player.components.rememberVistaCastSession''',1)
anchor='''    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }'''
assert s.count(anchor)==1
s=s.replace(anchor,'''    val context = LocalContext.current
    val castSession=rememberVistaCastSession()
    val castStatus by castSession.castStatus.collectAsState()
    val castSubtitles by castSession.castSubtitles.collectAsState()
    var castSheet by remember { mutableStateOf(false) }
    var hadCast by remember { mutableStateOf(false) }
    var tvLastPosition by remember { mutableLongStateOf(0L) }
    val activity = remember(context) { context.findPlayerActivity() }''',1)
needle='''    DisposableEffect(player) {
        val listener = object : Player.Listener {'''
assert s.count(needle)==1
s=s.replace(needle,'''    // Cast is selected at Home or here; any subsequent play of a source goes
    // straight to the selected TV. This is one Cast session shared with the
    // ordinary Vista player, not a separate Cast connection.
    LaunchedEffect(castStatus.isCasting, server.url) {
        if(castStatus.isCasting){
            val info=VistaCastVideo(title,episodeLabel,server,availableServers)
            VistaCastVideoStore.update(info)
            val position=if(hadCast && castSession.nowPlaying.value?.title==title)
                castSession.currentPositionMs()
                else player.currentPosition.takeIf { it>0 }?:resumePositionMs
            castSession.update(castNowPlaying(info,position))
            player.pause()
            hadCast=true
        } else if(hadCast) {
            // Handoff back to the local player only while this screen exists.
            player.seekTo(tvLastPosition.coerceAtLeast(0))
            player.prepare()
            player.playWhenReady=false
            // The shared player can still offer playback from the mini player.
            hadCast=false
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {''',1)
s=s.replace('''    LaunchedEffect(player) {
        // Probe direct HTTP sources once before playback.''',
'''    LaunchedEffect(player,castStatus.isCasting) {
        if(castStatus.isCasting)return@LaunchedEffect
        // Probe direct HTTP sources once before playback.''',1)
s=s.replace('''    LaunchedEffect(player) {
        var lastSavedBucket = -1L''',
'''    LaunchedEffect(player,castStatus.isCasting) {
        var lastSavedBucket = -1L''',1)
needle='''            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L'''
assert s.count(needle)==1
s=s.replace(needle,'''            val currentPlayer=if(castStatus.isCasting)castSession.castPlayer?:player else player
            position = currentPlayer.currentPosition.coerceAtLeast(0)
            duration = currentPlayer.duration.takeIf { it > 0 } ?: 0L
            if(castStatus.isCasting)tvLastPosition=position''',1)
s=s.replace('''            if (player.isPlaying && duration > 0 && position / 5_000L != lastSavedBucket)''',
'''            if (currentPlayer.isPlaying && duration > 0 && position / 5_000L != lastSavedBucket)''',1)
s=s.replace('''    LaunchedEffect(visible, playing, trackSheet, err) {''',
'''    LaunchedEffect(visible, playing, trackSheet, err, castStatus.isCasting) {''',1)
s=s.replace('''        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { this.player = player; useController = false } },
            update = { if (it.player !== player) it.player = player }
        )''',
'''        if (castStatus.isCasting) {
            Column(
                modifier=Modifier.fillMaxSize(),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center
            ) {
                Icon(Icons.Default.CastConnected,
                    contentDescription="Em reprodução na TV",
                    tint=Color(0xFFB1C5FF),
                    modifier=Modifier.size(70.dp))
                Spacer(Modifier.height(18.dp))
                Text("Em reprodução na TV",color=Color.White,
                    style=MaterialTheme.typography.titleLarge)
                Text(castStatus.deviceName.orEmpty(),color=Color.White.copy(alpha=.75f))
            }
        } else AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { this.player = player; useController = false } },
            update = { if (it.player !== player) it.player = player }
        )''',1)
s=s.replace('''        if (state == Player.STATE_BUFFERING && err == null) {''',
'''        if ((if(castStatus.isCasting) castSession.castLoading.collectAsState().value
            else state == Player.STATE_BUFFERING) && err == null) {''',1)
s=s.replace('''                IconButton(onClick = { trackSheet = true }) {
                    Icon(Icons.Default.Subtitles, contentDescription = "Áudio e legendas", tint = Color.White)
                }''',
'''                IconButton(onClick = { castSheet = true }) {
                    Icon(if(castStatus.isCasting)Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription=if(castStatus.isCasting)
                            "Desligar ou mudar Chromecast" else "Ligar ao Chromecast",
                        tint=Color.White)
                }''',1)
# Video controls target whichever player actually holds the stream.
s=s.replace('''                    IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0)) })''',
'''                    IconButton(onClick = {
                        val active=if(castStatus.isCasting)castSession.activePlayer else player
                        active.seekTo((active.currentPosition - 10_000L).coerceAtLeast(0))
                    })''',1)
s=s.replace('''                        onClick = { if (player.isPlaying) player.pause() else player.play(); visible = true },''',
'''                        onClick = {
                            val active=if(castStatus.isCasting)castSession.activePlayer else player
                            if(active.isPlaying)active.pause() else active.play()
                            visible=true
                        },''',1)
s=s.replace('''                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,''',
'''                            if (if(castStatus.isCasting)
                                castSession.isPlaying.collectAsState().value
                                else playing) Icons.Default.Pause else Icons.Default.PlayArrow,''',1)
s=s.replace('''                    IconButton(onClick = { player.seekTo(player.currentPosition + 10_000L) })''',
'''                    IconButton(onClick = {
                        val active=if(castStatus.isCasting)castSession.activePlayer else player
                        active.seekTo(active.currentPosition + 10_000L)
                    })''',1)
s=s.replace('''                            player.seekTo((fraction * duration).toLong())
                            position = player.currentPosition''',
'''                            val active=if(castStatus.isCasting)castSession.activePlayer else player
                            active.seekTo((fraction * duration).toLong())
                            position = active.currentPosition''',1)
s=s.replace('''    if (sourceSheet) {
        AlertDialog(''',
'''    if(castSheet) {
        CastDeviceSheet(
            selector=castSession.castRouteSelector,
            status=castStatus,
            onStopCasting=castSession::stopCasting,
            onDismiss={castSheet=false}
        )
    }
    if (sourceSheet) {
        AlertDialog(''',1)
s=s.replace('''    if (trackSheet) {
        VistaTrackPicker(
            tracks = tracks,
            player = player,
            onDismiss = { trackSheet = false }
        )
    }''',
'''    if (trackSheet) {
        if(castStatus.isCasting) {
            AlertDialog(
                onDismissRequest={trackSheet=false},
                title={Text("Áudio e legendas na TV")},
                text={
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("A qualidade e as faixas de áudio dependem do dispositivo Cast e da fonte.")
                        TextButton(onClick={
                            castSession.selectCastSubtitle(null)
                            trackSheet=false
                        }) { Text("Legendas desligadas") }
                        castSubtitles.options.forEach{option->
                            TextButton(onClick={
                                castSession.selectCastSubtitle(option.id)
                                trackSheet=false
                            }) { Text(option.label) }
                        }
                    }
                },
                confirmButton={TextButton(onClick={trackSheet=false}){Text("Fechar")}}
            )
        }else VistaTrackPicker(
            tracks=tracks,player=player,onDismiss={trackSheet=false}
        )
    }''',1)
player.write_text(s)
print("Fullscreen Vista player shares Cast session, route dialog, TV transport, source and subtitles.")
