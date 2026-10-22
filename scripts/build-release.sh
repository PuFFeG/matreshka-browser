#!/usr/bin/env bash
# Build an ARM64 Fenix release APK from a patched Firefox source tree.
# Usage: FIREFOX_SRC=/path/to/firefox ./scripts/build-release.sh
set -euo pipefail
SRC="${1:-${FIREFOX_SRC:-}}"
[ -n "$SRC" ] || { echo "error: pass the Firefox source directory or set FIREFOX_SRC" >&2; exit 2; }
[ -x "$SRC/mach" ] || { echo "error: mach not found in $SRC" >&2; exit 1; }

if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/.mozbuild/jdk" ]; then
    JAVA_HOME="$(find "$HOME/.mozbuild/jdk" -maxdepth 1 -mindepth 1 -type d | sort | tail -1)"
    export JAVA_HOME
fi
export MOZCONFIG="${MOZCONFIG:-$SRC/mozconfig-arm64}"

cd "$SRC"
echo "==> Building fenix:assembleRelease (MOZCONFIG=$MOZCONFIG)"
./mach gradle fenix:assembleRelease

APK="$(find "$SRC"/obj-*-android/gradle/build/mobile/android/fenix/app/outputs/apk/release \
    -name 'fenix-arm64-v8a-release.apk' -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)"
[ -n "$APK" ] || { echo "error: ARM64 release APK not found" >&2; exit 1; }
echo "APK=$APK"
