#!/usr/bin/env python3
"""Friedman / Wilcoxon tables for the Q2 package. Run after the four waves merge."""
from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT.parent / "data"
PAPER = DATA / "paper-results" / "ready_paper_runs.csv"
Q2 = DATA / "q2"


def load(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def ok_rows(rows: list[dict]) -> list[dict]:
    return [r for r in rows if r.get("status") == "OK"]


def mean_by(rows: list[dict], keys: tuple[str, ...], metric: str) -> dict:
    buckets: dict[tuple, list[float]] = defaultdict(list)
    for r in rows:
        k = tuple(r[x] for x in keys)
        buckets[k].append(float(r[metric]))
    return {k: sum(v) / len(v) for k, v in buckets.items()}


def main() -> None:
    paper = ok_rows(load(PAPER))
    mumip = ok_rows(load(Q2 / "q2_mumip_runs.csv"))
    liu = ok_rows(load(Q2 / "q2_liu_ref_runs.csv"))
    rto = ok_rows(load(Q2 / "q2_retail_to_runs.csv"))
    s10 = ok_rows(load(Q2 / "q2_seeds10_small_runs.csv"))
    print(f"paper_ok={len(paper)} mumip_ok={len(mumip)} liu_ok={len(liu)} retail_to_ok={len(rto)} seeds10_ok={len(s10)}")
    if not paper:
        raise SystemExit("paper CSV missing")
    try:
        from scipy.stats import friedmanchisquare, wilcoxon
    except ImportError as e:
        raise SystemExit(f"scipy is required: {e}") from e

    # Dataset-level Friedman on paper mean loss (3 seeds) for the five paper algorithms.
    algs = ["SMAU-PHUI", "SMSE-PHUI", "MU-MAP", "AGSA", "MPH"]
    loss = mean_by(paper, ("dataset", "algorithm"), "utilityLoss_pct")
    datasets = sorted({r["dataset"] for r in paper})
    series = []
    for alg in algs:
        series.append([loss.get((d, alg), float("nan")) for d in datasets])
    if all(len(s) == len(datasets) and all(x == x for x in s) for s in series):
        stat, p = friedmanchisquare(*series)
        print(f"friedman_loss n_dataset={len(datasets)} stat={stat:.4f} p={p:.4g}")
        try:
            import math
            from scipy.stats import rankdata

            ranks = [rankdata(row, method="average") for row in zip(*series)]
            avg = [sum(r[i] for r in ranks) / len(ranks) for i in range(len(algs))]
            k, n = len(algs), len(datasets)
            cd = 2.728 * math.sqrt(k * (k + 1) / (6.0 * n))
            print("avg_ranks_loss", list(zip(algs, [round(x, 4) for x in avg])))
            print(f"nemenyi_cd_0.05={cd:.4f}")
            for i, ai in enumerate(algs):
                for j, aj in enumerate(algs):
                    if j <= i:
                        continue
                    d = abs(avg[i] - avg[j])
                    print(f"  {ai} vs {aj}: delta={d:.3f} sig={d > cd}")
        except Exception as e:
            print(f"nemenyi skipped: {e}")
    else:
        print("friedman_loss skipped (missing algorithm/dataset cell)")

    if s10:
        pairs = defaultdict(dict)
        for r in s10:
            key = (r["dataset"], r["min_per"], r["seed"])
            pairs[key][r["algorithm"]] = float(r["utilityLoss_pct"])
        a, b = [], []
        for vals in pairs.values():
            if "AGSA" in vals and "MPH" in vals:
                a.append(vals["AGSA"])
                b.append(vals["MPH"])
        if len(a) >= 6:
            w = wilcoxon(a, b, zero_method="wilcox", alternative="two-sided")
            print(f"wilcoxon AGSA vs MPH loss n={len(a)} stat={w.statistic:.4f} p={w.pvalue:.4g}")


if __name__ == "__main__":
    main()
