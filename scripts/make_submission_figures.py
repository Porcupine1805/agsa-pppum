#!/usr/bin/env python3
"""Grayscale EPS/PDF figures for the TDP manuscript. Numbers from the paper CSVs."""
from __future__ import annotations

import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.patches import Patch
from matplotlib.ticker import MaxNLocator

ROOT = Path(__file__).resolve().parents[1]
CANDIDATES_PAPER = [ROOT / "paper-results/ready_paper_runs.csv"]
CANDIDATES_SCALE = [ROOT / "paper-results/ready_scaling_runs.csv"]
OUT = ROOT / "results/submission-figures"
OUT.mkdir(parents=True, exist_ok=True)

ALGS = ["SMAU-PHUI", "SMSE-PHUI", "MU-MAP", "AGSA", "MPH"]
ALG_LABEL = {
    "SMAU-PHUI": "SMAU",
    "SMSE-PHUI": "SMSE",
    "MU-MAP": "MU-MAP",
    "AGSA": "AGSA",
    "MPH": "MPH",
}
STYLES = {
    "SMAU-PHUI": dict(facecolor="white", edgecolor="black", hatch="///", linewidth=0.8),
    "SMSE-PHUI": dict(facecolor="0.90", edgecolor="black", hatch="xxx", linewidth=0.8),
    "MU-MAP": dict(facecolor="white", edgecolor="black", hatch="...", linewidth=0.8),
    "AGSA": dict(facecolor="0.30", edgecolor="black", hatch="", linewidth=0.8),
    "MPH": dict(facecolor="0.62", edgecolor="black", hatch="", linewidth=0.8),
}
DS_ORDER = [
    "foodmart",
    "liquor_11",
    "retail",
    "mushroom",
    "ecommerce_real",
    "fruithut_real",
    "yoochoose_real",
    "chainstore_real",
]
DS_LABEL = {
    "foodmart": "Foodmart",
    "liquor_11": "Liquor",
    "retail": "Retail",
    "mushroom": "Mushroom",
    "ecommerce_real": "EComm.",
    "fruithut_real": "Fruithut",
    "yoochoose_real": "Yooch.",
    "chainstore_real": "Chain.",
}
THETAG_DS = [d for d in DS_ORDER if d != "mushroom"]


def first_existing(paths):
    for p in paths:
        if p.is_file():
            return p
    raise FileNotFoundError(paths)


def load(path):
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def mean_sd(xs):
    xs = [float(x) for x in xs]
    if not xs:
        return float("nan"), 0.0
    if len(xs) == 1:
        return xs[0], 0.0
    return statistics.fmean(xs), statistics.stdev(xs)


def paper_ok(rows):
    return [r for r in rows if r["status"] == "OK"]


def grouped(rows, min_per, metric, datasets):
    bucket = defaultdict(list)
    for r in rows:
        if int(float(r["min_per"])) != min_per:
            continue
        if r["dataset"] not in datasets:
            continue
        bucket[(r["dataset"], r["algorithm"])].append(float(r[metric]))
    means, sds = {}, {}
    for ds in datasets:
        for alg in ALGS:
            m, s = mean_sd(bucket.get((ds, alg), []))
            means[(ds, alg)] = m
            sds[(ds, alg)] = s
    return means, sds


def setup_style():
    plt.rcParams.update(
        {
            "font.family": "serif",
            "font.size": 9,
            "axes.labelsize": 9,
            "axes.titlesize": 9,
            "legend.fontsize": 8.5,
            "xtick.labelsize": 8.5,
            "ytick.labelsize": 8.5,
            "axes.linewidth": 0.6,
            "pdf.fonttype": 42,
            "ps.fonttype": 42,
            "hatch.linewidth": 0.45,
            "figure.dpi": 120,
            "savefig.bbox": "tight",
            "savefig.pad_inches": 0.04,
        }
    )


def legend_handles():
    return [
        Patch(label=ALG_LABEL[a], **{k: v for k, v in STYLES[a].items() if k != "linewidth"})
        for a in ALGS
    ]


