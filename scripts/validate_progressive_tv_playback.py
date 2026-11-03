#!/usr/bin/env python3
"""Static regression checks for fast TV title opening and progressive source playback."""
from pathlib import Path

root = Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
def read(path):
    return (root / path).read_text(encoding="utf-8")

details = read("presentation/tv/TvEpisodeDetailsScreen.kt")
streaming = read("data/streaming/StreamingRepositoryImpl.kt")
player = read("presentation/player/PlayerViewModel.kt")
screen = read("presentation/player/PlayerScreen.kt")
stremio = read("data/streaming/providers/StremioAddonProvider.kt")
bridge = read("data/streaming/providers/VistaNativeP2pBridge.kt")
cache = read("data/streaming/ResolvedStreamCache.kt")
checks = {
    "TV title does not start full source scan": "resolveStreams = false" in details,
    "Sources selection can start a scan": "playerViewModel.retryResolution()" in details,
    "Progressive first-candidate policy": "PlaybackSourcePolicy.select(" in player,
    "TV auto search always stops at first provider": "SourceSearchPolicy.firstPlayableOnlyOnTv" in streaming,
    "TV uses at most two concurrent provider workers": "TV_MAX_CONCURRENT_PROVIDERS = 2" in streaming,
    "TV cancels unfinished discovery as soon as a stream is found": "currentCoroutineContext().cancelChildren()" in streaming,
    "TV remembered source does not stall for ten seconds": "TV_REMEMBERED_HEAD_START_MS = 1_500L" in streaming,
    "TV player cancels outstanding discovery after playback starts": "stopTvBackgroundSourceDiscovery()" in player,
    "Explicit Find more remains full discovery": "fullScan = true" in player,
    "A failed first source does not restart unfinished discovery": "resolutionJob?.isActive == true" in player,
    "Partial result reusable for subsequent manual search": "partialSeed = ranked" in streaming,
    "Direct sources do not wait for IMDb": "if (provider.requiresIdEnrichment)" in streaming,
    "Concurrent providers bounded on TV and phone": "TV_MAX_CONCURRENT_PROVIDERS = 2" in streaming and "MAX_CONCURRENT_PROVIDERS = 8" in streaming and "providerSlots = Semaphore(" in streaming,
    "Partial cache does not suppress full discovery": "existing.resolution.isComplete" in cache,
    "Addon probe has finite quick budget": "VERIFY_BUDGET_MS = 1_500L" in stremio,
    "Only one native torrent warms up on TV": "nativeBridge.isTelevisionDevice()) 1 else 2" in stremio,
    "P2P TV detection available to addon": "fun isTelevisionDevice()" in bridge,
    "TV loading backdrop avoids expensive blur": "if (tvControls) Modifier.fillMaxSize()" in screen,
    "Known media formats avoid unnecessary preflight": 'sourceToInspect.streamType == "STREAM"' in screen,
}
for name, ok in checks.items():
    print(("OK" if ok else "FAIL") + ": " + name)
assert all(checks.values()), "Progressive TV playback regression"
