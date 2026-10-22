#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROFILE="${1:-smoke}"
BUDGET_GB="${BUDGET_GB:-24}"
SOLO_HEAP_GB="${SOLO_HEAP_GB:-16}"
case "$PROFILE" in smoke|paper|scaling) ;; *) echo "profile must be smoke, paper, or scaling" >&2; exit 2;; esac
./scripts/build.sh
java -jar build/agsa-pppum.jar selftest
java -jar build/agsa-pppum.jar env --out results/run_environment.txt
python3 scripts/validate_dataset_layout.py
python3 scripts/generate_ready_experiments.py --profile "$PROFILE"
python3 scripts/run_publication_parallel.py \
  --config "experiments/ready_${PROFILE}.csv" \
  --out "results/ready_${PROFILE}_runs.csv" \
  --budget-gb "$BUDGET_GB" --solo-heap-gb "$SOLO_HEAP_GB"
python3 scripts/validate_results.py "results/ready_${PROFILE}_runs.csv" --allow-infeasible
python3 scripts/plot_experiment_results.py "results/ready_${PROFILE}_runs.csv" --out "results/figures/${PROFILE}"
echo "Complete: results/ready_${PROFILE}_runs.csv and results/figures/${PROFILE}"
