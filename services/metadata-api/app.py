"""Vista Stream metadata gateway.

Run this on a server, NOT in the Android APK. Credentials are environment
variables, never response fields and never user-controlled target URLs.
Only the app's metadata endpoints are reachable through this API.
"""
import asyncio
import hmac
import os
import re
import time
from collections import defaultdict
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response

app = FastAPI(title="Vista Stream Metadata Gateway", docs_url=None, redoc_url=None)
TMDB_BASE = "https://api.themoviedb.org/3"
TVDB_BASE = "https://api4.thetvdb.com/v4"
TMDB_ALLOWED = [
    re.compile(r"^configuration$"),
    re.compile(r"^discover/(movie|tv)$"),
    re.compile(r"^trending/(all|movie|tv)/(day|week)$"),
    re.compile(r"^tv/(on_the_air|top_rated|airing_today|popular)$"),
    re.compile(r"^movie/(popular|top_rated|now_playing|upcoming)$"),
    re.compile(r"^search/(multi|movie|tv|person|keyword)$"),
    re.compile(r"^(movie|tv)/[0-9]{1,12}(/(external_ids|release_dates|content_ratings|recommendations|similar|videos|credits|images|watch/providers))?$"),
    re.compile(r"^tv/[0-9]{1,12}/season/[0-9]{1,4}$"),
    re.compile(r"^person/[0-9]{1,12}$"),
]
TVDB_ALLOWED = [
    re.compile(r"^search$"),
    re.compile(r"^series/[0-9]{1,12}(/extended|/episodes/official)?$"),
    re.compile(r"^episodes/[0-9]{1,12}(/extended)?$"),
]
QUERY_KEYS = frozenset({
    "page", "query", "language", "region", "sort_by", "with_genres",
    "with_keywords", "with_original_language", "with_original_country",
    "timezone", "append_to_response", "include_adult", "year",
    "first_air_date_year", "primary_release_year", "watch_region",
    "with_watch_providers", "vote_count.gte", "vote_average.gte",
    "air_date.gte", "air_date.lte", "first_air_date.gte", "first_air_date.lte",
    "include_video", "country", "type", "limit", "offset",
})
REQUESTS_PER_MINUTE = 60
MAX_CACHE = 512
cache: dict[str, tuple[float, int, bytes]] = {}
throttle: dict[str, list[float]] = defaultdict(list)
gateway_lock = asyncio.Lock()
tvdb_auth: tuple[float, str] | None = None


def secret(name: str) -> str:
    return os.environ.get(name, "").strip()


def valid_token(token: str) -> bool:
    tokens = [t.strip() for t in secret("VS_ACCESS_TOKENS").split(",") if t.strip()]
    return any(hmac.compare_digest(token, allowed) for allowed in tokens)


async def authorize(request: Request) -> None:
    presented = request.headers.get("x-vs-access-token", "")
    if not presented or not valid_token(presented):
        raise HTTPException(401, "Client access token required")
    # Per-token, in-process throttling; put Redis / gateway limits in front
    # when running more than one server instance.
    key = str(hashlib_sha256(presented))
    now = time.monotonic()
    async with gateway_lock:
        remaining = [stamp for stamp in throttle[key] if now - stamp < 60]
        if len(remaining) >= REQUESTS_PER_MINUTE:
            raise HTTPException(429, "Rate limit exceeded")
        remaining.append(now)
        throttle[key] = remaining


def hashlib_sha256(s: str) -> str:
    import hashlib
    return hashlib.sha256(s.encode()).hexdigest()


def require_allowed(path: str, allowed: list[re.Pattern]) -> None:
    if not any(pattern.fullmatch(path) for pattern in allowed):
        raise HTTPException(404, "Unsupported metadata endpoint")


def sanitized_params(request: Request) -> dict[str, str]:
    if len(str(request.url.query)) > 1800:
        raise HTTPException(400, "Query too long")
    result = {}
    for key, value in request.query_params.items():
        if key not in QUERY_KEYS or len(value) > 180:
            raise HTTPException(400, "Invalid metadata filter")
        if key == "page" and (not value.isdigit() or int(value) > 500 or int(value) < 1):
            raise HTTPException(400, "Invalid page")
        if key == "append_to_response" and any(
            part not in ("videos", "images", "credits", "combined_credits", "external_ids", "release_dates",
                         "content_ratings", "recommendations", "similar")
            for part in value.split(",")
        ):
            raise HTTPException(400, "Invalid append_to_response")
        result[key] = value
    result["include_adult"] = "false"
    result.setdefault("language", "pt-BR")
    return result


async def upstream(path: str, params: dict[str, str], headers: dict[str, str] | None = None) -> Response:
    from urllib.parse import urlencode
    cache_key = path + "?" + urlencode(sorted(params.items()))
    now = time.monotonic()
    saved = cache.get(cache_key)
    if saved and now - saved[0] < 1800:
        return Response(saved[2], status_code=saved[1], media_type="application/json",
                        headers={"Cache-Control": "private, max-age=300"})
    async with httpx.AsyncClient(timeout=httpx.Timeout(12.0), follow_redirects=False) as client:
        try:
            answer = await client.get(path, params=params, headers=headers)
        except httpx.HTTPError:
            raise HTTPException(502, "Metadata service unavailable")
    if answer.status_code != 200:
        # Never forward upstream request/auth details.
        return JSONResponse({"detail": "Metadata upstream unavailable"},
                            status_code=answer.status_code if answer.status_code in
                            (400, 401, 403, 404, 429) else 502)
    if len(answer.content) > 3_000_000:
        raise HTTPException(502, "Metadata response too large")
    if len(cache) >= MAX_CACHE:
        cache.pop(next(iter(cache)))
    cache[cache_key] = (now, 200, answer.content)
    return Response(answer.content, media_type="application/json",
                    headers={"Cache-Control": "private, max-age=300"})


