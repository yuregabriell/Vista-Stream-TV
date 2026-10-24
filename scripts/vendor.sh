#!/usr/bin/env bash
set -euo pipefail
OPENSTREAM_SHA=d00b4d1f0f0ab1db8590746fbe18a1845a30738e
GOANIME_SHA=85258f7b483064e428b0797955e320c152c8a7ea
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fetch_repo() {
  local src="$1" ref="$2" sha="$3" dest="$4"
  local directory="$WORK/$(basename "$dest")"
  if [[ -f "$dest/LICENSE" ]]; then
    echo "Preserving customized source $dest"
    return
  fi
  git clone --quiet --depth 1 --branch "$ref" "https://github.com/$src.git" "$directory"
  git -C "$directory" fetch --quiet --depth 1 origin "$sha"
  git -C "$directory" checkout --quiet --detach "$sha"
  mkdir -p "$dest"
  rsync -a --delete --exclude='.git' --exclude='.gradle' "$directory/" "$dest/"
}
fetch_repo Ivorisnoob/OpenStream beta "$OPENSTREAM_SHA" vendor/openstream
fetch_repo alvarorichard/GoAnime main "$GOANIME_SHA" vendor/goanime
test -f vendor/openstream/LICENSE
test -f vendor/goanime/LICENSE
echo "Original MIT notices are preserved with vendored code."
