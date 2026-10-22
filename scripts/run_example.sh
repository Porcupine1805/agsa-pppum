#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./scripts/build.sh
java -jar build/agsa-pppum.jar selftest
java -jar build/agsa-pppum.jar mine --db data/example_db.txt --profits data/example_profits.txt --minUtil 260 --minPer 1 --maxPer 6 --minAvg 1 --maxAvg 2
java -jar build/agsa-pppum.jar benchmark --db data/example_db.txt --profits data/example_profits.txt --sensitive data/example_sensitive.txt --minUtil 260 --minPer 1 --maxPer 6 --minAvg 1 --maxAvg 2 --out results/example.csv
