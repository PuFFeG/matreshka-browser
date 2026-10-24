#!/usr/bin/env bash
# Apply the Matreshka core patch and Fenix overlay to a Firefox source tree.
# Usage: FIREFOX_SRC=/path/to/firefox ./scripts/apply.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${1:-${FIREFOX_SRC:-}}"
[ -n "$SRC" ] || { echo "error: pass the Firefox source directory or set FIREFOX_SRC" >&2; exit 2; }
[ -d "$SRC/mobile/android/fenix/app" ] || { echo "error: not a Firefox/Fenix tree: $SRC" >&2; exit 1; }
PATCH="$ROOT/patches/core/matreshka-trust.patch"

echo "==> Applying core patch"
if command -v git >/dev/null 2>&1; then
    if (cd "$SRC" && git apply --check "$PATCH" 2>/dev/null); then
        (cd "$SRC" && git apply "$PATCH")
    elif (cd "$SRC" && git apply --reverse --check "$PATCH" 2>/dev/null); then
        echo "core patch already applied"
    else
        echo "error: core patch does not apply cleanly; rebase it first" >&2
        exit 1
    fi
elif (cd "$SRC" && patch -p1 --dry-run --forward < "$PATCH" >/dev/null 2>&1); then
    (cd "$SRC" && patch -p1 --forward < "$PATCH")
elif (cd "$SRC" && patch -p1 --dry-run --reverse < "$PATCH" >/dev/null 2>&1); then
    echo "core patch already applied"
else
    echo "error: core patch does not apply cleanly; rebase it first" >&2
    exit 1
fi

if "$ROOT/scripts/apply-fenix-patches.sh" --check "$SRC"; then
    echo "Fenix patches already applied; skipping overlay"
else
    echo "==> Applying Fenix overlay"
    "$ROOT/fenix/apply.sh" "$SRC/mobile/android/fenix"
    "$ROOT/scripts/apply-fenix-patches.sh" "$SRC"
fi
