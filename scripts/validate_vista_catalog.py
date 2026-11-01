#!/usr/bin/env python3
"""Validate the remotely distributed Vista addon list without importing executable plugins."""
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

path = Path("extensions/vista-catalog.json")
doc = json.loads(path.read_text(encoding="utf-8"))
assert isinstance(doc, dict), "Catalog must be an object"
assert doc.get("manifestVersion") == 1, "Unsupported manifestVersion"
assert isinstance(doc.get("extensions"), list), "extensions must be an array"
seen = set()


def public_https(value, field, required=False):
    if not value and not required:
        return
    assert isinstance(value, str) and value.startswith("https://"), f"{field}: HTTPS required"
    parsed = urlsplit(value)
    assert parsed.hostname and not parsed.username and not parsed.password, f"{field}: invalid URL"
    # A public repository must never leak per-account authentication URLs.
    assert not parsed.query and not parsed.fragment, f"{field}: use public URLs without tokens/queries"


for i, entry in enumerate(doc["extensions"]):
    key = f"extensions[{i}]"
    assert isinstance(entry, dict), f"{key}: entry must be an object"
    addon_id = entry.get("id")
    assert isinstance(addon_id, str) and re.fullmatch(r"[a-z0-9][a-z0-9_-]{1,90}", addon_id), f"{key}: invalid id"
    assert addon_id not in seen, f"{key}: duplicate id {addon_id}"
    seen.add(addon_id)
    assert isinstance(entry.get("name"), str) and entry["name"].strip(), f"{key}: name required"
    assert isinstance(entry.get("language", "Multi"), str), f"{key}: language must be a string"
    for prop in ("authors", "tags", "contentTypes", "titleExamples"):
        values = entry.get(prop, [])
        assert isinstance(values, list) and all(isinstance(s, str) for s in values), f"{key}: {prop} must be strings"
    public_https(entry.get("manifestUrl"), f"{key}.manifestUrl")
    public_https(entry.get("homepage"), f"{key}.homepage")
    public_https(entry.get("iconUrl"), f"{key}.iconUrl")
    assert not entry.get("installedByDefault", False), f"{key}: no remote auto-install"
    engine = entry.get("engine")
    assert isinstance(engine, dict), f"{key}: engine required"
    engine_type = engine.get("type")
    assert engine_type in {
        "stremio", "web-embed", "vidking-direct", "vidking-webview",
        "native-harvester", "native-anime-html", "unsupported"
    }, f"{key}: unrecognised engine type"
    if engine_type == "stremio":
        public_https(engine.get("endpoint"), f"{key}.engine.endpoint", required=True)
        assert entry.get("manifestUrl"), f"{key}: Stremio entries must link to a manifest"
        assert entry["manifestUrl"].endswith("/manifest.json"), f"{key}: expected /manifest.json"
    elif engine_type in {"native-harvester", "native-anime-html"}:
        raise AssertionError(f"{key}: cannot remotely install native engine implementations")
    for attr in ("movieUrl", "tvUrl"):
        if engine.get(attr):
            public_https(engine[attr], f"{key}.engine.{attr}", required=True)

print(f"Validated {len(seen)} curated Vista addon entries in {path}")
