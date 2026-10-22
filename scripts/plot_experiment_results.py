#!/usr/bin/env python3
"""Aggregate result CSV and create publication-ready PDF/PNG comparisons."""
from __future__ import annotations

import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

METRICS = {
    "algorithmWall_ms": ("Runtime (s)", 0.001),
    "utilityLoss_pct": ("Utility loss (%)", 1.0),
    "MC_pct": ("Missing cost (%)", 1.0),
    "AC_pct": ("Artificial cost (%)", 1.0),
    "modifiedTx_pct": ("Modified transactions (%)", 1.0),
}


def mean_sd(values):
    return statistics.fmean(values), statistics.stdev(values) if len(values) > 1 else 0.0


def tex(value):
    return str(value).replace("\\", r"\textbackslash{}").replace("_", r"\_").replace("%", r"\%")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("--out", default="results/figures")
    args = parser.parse_args()
    try:
        import matplotlib.pyplot as plt
    except ImportError as exc:
        raise SystemExit("Install plotting dependency: python -m pip install -r requirements-analysis.txt") from exc

    inp, out = Path(args.input), Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    with inp.open(encoding="utf-8", newline="") as f:
        rows = [r for r in csv.DictReader(f) if r.get("status") == "OK"]
    if not rows:
        raise SystemExit("No status=OK rows")

    grouped = defaultdict(list)
    for r in rows:
        grouped[(r["dataset"], int(r["tx_count"]), int(r["min_per"]), r["algorithm"])].append(r)
    summary_fields = ["dataset", "tx_count", "min_per", "algorithm", "runs"]
    for metric in METRICS:
        summary_fields += [f"{metric}_mean", f"{metric}_sd"]
    summary = []
    for key in sorted(grouped):
        rs = grouped[key]
        record = {"dataset": key[0], "tx_count": key[1], "min_per": key[2], "algorithm": key[3], "runs": len(rs)}
        for metric in METRICS:
            avg, sd = mean_sd([float(r[metric]) for r in rs])
            record[f"{metric}_mean"], record[f"{metric}_sd"] = avg, sd
        summary.append(record)
    with (out / "summary.csv").open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, summary_fields, lineterminator="\n")
        writer.writeheader(); writer.writerows(summary)

    algorithms = sorted({r["algorithm"] for r in rows})
    labels = sorted({(r["dataset"], int(r["tx_count"]), int(r["min_per"])) for r in rows})
    width = 0.8 / max(1, len(algorithms))
    for metric, (ylabel, scale) in METRICS.items():
        fig, ax = plt.subplots(figsize=(max(8, len(labels) * 0.65), 4.8), constrained_layout=True)
        lookup = {(r["dataset"], int(r["tx_count"]), int(r["min_per"]), r["algorithm"]): r for r in summary}
        for ai, algorithm in enumerate(algorithms):
            xs, means, errors = [], [], []
            for i, label in enumerate(labels):
                rec = lookup.get((*label, algorithm))
                xs.append(i - 0.4 + width / 2 + ai * width)
                means.append(float(rec[f"{metric}_mean"]) * scale if rec else math.nan)
                errors.append(float(rec[f"{metric}_sd"]) * scale if rec else 0)
            ax.bar(xs, means, width, yerr=errors, capsize=2, label=algorithm)
        ax.set_ylabel(ylabel)
        ax.set_xticks(range(len(labels)), [f"{d}\nn={n}, p={p}" for d, n, p in labels], rotation=35, ha="right")
        ax.grid(axis="y", alpha=0.25)
        ax.legend(ncol=min(5, len(algorithms)), fontsize=8)
        fig.savefig(out / f"{metric}.pdf")
        fig.savefig(out / f"{metric}.png", dpi=300)
        plt.close(fig)

    # Compact LaTeX table of the primary correctness/utility metrics.
    with (out / "summary_table.tex").open("w", encoding="utf-8", newline="\n") as f:
        f.write("\\begin{tabular}{llrrrr}\n\\toprule\n")
        f.write("Dataset / $p_{min}$ & Algorithm & Time (s) & Loss (\\%) & MC (\\%) & AC (\\%) \\\\\n\\midrule\n")
        for r in summary:
            f.write(f"{tex(r['dataset'])} ($n={r['tx_count']}$) / {r['min_per']} & {tex(r['algorithm'])} & "
                    f"{float(r['algorithmWall_ms_mean'])/1000:.2f} & {float(r['utilityLoss_pct_mean']):.3f} & "
                    f"{float(r['MC_pct_mean']):.3f} & {float(r['AC_pct_mean']):.3f} \\\\\n")
        f.write("\\bottomrule\n\\end{tabular}\n")
    print(f"[done] summary and {len(METRICS)} PDF/PNG figures in {out}")


if __name__ == "__main__":
    main()