def grouped_bars(ax, datasets, means, sds, ylabel, logy=False):
    n_ds = len(datasets)
    n_alg = len(ALGS)
    width = 0.78 / n_alg
    xs_center = list(range(n_ds))
    for j, alg in enumerate(ALGS):
        offs = -0.39 + width / 2 + j * width
        x = [c + offs for c in xs_center]
        y = [0.0 if math.isnan(means[(ds, alg)]) else means[(ds, alg)] for ds in datasets]
        e = [0.0 if math.isnan(sds[(ds, alg)]) else sds[(ds, alg)] for ds in datasets]
        ax.bar(x, y, width, yerr=e, capsize=1.4, error_kw={"linewidth": 0.5, "ecolor": "black"}, **STYLES[alg])
    ax.set_xticks(xs_center, [DS_LABEL[d] for d in datasets], rotation=25, ha="right")
    ax.set_ylabel(ylabel)
    ax.set_axisbelow(True)
    ax.grid(axis="y", linestyle=":", linewidth=0.4, color="0.55")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    if logy:
        ax.set_yscale("log")
    else:
        ax.set_ylim(bottom=0)
        ax.yaxis.set_major_locator(MaxNLocator(nbins=5, min_n_ticks=4))


def save(fig, stem):
    fig.savefig(OUT / f"{stem}.pdf")
    plt.close(fig)
    print("wrote", stem)


def fig_theta_quality(rows):
    loss_m, loss_s = grouped(rows, 1, "utilityLoss_pct", DS_ORDER)
    mc_m, mc_s = grouped(rows, 1, "MC", DS_ORDER)
    fig, axes = plt.subplots(2, 1, figsize=(5.0, 4.2), sharex=True)
    grouped_bars(axes[0], DS_ORDER, loss_m, loss_s, "Loss (%)")
    grouped_bars(axes[1], DS_ORDER, mc_m, mc_s, "Missing cost")
    axes[0].set_title(r"(a) Utility loss at $\mathrm{minPer}=1$")
    axes[1].set_title(r"(b) Missing cost at $\mathrm{minPer}=1$")
    fig.legend(handles=legend_handles(), ncol=5, loc="upper center", frameon=False)
    fig.tight_layout(rect=(0, 0, 1, .9))
    save(fig, "fig-theta-quality")


def fig_thetag_ac(rows):
    fig, axes = plt.subplots(2, 1, figsize=(5.0, 4.1), sharex=True)
    for ax, mp, letter in ((axes[0], 2, "a"), (axes[1], 3, "b")):
        m, s = grouped(rows, mp, "AC", THETAG_DS)
        grouped_bars(ax, THETAG_DS, m, s, "Artificial cost")
        ax.set_title(rf"({letter}) Re-mine AC at $\mathrm{{minPer}}={mp}$")
    fig.legend(handles=legend_handles(), ncol=5, loc="upper center", frameon=False)
    fig.tight_layout(rect=(0, 0, 1, .9))
    save(fig, "fig-thetag-ac")


def fig_theta_time(rows):
    m, s = grouped(rows, 1, "algorithmWall_ms", DS_ORDER)
    m = {k: v / 1000.0 for k, v in m.items()}
    s = {k: v / 1000.0 for k, v in s.items()}
    fig, ax = plt.subplots(figsize=(5.0, 2.8))
    grouped_bars(ax, DS_ORDER, m, s, "Wall-clock (s)", logy=True)
    ax.legend(
        handles=legend_handles(),
        ncol=5,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.22),
        frameon=False,
        borderaxespad=0.0,
    )
    fig.tight_layout(rect=(0.0, 0.0, 1.0, 0.88))
    save(fig, "fig-theta-time")


def fig_thetag_quality(rows):
    fig, axes = plt.subplots(2, 1, figsize=(5.0, 4.1), sharex=True)
    for ax, mp, letter in ((axes[0], 2, "a"), (axes[1], 3, "b")):
        m, s = grouped(rows, mp, "utilityLoss_pct", THETAG_DS)
        grouped_bars(ax, THETAG_DS, m, s, "Loss (%)")
        ax.set_title(rf"({letter}) Utility loss at $\mathrm{{minPer}}={mp}$")
    fig.legend(handles=legend_handles(), ncol=5, loc="upper center", frameon=False)
    fig.tight_layout(rect=(0, 0, 1, .9))
    save(fig, "fig-thetag-quality")


