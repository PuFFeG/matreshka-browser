#!/usr/bin/env bash
# Build and sign an ARM64 release, then write dist/<name> and SHA256SUMS.
# Usage: VERSION=0.2.0 FIREFOX_SRC=/path/to/firefox ./scripts/release.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${FIREFOX_SRC:-}"
VERSION="${VERSION:-}"
[ -n "$SRC" ] || { echo "error: set FIREFOX_SRC" >&2; exit 2; }
[ -n "$VERSION" ] || { echo "error: set VERSION (e.g. 160.0a1)" >&2; exit 2; }

"$ROOT/scripts/apply.sh" "$SRC"
OUT="$("$ROOT/scripts/build-release.sh" "$SRC" | sed -n 's/^APK=//p' | tail -1)"
[ -f "$OUT" ] || { echo "error: build did not produce an APK" >&2; exit 1; }

DIST="$ROOT/dist"
mkdir -p "$DIST"
TARGET="$DIST/matreshka-browser-$VERSION-arm64-v8a-release.apk"
"$ROOT/scripts/sign-apk.sh" "$OUT" "$TARGET"
"$ROOT/scripts/verify-apk.sh" "$TARGET"
(cd "$DIST" && sha256sum "$(basename "$TARGET")" > SHA256SUMS)
echo "Released $TARGET"
