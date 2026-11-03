#!/usr/bin/env python3
"""Validate published opt-in PT-BR preset and bundled offline copy."""
import json
from pathlib import Path

published = Path("presets/pt-br.json").read_text(encoding="utf-8")
bundled = Path("vendor/openstream/app/src/main/assets/presets/pt-br.json").read_text(encoding="utf-8")
assert published == bundled, "Bundled preset must match published source"
data = json.loads(published)
assert data["format"] == "vista-extensions" and data["version"] == 1
assert data.get("presetId") == "pt-br"
repos = data["repositories"]
installed = data["installed"]
order = data["sourceOrder"]
assert len(repos) == len(set(repos)) == 5
assert len(installed) == 21
assert len({e["key"] for e in installed}) == len(installed)
assert all(e["enabled"] is True for e in installed)
assert all(k in {e["key"] for e in installed} for k in order)
assert len(order) == 5
assert all(u.startswith("https://") for u in repos)
print(f"Validated opt-in PT-BR preset: {len(installed)} sources, {len(repos)} additional repositories")

# The Android TV UI must expose the same opt-in preset as the phone marketplace.
tv = Path("vendor/openstream/app/src/main/java/com/ivor/openstream/presentation/tv/TvExtensionsScreen.kt").read_text(encoding="utf-8")
assert '"Presets"' in tv
assert 'viewModel.applyPortuguesePreset()' in tv
assert 'viewModel.presetInstalling.collectAsState()' in tv
assert 'TvConfirmPanel(' in tv
print("Verified Android TV preset tab and explicit confirmation wiring.")
