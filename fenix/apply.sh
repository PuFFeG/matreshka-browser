#!/usr/bin/env bash
# Copy the Matreshka Fenix integration over a Firefox/Fenix source tree.
# Usage: ./fenix/apply.sh /path/to/firefox/mobile/android/fenix
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FENIX_DIR="${1:-${FENIX_DIR:-}}"
if [ -z "$FENIX_DIR" ]; then
    echo "error: pass the Fenix directory, e.g. /path/to/firefox/mobile/android/fenix" >&2
    exit 2
fi
[ -d "$FENIX_DIR/app" ] || { echo "error: Fenix tree not found: $FENIX_DIR" >&2; exit 1; }
cd "$HERE/files"
find . -type f -print0 | while IFS= read -r -d '' f; do
    mkdir -p "$FENIX_DIR/$(dirname "$f")"
    cp "$f" "$FENIX_DIR/$f"
done
echo "Applied Matreshka Fenix files to $FENIX_DIR"
