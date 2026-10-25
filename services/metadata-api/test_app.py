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
