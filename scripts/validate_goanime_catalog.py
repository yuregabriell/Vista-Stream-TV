#!/usr/bin/env python3
"""Validate the JSON catalog published by VistaStream for GoAnime-derived adapters."""
import json
from pathlib import Path

path = Path("extensions/goanime-catalog.json")
data = json.loads(path.read_text(encoding="utf-8"))
assert data["manifestVersion"] == 1
assert data["name"].startswith("GoAnime")
entries = data.get("extensions") or []
assert len(entries) >= 2
ids = [entry["id"] for entry in entries]
assert len(ids) == len(set(ids))
for entry in entries:
    assert entry["language"] == "Português (Brasil)"
    assert entry["engine"]["type"] == "native-anime-html"
    assert entry["engine"]["endpoint"].startswith("https://")
    assert entry["installedByDefault"] is False
    assert entry["status"] == 3
print(f"Validated GoAnime opt-in JSON catalog: {len(entries)} compatible Android adapters.")
