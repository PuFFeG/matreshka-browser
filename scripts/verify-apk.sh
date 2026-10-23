#!/usr/bin/env bash
# Verify the public identity of an APK with independent Android tools.
# Usage: ./scripts/verify-apk.sh path/to/app.apk
set -euo pipefail
APK="${1:?usage: verify-apk.sh path/to/app.apk}"
[ -f "$APK" ] || { echo "error: APK not found: $APK" >&2; exit 1; }

EXPECTED_PACKAGE="${EXPECTED_PACKAGE:-ru.matreshka.browser}"
EXPECTED_LABEL="${EXPECTED_LABEL:-matreshka-browser}"
EXPECTED_VERSION="${EXPECTED_VERSION:-0.2.0}"

if [ -n "${ANDROID_BUILD_TOOLS:-}" ]; then
    BT="$ANDROID_BUILD_TOOLS"
else
    BT="$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
fi
AAPT="$BT/aapt"
AAPT2="$BT/aapt2"
[ -x "$AAPT" ] && [ -x "$AAPT2" ] || { echo "error: aapt/aapt2 not found in $BT" >&2; exit 1; }

PKG="$("$AAPT2" dump packagename "$APK")"
BADGING="$("$AAPT" dump badging "$APK")"
LABEL="$(printf '%s\n' "$BADGING" | awk -F"'" '/^application-label:/{print $2; exit}')"
VERSION="$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='[^']*' versionCode='[^']*' versionName='\([^']*\)'.*/\1/p" | head -1)"

fail=0
[ -n "$PKG" ] && [ "$PKG" != "null" ] && [ "$PKG" = "$EXPECTED_PACKAGE" ] || { echo "FAIL package: '$PKG' (expected '$EXPECTED_PACKAGE')" >&2; fail=1; }
[ -n "$LABEL" ] && [ "$LABEL" = "$EXPECTED_LABEL" ] || { echo "FAIL label: '$LABEL' (expected '$EXPECTED_LABEL')" >&2; fail=1; }
[ -n "$VERSION" ] && [ "$VERSION" = "$EXPECTED_VERSION" ] || { echo "FAIL version: '$VERSION' (expected '$EXPECTED_VERSION')" >&2; fail=1; }

printf 'package: %s\nlabel:   %s\nversion: %s\n' "$PKG" "$LABEL" "$VERSION"

# Second, independent reader from the Android command-line tools.
APK_ANALYZER="$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/cmdline-tools" -mindepth 2 -maxdepth 3 -name apkanalyzer 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$APK_ANALYZER" ]; then
    if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/.mozbuild/jdk" ]; then
        JAVA_BIN="$(find "$HOME/.mozbuild/jdk" -mindepth 3 -maxdepth 3 -type f -path '*/bin/java' | sort | tail -1)"
        if [ -n "$JAVA_BIN" ]; then
            JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
            export JAVA_HOME
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
    fi
    ANALYZER_ID="$(timeout 30 "$APK_ANALYZER" manifest application-id "$APK")"
    printf 'apkanalyzer: %s\n' "$ANALYZER_ID"
    [ "$ANALYZER_ID" = "$EXPECTED_PACKAGE" ] || { echo "FAIL apkanalyzer package: '$ANALYZER_ID'" >&2; fail=1; }
fi

[ "$fail" -eq 0 ] || exit 1
echo "APK metadata OK"
