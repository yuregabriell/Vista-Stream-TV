import os
import pytest
from fastapi.testclient import TestClient
from fastapi.responses import JSONResponse
import app as gateway

client = TestClient(gateway.app)
HEADERS = {"X-VS-Access-Token": "test-client-access-token-0000000001"}

@pytest.fixture(autouse=True)
def env(monkeypatch):
    monkeypatch.setenv("VS_ACCESS_TOKENS", HEADERS["X-VS-Access-Token"])
    monkeypatch.setenv("TMDB_API_KEY", "test-tmdb-secret")
    monkeypatch.delenv("TVDB_API_KEY", raising=False)
    monkeypatch.delenv("TVDB_LICENSED", raising=False)
    gateway.cache.clear()
    gateway.throttle.clear()
    gateway.public_throttle.clear()
    gateway.season_overlay_cache.clear()
    gateway.tvdb_auth = None

def test_health_never_exposes_keys():
    response = client.get("/health")
    assert response.status_code == 200
    assert "test-tmdb-secret" not in response.text

def test_private_gateway_rejects_unauthorized_users():
    assert client.get("/api/status").status_code == 401
    assert client.get("/api/tmdb/3/configuration").status_code == 401

def test_status_safe_and_tmdb_configured():
    r = client.get("/api/status", headers=HEADERS)
    assert r.status_code == 200
    assert r.json()["tmdb"] is True
    assert r.json()["tvdb"] is False
    assert "test-tmdb-secret" not in r.text

def test_disallows_open_proxy_or_secret_overrides():
    assert client.get("/api/tmdb/3/https://example.org/", headers=HEADERS).status_code == 404
    assert client.get("/api/tmdb/3/search/movie?api_key=evil", headers=HEADERS).status_code == 400
    assert client.get("/api/tmdb/3/discover/movie?page=0", headers=HEADERS).status_code == 400
    assert client.get("/api/tmdb/3/tv/1?append_to_response=evil", headers=HEADERS).status_code == 400

def test_passes_only_server_key_to_tmdb(monkeypatch):
    received = {}
    async def mock_upstream(path, params, headers=None):
        received["path"] = path
        received["params"] = params
        return JSONResponse({"results": []})
    monkeypatch.setattr(gateway, "upstream", mock_upstream)
    r = client.get("/api/tmdb/3/search/movie?query=anime", headers=HEADERS)
    assert r.status_code == 200
    assert received["path"] == "https://api.themoviedb.org/3/search/movie"
    assert received["params"]["api_key"] == "test-tmdb-secret"
    assert received["params"]["language"] == "pt-BR"
    assert received["params"]["include_adult"] == "false"

def test_tvdb_disabled_until_licensed():
    response = client.get("/api/tvdb/v4/search", headers=HEADERS)
    assert response.status_code == 503

def test_public_tmdb_requires_no_client_key_and_does_not_return_credentials(monkeypatch):
    upstream_requests = []
    async def fake(path, params, headers=None):
        upstream_requests.append((path, params))
        return JSONResponse({"results": [{"id": 7}]})
    monkeypatch.setattr(gateway, "upstream", fake)
    result = client.get("/api/public/tmdb/3/trending/all/week?page=1")
    assert result.status_code == 200
    assert result.json()["results"][0]["id"] == 7
    assert upstream_requests[0][0].endswith("/trending/all/week")
    assert upstream_requests[0][1]["api_key"] == "test-tmdb-secret"
    assert "test-tmdb-secret" not in result.text


def test_public_tmdb_not_an_open_proxy_and_api_key_override_forbidden():
    assert client.get("/api/public/tmdb/3/https://evil.example/").status_code == 404
    assert client.get("/api/public/tmdb/3/search/movie?api_key=evil").status_code == 400
    assert client.get("/api/public/tmdb/3/discover/movie?page=101").status_code == 400


def test_public_tv_details_allow_aggregate_credits(monkeypatch):
    """The Android details page requests aggregate_credits for TV casts.
    An older whitelist returned HTTP 400 before forwarding to TMDB.
    """
    seen = {}
    async def mock_upstream(url, params, headers=None):
        seen["url"] = url
        seen["append"] = params.get("append_to_response")
        return JSONResponse({"id": 1396, "name": "Example series", "aggregate_credits": {"cast": []}})
    monkeypatch.setattr(gateway, "upstream", mock_upstream)
    result = client.get(
        "/api/public/tmdb/3/tv/1396"
        "?append_to_response=videos,aggregate_credits,recommendations"
    )
    assert result.status_code == 200
    assert seen["url"].endswith("/tv/1396")
    assert seen["append"] == "videos,aggregate_credits,recommendations"