@app.get("/health")
async def health():
    # Only booleans, never disclose any credentials or authorization material.
    return {
        "status": "ok",
        "tmdb_configured": bool(secret("TMDB_API_KEY")),
        "tvdb_configured": bool(secret("TVDB_API_KEY")),
        "tvdb_licensed": bool(secret("TVDB_API_KEY")) and secret("TVDB_LICENSED") == "true",
    }


@app.get("/api/status")
async def status(request: Request):
    await authorize(request)
    return {
        "tmdb": bool(secret("TMDB_API_KEY")),
        "tvdb": bool(secret("TVDB_API_KEY")) and secret("TVDB_LICENSED") == "true",
        "gatewayVersion": 1,
    }


# Public, read-only, limited metadata access for the official VS app.
# No embedded Android API key, token, arbitrary proxy target, or playback URL.
# Access protection should additionally be enforced by the hosting provider/WAF
# before promoting this hobby deployment to a multi-user commercial service.
PUBLIC_LIMIT_PER_MINUTE = 45
PUBLIC_GLOBAL_PER_MINUTE = 500
public_throttle: dict[str, list[float]] = defaultdict(list)

async def limit_public(request: Request) -> None:
    now = time.monotonic()
    # FastAPI's request.client.host is the connection peer, not a caller-
    # supplied forwarded-IP header, so it cannot be spoofed by the requester.
    client_ip = request.client.host if request.client else "unknown"
    key = "ip:" + hashlib_sha256(client_ip)
    async with gateway_lock:
        for scope, limit in ((key, PUBLIC_LIMIT_PER_MINUTE),
                             ("global", PUBLIC_GLOBAL_PER_MINUTE)):
            stamps = [t for t in public_throttle[scope] if now - t < 60]
            if len(stamps) >= limit:
                raise HTTPException(429, "Public catalog rate limit exceeded")
            public_throttle[scope] = stamps
        public_throttle[key].append(now)
        public_throttle["global"].append(now)
        # Bound memory usage by dropping inactive addresses periodically.
        if len(public_throttle) > 2000:
            for stale_key in list(public_throttle.keys())[:1000]:
                if stale_key != "global" and not public_throttle[stale_key]:
                    public_throttle.pop(stale_key, None)


@app.get("/api/public/tmdb/3/{path:path}")
async def public_tmdb(path: str, request: Request):
    if secret("VS_PUBLIC_CATALOG").lower() == "false":
        raise HTTPException(503, "Public catalog disabled")
    require_allowed(path, TMDB_ALLOWED)
    if not secret("TMDB_API_KEY"):
        raise HTTPException(503, "Metadata provider unavailable")
    params = sanitized_params(request)
    if int(params.get("page", "1")) > 100:
        raise HTTPException(400, "Invalid page")
    await limit_public(request)
    params["api_key"] = secret("TMDB_API_KEY")
    return await upstream(f"{TMDB_BASE}/{path}", params)


@app.get("/api/tmdb/3/{path:path}")
async def tmdb(path: str, request: Request):
    await authorize(request)
    require_allowed(path, TMDB_ALLOWED)
    api_key = secret("TMDB_API_KEY")
    if not api_key:
        raise HTTPException(503, "TMDB not configured on server")
    params = sanitized_params(request)
    params["api_key"] = api_key
    return await upstream(f"{TMDB_BASE}/{path}", params)


async def tvdb_bearer() -> str:
    global tvdb_auth
    now = time.monotonic()
    if tvdb_auth and tvdb_auth[0] > now:
        return tvdb_auth[1]
    async with httpx.AsyncClient(timeout=12.0, follow_redirects=False) as client:
        try:
            resp = await client.post(
                f"{TVDB_BASE}/login", json={"apikey": secret("TVDB_API_KEY")}
            )
        except httpx.HTTPError:
            raise HTTPException(502, "TVDB login unavailable")
    if resp.status_code != 200:
        raise HTTPException(502, "TVDB credentials rejected")
    bearer = resp.json().get("data", {}).get("token")
    if not isinstance(bearer, str) or not bearer:
        raise HTTPException(502, "TVDB login invalid")
    tvdb_auth = (now + 3600, bearer)
    return bearer


@app.get("/api/tvdb/v4/{path:path}")
async def tvdb(path: str, request: Request):
    await authorize(request)
    if not secret("TVDB_API_KEY") or secret("TVDB_LICENSED") != "true":
        raise HTTPException(503, "TVDB integration disabled pending valid license")
    require_allowed(path, TVDB_ALLOWED)
    params = sanitized_params(request)
    params.pop("include_adult", None)
    bearer = await tvdb_bearer()
    return await upstream(f"{TVDB_BASE}/{path}", params,
                          headers={"Authorization": f"Bearer {bearer}"})
