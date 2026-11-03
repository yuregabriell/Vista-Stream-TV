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
cache = read("data/streaming/ResolvedStreamCache.kt")
checks = {
    "TV title does not start full source scan": "resolveStreams = false" in details,
    "Sources selection can start a scan": "playerViewModel.retryResolution()" in details,
    "Progressive first-candidate policy": "PlaybackSourcePolicy.select(" in player,
    "Partial result cached and background scan continues": "partialSeed = ranked" in streaming,
    "Direct sources do not wait for IMDb": "if (provider.requiresIdEnrichment)" in streaming,
    "Concurrent providers bounded": "Semaphore(MAX_CONCURRENT_PROVIDERS)" in streaming,
    "Partial cache does not suppress full discovery": "existing.resolution.isComplete" in cache,
    "Addon probe has finite quick budget": "VERIFY_BUDGET_MS = 1_500L" in stremio,
    "TV loading backdrop avoids expensive blur": "if (tvControls) Modifier.fillMaxSize()" in screen,
    "Known media formats avoid unnecessary preflight": 'sourceToInspect.streamType == "STREAM"' in screen,
}
for name, ok in checks.items():
    print(("OK" if ok else "FAIL") + ": " + name)
assert all(checks.values()), "Progressive TV playback regression"
