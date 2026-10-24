#!/usr/bin/env python3
"""Merge Harvester Browser v2.3 into Vista Stream as an internal feature.

The OpenStream media player remains the ONLY media player and Cast stack.
The legacy VistaCast code is deliberately not copied.
"""
from pathlib import Path
import re
import shutil

ROOT = Path("vendor/openstream")
ANDROID = ROOT / "app"
SRC = ANDROID / "src/main"
JAVA = SRC / "java"
HB = Path("/tmp/harvester-app")
HB_SRC = HB / "app/src/main"

main = HB_SRC / "java/com/harvester/browser/MainActivity.java"
assert main.exists(), "Harvester Browser v2.3 not restored"
source = main.read_text()
assert "pageLockBlocks" in source and "HOST_PAUSE_MS" in source, "Missing browser v2.3 safety changes"
assert "openNativePlayer" in source, "No native playback entry"
assert "extends androidx.fragment.app.FragmentActivity" in source, "Expected browser Activity lifecycle"

# Copy the full real browser, UI, store and scanner, NOT its old VistaCast
# MediaSessionService/receiver/player and NOT its independent application shell.
from_dir = HB_SRC / "java/com/harvester/browser"
to_dir = JAVA / "com/harvester/browser"
shutil.copytree(from_dir, to_dir, dirs_exist_ok=True)
for unused in ("PlaybackService.java", "LocalVideoServer.java", "NativePlayerActivity.java"):
    (to_dir / unused).unlink(missing_ok=True)

assets = SRC / "assets"
assets.mkdir(parents=True, exist_ok=True)
for name in ("autoplayer.js", "pagelock.js"):
    old = HB_SRC / "assets" / name
    assert old.exists(), f"{name} missing from Harvester"
    shutil.copy2(old, assets / name)

# Preserve player title, MIME, the originating page and resume position when
# redirecting to the official Vista Stream player.
source = (to_dir / "MainActivity.java").read_text()
point = '        intent.putExtra("format",item.format);'
assert source.count(point)==1
source = source.replace(point,point+'''
        intent.putExtra("page_url",item.pageUrl);
        intent.putExtra("user_agent",selected==null||selected.web==null?"Harvester Browser":
            selected.web.getSettings().getUserAgentString());''',1)
(to_dir / "MainActivity.java").write_text(source)

# Keep only a compatibility bridge class for existing Harvester intents.
# This Activity is NOT a player: it forwards immediately to Vista's Media3
# player. No extra Cast SDK, CastPlayer, service or app is initialized here.
bridge = r'''package com.harvester.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.widget.Toast;

public final class NativePlayerActivity extends Activity {
    public static final String TEST_VIDEO =
        "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent original=getIntent();
        String url=original==null?null:original.getStringExtra("url");
        if(url==null || !url.startsWith("https://") || url.length()>8192) {
            Toast.makeText(this,"Fonte não disponível para reprodução.",Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        Intent play=new Intent("com.vistastream.tv.action.PLAY");
        play.setClassName(getPackageName(),
            "com.ivor.openstream.harvester.HarvesterPlaybackActivity");
        String format=original.getStringExtra("format");
        String mime=null;
        if(format!=null) {
            switch(format.toLowerCase(java.util.Locale.ROOT)) {
                case "hls": mime="application/vnd.apple.mpegurl";break;
                case "dash": mime="application/dash+xml";break;
                case "webm":mime="video/webm";break;
                case "mp4":mime="video/mp4";break;
                case "mkv":mime="video/x-matroska";break;
                default:mime=null;
            }
        }
        if(mime==null){
            if(url.matches("(?i).*\\.m3u8(?:\\?.*)?$"))mime="application/vnd.apple.mpegurl";
            else if(url.matches("(?i).*\\.mpd(?:\\?.*)?$"))mime="application/dash+xml";
        }
        play.putExtra("com.vistastream.tv.extra.URL",url);
        play.putExtra("com.vistastream.tv.extra.TITLE",
            original.getStringExtra("title"));
        play.putExtra("com.vistastream.tv.extra.MIME_TYPE",mime);
        String pageUrl=original.getStringExtra("page_url");
        if(pageUrl!=null && (pageUrl.startsWith("https://")||pageUrl.startsWith("http://"))) {
            play.putExtra("com.vistastream.tv.extra.PAGE_URL",pageUrl);
            play.putExtra("com.vistastream.tv.extra.REFERER",pageUrl);
        }
        String userAgent=original.getStringExtra("user_agent");
        if(userAgent!=null && userAgent.length()<=350)
            play.putExtra("com.vistastream.tv.extra.USER_AGENT",userAgent);
        try {
            startActivity(play);
        }catch(RuntimeException e){
            Toast.makeText(this,"O player Vista Stream não conseguiu abrir o vídeo.",
                Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
'''
(to_dir / "NativePlayerActivity.java").write_text(bridge)

