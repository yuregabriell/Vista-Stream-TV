# Vista Stream — Metadata Gateway

Optional, authenticated backend for the **existing** VS Android/TV application.
TMDB/TVDB credentials live as secret **server** environment variables. No
TMDB/TVDB keys, or fixed shared client tokens, are compiled into the APK.

## Run locally

```bash
cd services/metadata-api
cp .env.example .env
# Edit .env with private settings
docker build -t vista-metadata .
docker run --env-file .env -p 127.0.0.1:8000:8000 vista-metadata
```

For Android on a different device, deploy it behind an HTTPS reverse proxy at
a domain you control. **Do not put the proxy directly on the public internet
without access control, TLS, rate limits and abuse protection.**

Client routes (HTTP GET only):
- `/health`: basic liveness (no secrets)
- `/api/status`: requires `X-VS-Access-Token`
- `/api/tmdb/3/...`: whitelisted metadata paths, parameter constraints, cache
- `/api/tvdb/v4/...`: restricted TVDB paths, **disabled by default** until a license has been granted to this VS project

For authenticated routes, configure a separate high-entropy token in
`VS_ACCESS_TOKENS` for each authorized device (comma separated). Set the
gateway HTTPS URL + that device's token in **VS → Definições → Servidor de
metadados**. This is a first-stage, single-instance access model; a public
multiuser service should replace it with account-based tokens, revocation,
per-user rate limits, centralized Redis rate limiting, monitoring and TLS.

The gateway never accesses user-supplied arbitrary addresses, forwards
authorization credentials to callers, or uses uploaded secrets. The TVDB
feature has a license gate on purpose. Verify the current TMDB attribution and
TVDB licensing requirements before public/commercial distribution.

## Important release behavior

The VS Android release CI intentionally DOES NOT read a `TMDB_API_KEY` secret:
Android `BuildConfig` fields are extractable from APKs. Keep secrets on the
backend deployment, not in GitHub Actions for APK compilation.

If no secure gateway is configured, VS preserves its previous user-entered TMDB
key option on that device and the public Cinemeta/AniList fallback. Publishing
this source does not automatically deploy/activate a server.