def fig_universe(rows):
    h_count, p_count = {}, {}
    for r in rows:
        if int(float(r["min_per"])) != 1:
            continue
        ds = r["dataset"]
        if ds in h_count:
            continue
        h_count[ds] = float(r["original_HUIs"])
        p_count[ds] = float(r["original_PHUIs"])
    fig, ax = plt.subplots(figsize=(5.0, 2.6))
    xs = list(range(len(DS_ORDER)))
    width = 0.36
    ax.bar(
        [c - width / 2 for c in xs],
        [h_count[d] for d in DS_ORDER],
        width,
        label=r"$|\mathcal{H}|$ (AGSA universe)",
        facecolor="0.30",
        edgecolor="black",
        linewidth=0.8,
    )
    ax.bar(
        [c + width / 2 for c in xs],
        [p_count[d] for d in DS_ORDER],
        width,
        label=r"$|\mathcal{P}|$ (MPH universe)",
        facecolor="0.75",
        edgecolor="black",
        hatch="///",
        linewidth=0.8,
    )
    ax.set_xticks(xs, [DS_LABEL[d] for d in DS_ORDER], rotation=25, ha="right")
    ax.set_ylabel("Itemset count")
    ax.set_yscale("log")
    ax.set_axisbelow(True)
    ax.grid(axis="y", linestyle=":", linewidth=0.4, color="0.55")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.legend(
        ncol=2,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.18),
        frameon=False,
        borderaxespad=0.0,
    )
    fig.tight_layout(rect=(0.0, 0.0, 1.0, 0.88))
    save(fig, "fig-universe")


def fig_scale(rows):
    # prefix as percent of full n per dataset
    full_n = {
        "fruithut_real": 181970,
        "yoochoose_real": 234300,
        "chainstore_real": 1112949,
    }
    ds_list = ["fruithut_real", "yoochoose_real", "chainstore_real"]
    line_style = {
        "fruithut_real": dict(linestyle="-", marker="o"),
        "yoochoose_real": dict(linestyle="--", marker="s"),
        "chainstore_real": dict(linestyle=":", marker="^"),
    }
    alg_gray = {"AGSA": "0.10", "MPH": "0.45", "SMAU-PHUI": "0.70"}
    fig, axes = plt.subplots(1, 2, figsize=(5.0, 3.0))
    for ds in ds_list:
        for alg, gray in alg_gray.items():
            pts = []
            for r in rows:
                if r["dataset"] != ds or r["algorithm"] != alg or r["status"] != "OK":
                    continue
                pct = 100.0 * int(r["tx_count"]) / full_n[ds]
                pts.append((pct, float(r["algorithmWall_ms"]) / 1000.0, float(r["utilityLoss_pct"])))
            pts.sort()
            if not pts:
                continue
            xs = [p[0] for p in pts]
            axes[0].plot(xs, [p[1] for p in pts], color=gray, linewidth=1.15, markersize=4.5, **line_style[ds])
            axes[1].plot(xs, [p[2] for p in pts], color=gray, linewidth=1.15, markersize=4.5, **line_style[ds])
    axes[0].set_ylabel("Wall-clock (s)")
    axes[1].set_ylabel("Loss (%)")
    for ax, letter, title in (
        (axes[0], "a", "Runtime vs prefix"),
        (axes[1], "b", "Loss vs prefix"),
    ):
        ax.set_title(f"({letter}) {title}")
        ax.set_xlabel("Prefix of $n$ (%)")
        ax.set_xticks([10, 25, 50, 100])
        ax.set_axisbelow(True)
        ax.grid(linestyle=":", linewidth=0.4, color="0.55")
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        ax.set_xlim(5, 105)
        ax.set_ylim(bottom=0)
    ds_h = [
        plt.Line2D([0], [0], color="0.2", linewidth=1.15, linestyle=line_style[d]["linestyle"], marker=line_style[d]["marker"], markersize=4.5, label=DS_LABEL[d])
        for d in ds_list
    ]
    alg_h = [
        plt.Line2D([0], [0], color=g, linewidth=2.0, label=ALG_LABEL.get(a, a))
        for a, g in alg_gray.items()
    ]
    axes[0].legend(handles=ds_h + alg_h, ncol=2, fontsize=8, frameon=False, loc="upper left")
    fig.tight_layout()
    save(fig, "fig-scale")


def main():
    setup_style()
    paper = paper_ok(load(first_existing(CANDIDATES_PAPER)))
    scale = load(first_existing(CANDIDATES_SCALE))
    fig_theta_quality(paper)
    fig_thetag_ac(paper)
    fig_thetag_quality(paper)
    fig_theta_time(paper)
    fig_universe(paper)
    fig_scale(scale)
    print("out dir", OUT)


if __name__ == "__main__":
    main()
