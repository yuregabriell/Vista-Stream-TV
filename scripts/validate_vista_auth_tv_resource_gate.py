#!/usr/bin/env python3
"""Static regression check: mandatory login and TV playback resource safeguards."""
from pathlib import Path
root = Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
def read(rel): return (root / rel).read_text(encoding="utf-8")

main=read("MainActivity.kt")
profiles=read("presentation/profiles/ProfileScreens.kt")
home=read("presentation/home/HomeScreen.kt")
nav=read("presentation/navigation/AppNavigation.kt")
mobile=read("presentation/settings/SettingsScreen.kt")
tv=read("presentation/tv/TvSettingsScreen.kt")
guard=read("data/streaming/TvPlaybackSearchGuard.kt")
player=read("presentation/player/PlayerViewModel.kt")
stream=read("data/streaming/StreamingRepositoryImpl.kt")
session=read("presentation/player/session/PlaybackSession.kt")
budget=read("data/cache/CacheBudget.kt")
checks={
    "Auth required before root navigation": "if (!vistaSignedIn)" in main and "VistaSignInGate(" in main,
    "No guest entry in root": "guest" not in read("presentation/auth/VistaSignInGate.kt").lower(),
    "Profile selection before catalog": "else if (!profileSelectedOnLaunch)" in main and "WhoIsWatchingScreen(" in main,
    "Settings from profile": "onSettings: () -> Unit" in profiles and "onSettings = { accessPage = \"settings\" }" in main,
    "App updater inside settings": "onOpenUpdate" in mobile and "onOpenUpdate" in tv,
    "Top bar has plugins and history": 'Icon(Icons.Default.Storefront, contentDescription = "Plugins")' in home and 'Icon(Icons.Default.History, contentDescription = "Histórico")' in home,
    "Compact bottom bar excludes plugin/history": "listOf(Screen.Home, Screen.Search, Screen.WatchLater, Screen.Downloads)" in nav,
    "TV disk 128 MiB": "TV_P2P_DISK_MAX_BYTES = 128L" in budget,
    "TV P2P RAM stays 16 MiB": "TV_P2P_MEMORY_MAX_BYTES = 16L" in budget,
    "Prevent provider scans in TV playback": "TvPlaybackSearchGuard.track(work)" in stream and "TvPlaybackSearchGuard.beginPlayback()" in player,
    "TV adaptive Media3 memory budget": "setTargetBufferBytes(24 * 1024 * 1024)" in session,
    "TV player stops background sources": "stopTvBackgroundSourceDiscovery()" in player,
}
for k,v in checks.items(): print(("OK: " if v else "FAIL: ")+k)
assert all(checks.values()), "VistaStream TV/login UI regression"
