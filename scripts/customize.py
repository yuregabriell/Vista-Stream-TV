#!/usr/bin/env python3
"""Patch a literal snapshot of OpenStream without renaming its Kotlin namespace."""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
old = 'applicationId = "com.ivor.openstream"'
if old in text:
    text = text.replace(old, 'applicationId = "com.vistastream.tv"')
else:
    assert 'applicationId = "com.vistastream.tv"' in text
text = text.replace('versionName = "2.2"', 'versionName = "0.2.0-vista"')
build.write_text(text, encoding="utf-8")

settings = root / "settings.gradle.kts"
settings.write_text(settings.read_text(encoding="utf-8").replace(
    'rootProject.name = "OpenStream"', 'rootProject.name = "VistaStreamTV"'
), encoding="utf-8")
labels = root / "app/src/main/res/values/strings.xml"
s = labels.read_text(encoding="utf-8")
assert re.search(r'<string name="app_name"[^>]*>', s), "App label changed upstream"
s = re.sub(r'<string name="app_name"[^>]*>.*?</string>',
           '<string name="app_name">Vista Stream TV</string>', s, count=1, flags=re.S)
labels.write_text(s, encoding="utf-8")

xml = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert "android.intent.category.LEANBACK_LAUNCHER" in xml

# Prevent the original app's updater from suggesting third-party branded APKs.
github_api = root / "app/src/main/java/com/ivor/openstream/data/remote/GithubApi.kt"
api = github_api.read_text(encoding="utf-8")
original_route = 'repos/ivorisnoob/openstream/releases/latest'
assert original_route in api or 'repos/yuregabriell/Vista-Stream-TV/releases/latest' in api
github_api.write_text(api.replace(
    original_route, 'repos/yuregabriell/Vista-Stream-TV/releases/latest'
), encoding="utf-8")

print("App ID and branding changed. TV launcher intent present; D-pad still requires device testing.")
