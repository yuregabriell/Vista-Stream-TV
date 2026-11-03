#!/usr/bin/env bash
set -euo pipefail
# Verified, pinned binary from the GPLv3 Nuvio Engine release.
# This is intentionally downloaded at build time, not checked in as a binary.
VERSION="0.1.6"
EXPECTED="66b049a3625bfdc39c3b5d25a0af9bbc127c530512617e9f57cd988c019d33d6"
TARGET="vendor/openstream/app/libs/nuvio-engine-android-${VERSION}.aar"
URL="https://github.com/NuvioMedia/nuvio-engine/releases/download/v${VERSION}/nuvio-engine-android-${VERSION}.aar"
mkdir -p "$(dirname "$TARGET")"
if [[ -f "$TARGET" ]] && echo "$EXPECTED  $TARGET" | sha256sum --check --status; then
  echo "Nuvio Engine $VERSION already verified"
  exit 0
fi
curl --fail --location --show-error --silent --retry 3 --retry-delay 2 "$URL" --output "$TARGET.download"
echo "$EXPECTED  $TARGET.download" | sha256sum --check
mv "$TARGET.download" "$TARGET"
echo "Pinned GPLv3 native engine verified for Android"