# Declare the browser and the Intent bridge as internal Activities, leaving
# Vista's launcher, permissions, namespace, service and Cast provider intact.
manifest=SRC / "AndroidManifest.xml"
xml=manifest.read_text()
assert 'com.harvester.browser.MainActivity' not in xml
block='''        <!-- Integrated Harvester Browser: internal, not a second launcher. -->
        <activity android:name="com.harvester.browser.MainActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:theme="@style/Theme.OpenStream" />
        <activity android:name="com.harvester.browser.NativePlayerActivity"
            android:exported="false"
            android:theme="@style/Theme.OpenStream" />
'''
assert xml.count('    </application>')==1
xml=xml.replace('    </application>',block+'    </application>',1)
manifest.write_text(xml)

# Add the browser as a real top-level destination on mobile; exiting Browser
# returns to Vista without leaving the app. TV launcher is unchanged.
navigation=JAVA / "com/ivor/openstream/presentation/navigation/AppNavigation.kt"
text=navigation.read_text()
assert 'data object Browser : Screen(' not in text
assert 'data object Marketplace : Screen(' in text
text=text.replace('import androidx.compose.material.icons.filled.Storefront',
    'import androidx.compose.material.icons.filled.Storefront\n'
    'import androidx.compose.material.icons.filled.Language\n'
    'import androidx.compose.ui.platform.LocalContext\n'
    'import android.content.Intent',1)
text=text.replace('    data object Marketplace : Screen(',
    '    data object Browser : Screen("browser", "Navegador", icon = Icons.Default.Language)\n'
    '    data object Marketplace : Screen(',1)
old='Screen.Marketplace, Screen.Downloads, Screen.History)'
assert text.count(old)==1
text=text.replace(old,'Screen.Marketplace, Screen.Browser, Screen.Downloads, Screen.History)',1)
old='''                composable(Screen.Home.route) {'''
assert text.count(old)==1
text=text.replace(old,'''                composable(Screen.Browser.route) {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        context.startActivity(Intent(context, com.harvester.browser.MainActivity::class.java))
                        navController.popBackStack()
                    }
                }
'''+old,1)
navigation.write_text(text)

# Keep existing official Harvester player & saved shelf unchanged: all links
# selected in the internal browser enter that same player, same Vista library.
assert 'HarvesterPlaybackActivity' in xml
assert 'CastOptionsProvider' in xml
assert 'com.harvester.browser.NativePlayerActivity' in (to_dir/'MainActivity.java').read_text() or 'NativePlayerActivity.class' in (to_dir/'MainActivity.java').read_text()
assert 'Harvester Browser' not in xml.split('<application',1)[1].split('<activity',1)[0], \
    "Main app identity has been replaced"
assert not (to_dir/"PlaybackService.java").exists(), "Do not ship competing VistaCast service"

print("UNIFIED VISTA OK: full Harvester feature with Vista-only playback / Cast.")
print("Browser classes:",len(list(to_dir.rglob("*.java"))))
print("Bundled scripts:",[p.name for p in assets.glob("*js")])
