#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./scripts/build.sh
java -jar build/agsa-pppum.jar selftest
java -jar build/agsa-pppum.jar directions --out results/three_directions
java -jar build/agsa-pppum.jar compaction --out results/compaction
if [[ -z "${PPUM_ROOT:-}" ]]; then
  echo "Toy checks done. Set PPUM_ROOT and re-run to execute experiments/tdp_paper.csv."
  exit 0
fi
java -jar build/agsa-pppum.jar batch \
  --config experiments/tdp_paper.csv \
  --out results/tdp_paper_runs.csv \
  --continueOnError true
echo "Wrote results/tdp_paper_runs.csv"
