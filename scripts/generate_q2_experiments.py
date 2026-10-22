#!/usr/bin/env python3
"""Write the four local Q2 matrices. Does not touch ready_paper.csv."""
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

PAPER_SEEDS = (202601, 202602, 202603)
EXTRA_SEEDS = (202604, 202605, 202606, 202607, 202608, 202609, 202610)
SMALL = ("foodmart", "liquor_11", "mushroom", "ecommerce_real")
ALL = (
    "foodmart", "liquor_11", "ecommerce_real", "mushroom",
    "retail", "fruithut_real", "yoochoose_real", "chainstore_real",
)


def load_manifest() -> dict[str, dict]:
    path = READY / "manifest.csv"
    if not path.exists():
        raise SystemExit("datasets/ready/manifest.csv is missing")
    with path.open(encoding="utf-8", newline="") as f:
        return {row["dataset"]: row for row in csv.DictReader(f)}


def row(meta: dict, prefix: str, min_per: int, seed: int, replicate: int,
        algorithms: str, notes: str) -> dict:
    name = meta["dataset"]
    n = int(meta["transactions"])
    max_per = max(10, math.ceil(n / 5))
    max_avg = max(5, math.ceil(n / 10))
    return {
        "enabled": "true",
        "configId": f"{prefix}-{name}-n{n}-p{min_per}-r{replicate}",
        "dataset": name,
        "db": f"datasets/ready/{name}/item_count.txt",
        "profits": f"datasets/ready/{name}/item_utility.txt",
        "sensitive": "",
        "minUtil": MIN_UTIL[name],
        "minPer": min_per,
        "maxPer": max_per,
        "minAvg": 1,
        "maxAvg": max_avg,
        "sepPct": 1,
        "sensitiveCount": 0,
        "seed": seed,
        "replicate": replicate,
        "prefixTx": 0,
        "maxLen": 4,
        "algorithms": algorithms,
        "topK": 5,
        "timeline": "fixed",
        "lambdaMissing": 30,
        "lambdaUtility": 8,
        "lambdaEdit": 2,
        "saveArtifacts": "false",
        "forceRemine": "true",
        "threshold_source": "pre-registered project defaults",
        "notes": notes,
    }


def write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"[write] {path.relative_to(ROOT)} configs={len(rows)}")


def matrices(manifest: dict[str, dict]) -> dict[str, list[dict]]:
    mumip, liu_ref, retail_to, seeds10 = [], [], [], []
    for name in ALL:
        meta = manifest[name]
        for replicate, seed in enumerate(PAPER_SEEDS, 1):
            for min_per in (1, 2, 3):
                mumip.append(row(
                    meta, "q2mip", min_per, seed, replicate, "MU-MIP",
                    "q2 wave1; MU-MIP only; same seeds as paper",
                ))
    for name in SMALL:
        meta = manifest[name]
        for replicate, seed in enumerate(PAPER_SEEDS, 1):
            for min_per in (1, 2, 3):
                liu_ref.append(row(
                    meta, "q2ref", min_per, seed, replicate,
                    "SMAU-REF|SMSE-REF",
                    "q2 wave2; Liu HUI-stopping vs paper PHUI-stopping",
                ))
        for extra_i, extra_seed in enumerate(EXTRA_SEEDS, 4):
            for min_per in (1, 2, 3):
                seeds10.append(row(
                    meta, "q2s10", min_per, extra_seed, extra_i,
                    "SMAU-PHUI|SMSE-PHUI|MU-MAP|MU-MIP|MPH|AGSA",
                    "q2 wave4; extra seeds 202604-202610 on small datasets",
                ))
    retail = manifest["retail"]
    n = int(retail["transactions"])
    retail_to.append(row(
        retail, "q2rto", 1, 202601, 1,
        "SMAU-PHUI|SMSE-PHUI|MU-MAP|MU-MIP",
        "q2 wave3a; Retail minPer=1 seed 202601 without AGSA",
    ))
    retail_to[-1]["configId"] = f"q2rto-retail-n{n}-p1-r1-fast"
    mph = row(
        retail, "q2rto", 1, 202601, 1, "MPH",
        "q2 wave3b; Retail minPer=1 seed 202601 MPH only",
    )
    mph["configId"] = f"q2rto-retail-n{n}-p1-r1-mph"
    retail_to.append(mph)
    return {
        "q2_mumip.csv": mumip,
        "q2_liu_ref.csv": liu_ref,
        "q2_retail_to.csv": retail_to,
        "q2_seeds10_small.csv": seeds10,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--profile",
        choices=("all", "mumip", "liu_ref", "retail_to", "seeds10_small"),
        default="all",
    )
    args = parser.parse_args()
    wanted = None if args.profile == "all" else f"q2_{args.profile}.csv"
    for filename, rows in matrices(load_manifest()).items():
        if wanted and filename != wanted:
            continue
        write_csv(OUT / filename, rows)


if __name__ == "__main__":
    main()
