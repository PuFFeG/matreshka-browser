#!/usr/bin/env bash
# Zipalign and sign an APK with the Matreshka release key.
# Required: MATRESHKA_KEYSTORE, MATRESHKA_KEYSTORE_PASSWORD_FILE
# Optional: MATRESHKA_KEY_ALIAS (default: matreshka), ANDROID_BUILD_TOOLS
set -euo pipefail
IN="${1:?usage: sign-apk.sh INPUT.apk OUTPUT.apk}"
OUT="${2:?usage: sign-apk.sh INPUT.apk OUTPUT.apk}"
: "${MATRESHKA_KEYSTORE:?set MATRESHKA_KEYSTORE to the .jks path}"
: "${MATRESHKA_KEYSTORE_PASSWORD_FILE:?set MATRESHKA_KEYSTORE_PASSWORD_FILE}"
ALIAS="${MATRESHKA_KEY_ALIAS:-matreshka}"

if [ -n "${ANDROID_BUILD_TOOLS:-}" ]; then
    BT="$ANDROID_BUILD_TOOLS"
else
    BT="$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
fi
command -v java >/dev/null || export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"

PW="$(head -n1 "$MATRESHKA_KEYSTORE_PASSWORD_FILE")"
PASSFILE="$(mktemp)"
ALIGNED="$(mktemp --suffix=.apk)"
trap 'rm -f "$PASSFILE" "$ALIGNED"' EXIT
chmod 600 "$PASSFILE"
printf '%s\n%s\n' "$PW" "$PW" > "$PASSFILE"
unset PW

mkdir -p "$(dirname "$OUT")"
"$BT/zipalign" -p -f 4 "$IN" "$ALIGNED"
"$BT/apksigner" sign --ks "$MATRESHKA_KEYSTORE" --ks-key-alias "$ALIAS" \
    --ks-pass "file:$PASSFILE" --key-pass "file:$PASSFILE" \
    --out "$OUT" "$ALIGNED"
"$BT/apksigner" verify --print-certs "$OUT"
