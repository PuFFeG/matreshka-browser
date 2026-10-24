#!/usr/bin/env bash
# Apply Matreshka's ordered Fenix patches after fenix/apply.sh has laid down the overlay.
# Usage: FIREFOX_SRC=/path/to/firefox ./scripts/apply-fenix-patches.sh [source-dir]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK=0
if [ "${1:-}" = "--check" ]; then
    CHECK=1
    shift
fi
SRC="${1:-${FIREFOX_SRC:-}}"
[ -n "$SRC" ] || { echo "error: pass the Firefox source directory or set FIREFOX_SRC" >&2; exit 2; }
[ -d "$SRC/mobile/android/fenix/app" ] || { echo "error: not a Firefox/Fenix tree: $SRC" >&2; exit 1; }
SERIES="$ROOT/patches/fenix/series"
[ -f "$SERIES" ] || { echo "error: missing $SERIES" >&2; exit 1; }

check_patch() {
    local patch_file="$1"
    if command -v git >/dev/null 2>&1; then
        (cd "$SRC" && git apply --reverse --check "$patch_file" 2>/dev/null)
    else
        (cd "$SRC" && patch -p1 --dry-run --reverse --fuzz=0 < "$patch_file" >/dev/null 2>&1)
    fi
}

if [ "$CHECK" -eq 1 ]; then
    last=""
    while IFS= read -r name; do
        case "$name" in ''|'#'*) continue ;; esac
        last="$name"
    done < "$SERIES"
    [ -n "$last" ] || { echo "error: empty Fenix patch series" >&2; exit 2; }

    # Sequential patches overlap, so the last patch is the reliable applied marker.
    # A clean tree (or a partial one) deliberately returns 1; the caller then runs the
    # overlay and the ordered series from the start.
    check_patch "$ROOT/patches/fenix/$last" && exit 0
    exit 1
fi

while IFS= read -r name; do
    case "$name" in ''|'#'*) continue ;; esac
    patch_file="$ROOT/patches/fenix/$name"
    [ -f "$patch_file" ] || { echo "error: missing patch $patch_file" >&2; exit 1; }

    if command -v git >/dev/null 2>&1; then
        if (cd "$SRC" && git apply --check "$patch_file"); then
            (cd "$SRC" && git apply "$patch_file")
            echo "applied $name"
        elif (cd "$SRC" && git apply --reverse --check "$patch_file" 2>/dev/null); then
            echo "already applied $name"
        else
            echo "error: $name does not apply cleanly; rebase it first" >&2
            exit 1
        fi
    elif (cd "$SRC" && patch -p1 --dry-run --forward --fuzz=0 < "$patch_file" >/dev/null 2>&1); then
        (cd "$SRC" && patch -p1 --forward --fuzz=0 < "$patch_file")
        echo "applied $name"
    elif (cd "$SRC" && patch -p1 --dry-run --reverse --fuzz=0 < "$patch_file" >/dev/null 2>&1); then
        echo "already applied $name"
    else
        echo "error: $name does not apply cleanly; rebase it first" >&2
        exit 1
    fi
done < "$SERIES"
