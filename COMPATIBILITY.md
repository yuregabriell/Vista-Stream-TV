# Compatibility analysis (24 October 2026)

| Project | Source license | Actual status | Manual addition |
|---|---|---|---|
| OpenStream | MIT | Literal imported Android base; TV intent, Media3, Compose, JSON extension catalogs and Stremio support | OpenStream compatible repositories/manifests via app |
| GoAnime | MIT | Literal Go sources in vendor; **not** running on Android | No Go plugins; needs port to Kotlin or Go bridge |
| SaimoPlayer | Unspecified | Not copied; different Swift/macOS technology | Not available |
| CloudstreamExtensions | Unspecified | Not copied; .cs3 binary ABI is incompatible with OpenStream | No direct .cs3 installation |
| IPTV M3U | Varies | Separate adapter needed; not claiming native import in this OpenStream-derived build | Not yet |
| PT-BR/legendas | Media-dependent | Player offers track selection where provided, not guaranteed per title | Subtitle providers supported by upstream |
| No advertising | App only | UI will not add advertising; cannot guarantee external sources or bypass their restrictions | Choose permitted ad-free sources |

Important: OpenStream is originally optimized for phones/tablets; despite a TV launcher entry, D-pad behavior must be tested on a real remote. TMDB needs a user's key (GitHub Actions secret `TMDB_API_KEY`) for reliable metadata; demo fallback is rate-limited. Debug APK updates may require uninstall due to ephemeral signing keys.
