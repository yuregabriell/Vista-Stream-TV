#!/usr/bin/env python3
"""Static guardrails for Android TV navigation and disposable storage."""
from pathlib import Path

src = Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
def read(relative):
    return (src / relative).read_text(encoding="utf-8")

budget = read("data/cache/CacheBudget.kt")
native = read("data/streaming/providers/VistaNativeP2pBridge.kt")
preview = read("presentation/tv/TvEpisodeDetailsScreen.kt")
vm = read("presentation/player/PlayerViewModel.kt")
session = read("presentation/player/session/PlaybackSession.kt")
artwork = read("data/images/ArtworkPrefetcher.kt")
app = read("OpenStreamApp.kt")

checks = {
    "TV artwork bound": "TV_ARTWORK_MAX_BYTES = 24L" in budget,
    "TV metadata bound": "TV_METADATA_MAX_BYTES = 16L" in budget,
    "TV P2P bound": "TV_P2P_DISK_MAX_BYTES = 128L" in budget,
    "Native engine uses TV disk bound": "CacheBudget.TV_P2P_DISK_MAX_BYTES" in native,
    "Native engine released after playback": "purgeNativeP2pTemporaryData(app.cacheDir)" in native,
    "Hidden preview cancels jobs": "playerViewModel.cancelSourcePreview()" in preview,
    "Preview P2P cleanup when leaving without playback": "playerViewModel.releaseAbandonedPreviewSources()" in preview,
    "Do not release a stream when navigating to player": "if (!navigatingToPlayer)" in preview,
    "Preview view model exposes scoped cleanup": "fun releaseAbandonedPreviewSources()" in vm,
    "Cleanup preserves current playback URL": "releaseUnusedExcept(_nowPlaying.value?.mediaUri)" in session,
    "No TV offscreen artwork prefetch": "if (TvDetect.isTv(context)) return false" in artwork,
    "Image loader respects TV limits": "CacheBudget.artworkBudget(wanted, tvDevice)" in app,
}
for name, passed in checks.items():
    print(f"{'OK' if passed else 'FAIL'}: {name}")
assert all(checks.values()), "TV memory/cache regression guard failed"