def test_private_tv_details_allow_aggregate_credits(monkeypatch):
    async def mock_upstream(url, params, headers=None):
        return JSONResponse({"id": 77, "aggregate_credits": {"cast": []}})
    monkeypatch.setattr(gateway, "upstream", mock_upstream)
    result = client.get(
        "/api/tmdb/3/tv/77?append_to_response=videos,aggregate_credits,recommendations",
        headers=HEADERS,
    )
    assert result.status_code == 200


def test_public_tmdb_limited(monkeypatch):
    async def fake(path, params, headers=None):
        return JSONResponse({"results": []})
    monkeypatch.setattr(gateway, "upstream", fake)
    for i in range(gateway.PUBLIC_LIMIT_PER_MINUTE):
        assert client.get("/api/public/tmdb/3/search/movie?query=a" + str(i)).status_code == 200
    assert client.get("/api/public/tmdb/3/search/movie?query=overflow").status_code == 429


def test_public_catalog_can_be_disabled(monkeypatch):
    monkeypatch.setenv("VS_PUBLIC_CATALOG", "false")
    assert client.get("/api/public/tmdb/3/trending/all/week").status_code == 503


def test_tvdb_enabled_provides_privacy_safe_provider_status(monkeypatch):
    monkeypatch.setenv("TVDB_API_KEY", "test-tvdb-secret")
    monkeypatch.setenv("TVDB_LICENSED", "true")
    async def fake_bearer():
        return "never-share-the-bearer"
    monkeypatch.setattr(gateway, "tvdb_bearer", fake_bearer)
    response = client.get("/api/public/tvdb/status")
    assert response.status_code == 200
    assert response.json() == {"configured": True, "enabled": True, "authenticated": True}
    assert "test-tvdb-secret" not in response.text
    assert "never-share-the-bearer" not in response.text


def test_tvdb_overlays_missing_episodes_preserving_tmdb_identity(monkeypatch):
    monkeypatch.setenv("TVDB_API_KEY", "test-tvdb-secret")
    monkeypatch.setenv("TVDB_LICENSED", "true")
    async def fake_bearer():
        return "mock-bearer"
    monkeypatch.setattr(gateway, "tvdb_bearer", fake_bearer)
    calls = []
    async def fake_upstream(path, params, headers=None):
        calls.append(path)
        if path.endswith("/tv/77/season/1"):
            return JSONResponse({"id": 888, "episodes": [
                {"id": 11, "season_number": 1, "episode_number": 1, "name": "Episódio 1", "overview": ""},
                {"id": 12, "season_number": 1, "episode_number": 2, "name": "Original TMDB", "overview": "Descrição TMDB"}
            ]})
        if path.endswith("/tv/77/external_ids"):
            return JSONResponse({"tvdb_id": 999})
        if path.endswith("/series/999/episodes/official/por"):
            assert headers == {"Authorization": "Bearer mock-bearer"}
            return JSONResponse({"data": {"episodes": [
                {"seasonNumber": 1, "number": 1, "name": "Nome TVDB", "overview": "Descrição TVDB"},
                {"seasonNumber": 1, "number": 2, "name": "Outro título", "overview": "Sobrescrever"}
            ]}})
        raise AssertionError(path)
    monkeypatch.setattr(gateway, "upstream", fake_upstream)
    result = client.get("/api/public/tmdb/3/tv/77/season/1")
    assert result.status_code == 200
    body = result.json()
    assert body["episodes"][0]["id"] == 11
    assert body["episodes"][0]["name"] == "Nome TVDB"
    assert body["episodes"][0]["overview"] == "Descrição TVDB"
    assert body["episodes"][1]["name"] == "Original TMDB"
    assert body["episodes"][1]["overview"] == "Descrição TMDB"
    assert body["_metadata_sources"] == ["TMDB", "TheTVDB"]
    assert "test-tvdb-secret" not in result.text
    assert result.headers["x-vs-metadata"] == "TMDB+TheTVDB"
    assert any(p.endswith("/series/999/episodes/official/por") for p in calls)


def test_season_still_available_when_tvdb_auth_fails(monkeypatch):
    monkeypatch.setenv("TVDB_API_KEY", "test-tvdb-secret")
    monkeypatch.setenv("TVDB_LICENSED", "true")
    async def fail_login():
        raise gateway.HTTPException(502, "TVDB rejected key")
    monkeypatch.setattr(gateway, "tvdb_bearer", fail_login)
    async def fake_upstream(path, params, headers=None):
        if path.endswith("/tv/77/external_ids"):
            return JSONResponse({"tvdb_id": 999})
        return JSONResponse({"episodes": [{"id": 3, "name": "TMDB original"}]})
    monkeypatch.setattr(gateway, "upstream", fake_upstream)
    resp = client.get("/api/public/tmdb/3/tv/77/season/1")
    assert resp.status_code == 200
    assert resp.json()["episodes"][0]["name"] == "TMDB original"
    assert "_metadata_sources" not in resp.json()
