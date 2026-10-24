#!/usr/bin/env python3
"""Change Vista Stream homepage storefront icon into global Cast control,
and expose clickable Now playing on TV with remote sources/subtitles.
"""
from pathlib import Path
p=Path("vendor/openstream/app/src/main/java/com/ivor/openstream/presentation/catalog/KeylessCatalogScreen.kt")
s=p.read_text()
s=s.replace("import androidx.compose.material.icons.filled.Storefront",
'''import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv''',1)
s=s.replace("import com.ivor.openstream.domain.model.VideoServer",
'''import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.components.CastDeviceSheet
import com.ivor.openstream.presentation.player.components.VistaCastVideoStore
import com.ivor.openstream.presentation.player.components.rememberVistaCastSession''',1)
old='''    val state by viewModel.state.collectAsState()
    var category by rememberSaveable'''
assert s.count(old)==1
s=s.replace(old,'''    val state by viewModel.state.collectAsState()
    val castSession=rememberVistaCastSession()
    val castStatus by castSession.castStatus.collectAsState()
    val tvNowPlaying by castSession.nowPlaying.collectAsState()
    var showCastDevices by remember { mutableStateOf(false) }
    var showTvPlayer by remember { mutableStateOf(false) }
    var tvSelectedSource by remember { mutableStateOf<com.ivor.openstream.domain.model.VideoServer?>(null) }
    var category by rememberSaveable''',1)
needle='''    val chosen = state.selected
    val player = state.playing'''
assert s.count(needle)==1
s=s.replace(needle,'''    val chosen = state.selected
    val player = state.playing
    if(showCastDevices){
        CastDeviceSheet(
            selector=castSession.castRouteSelector,
            status=castStatus,
            onStopCasting=castSession::stopCasting,
            onDismiss={showCastDevices=false}
        )
    }
    val castSelection=VistaCastVideoStore.current
    if(showTvPlayer && castStatus.isCasting && castSelection!=null &&
        castSelection.current.url==tvNowPlaying?.mediaUri){
        val current=tvSelectedSource?:castSelection.current
        Dialog(
            onDismissRequest={showTvPlayer=false},
            properties=DialogProperties(
                usePlatformDefaultWidth=false,
                decorFitsSystemWindows=false
            )
        ){
            VistaFullscreenPlayer(
                server=current,
                title=castSelection.title,
                episodeLabel=castSelection.episodeLabel,
                resumePositionMs=castSession.currentPositionMs(),
                onProgress={_,_->},
                availableServers=castSelection.sources,
                onSelectSource={next->
                    VistaCastVideoStore.change(next)
                    tvSelectedSource=next
                },
                hasAlternative=castSelection.sources.size>1,
                onTryAlternative={
                    val sources=castSelection.sources
                    val index=sources.indexOfFirst{it.url==current.url}
                    if(sources.size>1){
                        val next=sources[(index+1).coerceAtLeast(0)%sources.size]
                        VistaCastVideoStore.change(next)
                        tvSelectedSource=next
                    }
                },
                onClose={showTvPlayer=false}
            )
        }
    }''',1)
old='''                IconButton(onClick = onOpenSources) {
                    Icon(Icons.Default.Storefront, contentDescription = "Fontes e extensões")
                }'''
assert s.count(old)==1
s=s.replace(old,'''                IconButton(onClick = { showCastDevices=true }) {
                    Icon(
                        if(castStatus.isCasting)Icons.Default.CastConnected
                        else Icons.Default.Cast,
                        contentDescription=if(castStatus.isCasting)
                            "Gerir Chromecast ligado" else "Ligar ao Chromecast",
                        tint=if(castStatus.isCasting)
                            MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                    )
                }''',1)
anchor='''        item(key = "home-search") {'''
assert s.count(anchor)==1
s=s.replace(anchor,'''        if(castStatus.isCasting){
            item(key = "cast-status-banner"){
                Column(
                    modifier=Modifier.fillMaxWidth()
                        .padding(horizontal=20.dp,vertical=5.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val current=VistaCastVideoStore.current
                            if(current!=null && tvNowPlaying?.mediaUri==current.current.url){
                                tvSelectedSource=current.current
                                showTvPlayer=true
                            }else{
                                showCastDevices=true
                            }
                        }
                        .padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(4.dp)
                ){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.CastConnected,
                            contentDescription=null,
                            tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Em reprodução na TV",
                            style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.Bold,
                            modifier=Modifier.weight(1f))
                        TextButton(onClick={showCastDevices=true}){
                            Text("Gerir TV")
                        }
                    }
                    Text(
                        text=tvNowPlaying?.title?:("Ligado a "+castStatus.deviceName.orEmpty()),
                        style=MaterialTheme.typography.bodyLarge,
                        maxLines=1,
                        overflow=TextOverflow.Ellipsis
                    )
                    Text(
                        text=if(tvNowPlaying!=null)
                            "Toque para controlar a reprodução, fontes e legendas"
                            else "O próximo vídeo será enviado para a televisão",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        item(key = "home-search") {''',1)
p.write_text(s)
print("Keyless home: top global Cast button, available-devices sheet, click-to-control TV banner.")
