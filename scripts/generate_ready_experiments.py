#!/usr/bin/env python3
"""Generate reproducible smoke, paper, and scalability experiment matrices."""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
READY = ROOT / "datasets" / "ready"
OUT = ROOT / "experiments"

FIELDS = [
    "enabled", "configId", "dataset", "db", "profits", "sensitive", "minUtil",
    "minPer", "maxPer", "minAvg", "maxAvg", "sepPct", "sensitiveCount", "seed",
    "replicate", "prefixTx", "maxLen", "algorithms", "topK", "timeline",
    "lambdaMissing", "lambdaUtility", "lambdaEdit", "saveArtifacts", "forceRemine",
    "threshold_source", "notes",
]

# About 0.1% of database utility for new datasets; established manuscript values
# are retained for the four legacy datasets. These are starting points and must
# be reported, not silently tuned after observing algorithm rankings.
MIN_UTIL = {
    "foodmart": 3_000,
    "retail": 100_000,
    "mushroom": 5_000_000,
    "liquor_11": 500_000,
    "ecommerce_real": 500_000,
    "fruithut_real": 260_000,
    "yoochoose_real": 1_930_000,
    "chainstore_real": 2_610_000,
}


def load_manifest() -> list[dict]:
    path = READY / "manifest.csv"
    if not path.exists():
        raise SystemExit("Run scripts/prepare_all_datasets.py first")
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def row(meta: dict, profile: str, min_per: int, seed: int, replicate: int,
        prefix: int, max_len: int, min_util: int) -> dict:
    name = meta["dataset"]
    n = prefix or int(meta["transactions"])
    max_per = max(10, math.ceil(n / 5))
    max_avg = max(5, math.ceil(n / 10))
    config_id = f"{profile}-{name}-n{n}-p{min_per}-r{replicate}"
    return {
        "enabled": "true", "configId": config_id, "dataset": name,
        "db": f"datasets/ready/{name}/item_count.txt",
        "profits": f"datasets/ready/{name}/item_utility.txt", "sensitive": "",
        "minUtil": min_util, "minPer": min_per, "maxPer": max_per,
        "minAvg": 1, "maxAvg": max_avg, "sepPct": 1, "sensitiveCount": 0,
        "seed": seed, "replicate": replicate, "prefixTx": prefix,
        "maxLen": max_len,
        "algorithms": "SMAU-PHUI|SMSE-PHUI|MU-MAP|AGSA|MPH",
        "topK": 5, "timeline": "fixed", "lambdaMissing": 30,
        "lambdaUtility": 8, "lambdaEdit": 2, "saveArtifacts": "false",
        "forceRemine": "true", "threshold_source": "pre-registered project defaults",
        "notes": f"{profile}; positive utilities; fixed timeline",
    }


def matrices(manifest: list[dict]) -> dict[str, list[dict]]:
    smoke, paper, scaling = [], [], []
    for meta in manifest:
        name = meta["dataset"]
        n = int(meta["transactions"])
        total = int(meta["total_utility"])
        prefix = min(n, 10_000)
        if name == "mushroom":
            prefix = min(n, 1_000)
        smoke_util = max(1, math.ceil(total * (prefix / n) * 0.01))
        # Mushroom is exceptionally dense; the generic 1% threshold makes a
        # smoke check take several minutes in the sanitizers. Use its registered
        # full-data threshold so this profile remains a pipeline check.
        if name == "mushroom":
            smoke_util = math.ceil(MIN_UTIL[name] * prefix / n)
        smoke.append(row(meta, "smoke", 1, 202601, 1, prefix, 2, smoke_util))
        for replicate, seed in enumerate((202601, 202602, 202603), 1):
            for min_per in (1, 2, 3):
                paper.append(row(meta, "paper", min_per, seed, replicate, 0, 4, MIN_UTIL[name]))

    by_name = {m["dataset"]: m for m in manifest}
    for name in ("fruithut_real", "yoochoose_real", "chainstore_real"):
        meta = by_name[name]
        n = int(meta["transactions"])
        total = int(meta["total_utility"])
        for fraction in (0.10, 0.25, 0.50, 1.00):
            prefix = max(1, round(n * fraction))
            min_util = max(1, math.ceil(total * fraction * 0.001))
            scaling.append(row(meta, "scale", 2, 202601, 1, prefix, 3, min_util))
    return {"ready_smoke.csv": smoke, "ready_paper.csv": paper, "ready_scaling.csv": scaling}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=("all", "smoke", "paper", "scaling"), default="all")
    args = parser.parse_args()
    selected = None if args.profile == "all" else f"ready_{args.profile}.csv"
    OUT.mkdir(parents=True, exist_ok=True)
    for filename, rows in matrices(load_manifest()).items():
        if selected and filename != selected:
            continue
        path = OUT / filename
        with path.open("w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, FIELDS, lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
        print(f"[write] {path.relative_to(ROOT)} configs={len(rows)}")


if __name__ == "__main__":
    main()
