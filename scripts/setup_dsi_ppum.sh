#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-$ROOT/external/DSI-Lab1-PPUM}"
if [ -d "$DEST/.git" ]; then
  echo "Already cloned: $DEST"
else
  git clone https://github.com/DSI-Lab1/PPUM.git "$DEST"
fi
printf '\nSet this before running real experiments:\nexport PPUM_ROOT=%q\n' "$DEST"
