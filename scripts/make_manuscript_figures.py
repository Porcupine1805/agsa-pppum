#!/usr/bin/env python3
"""Publication figures for the JDSA manuscript, from frozen paper-results CSVs.

Writes vector PDFs into figures/ (Fig1–Fig3 and the ESM result figures).
"""
from __future__ import annotations

import csv
import math
import shutil
import statistics
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.colors import LinearSegmentedColormap
from matplotlib.lines import Line2D
from matplotlib.patches import Patch, Rectangle
from matplotlib.ticker import LogLocator, MaxNLocator

ROOT = Path(__file__).resolve().parents[1]
PAPER_CSV = ROOT / "results" / "paper-results" / "ready_paper_runs.csv"
SCALE_CSV = ROOT / "results" / "paper-results" / "ready_scaling_runs.csv"
MS = ROOT / "figures"
ARCHIVE = MS

ALGS = ["SMAU-PHUI", "SMSE-PHUI", "MU-MAP", "AGSA", "MPH"]
ALG_LABEL = {
    "SMAU-PHUI": "SMAU",
    "SMSE-PHUI": "SMSE",
    "MU-MAP": "MU-MAP",
    "AGSA": "AGSA",
    "MPH": "MPH",
}
# Okabe–Ito, colour-blind safe; AGSA (proposed) is the strongest blue.
ALG_COLOR = {
    "SMAU-PHUI": "#E69F00",
    "SMSE-PHUI": "#56B4E9",
    "MU-MAP": "#009E73",
    "AGSA": "#0072B2",
    "MPH": "#D55E00",
}
ALG_HATCH = {
    "SMAU-PHUI": "///",
    "SMSE-PHUI": "xxx",
    "MU-MAP": "...",
    "AGSA": "",
    "MPH": r"\\",
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
    "ecommerce_real": "E-comm.",
    "fruithut_real": "Fruithut",
    "yoochoose_real": "Yoochoose",
    "chainstore_real": "Chainstore",
}
THETAG_DS = [d for d in DS_ORDER if d != "mushroom"]
SCALE_DS = ["fruithut_real", "yoochoose_real", "chainstore_real"]
SCALE_N = {
    "fruithut_real": 181970,
    "yoochoose_real": 234300,
    "chainstore_real": 1112949,
}
SCALE_ALGS = ["SMAU-PHUI", "AGSA", "MPH"]
SCALE_MARK = {
    "fruithut_real": "o",
    "yoochoose_real": "s",
    "chainstore_real": "D",
}
SCALE_LS = {
    "fruithut_real": "-",
    "yoochoose_real": "--",
    "chainstore_real": ":",
}

AC_CMAP = LinearSegmentedColormap.from_list(
    "ac",
    ["#ffffff", "#fee8c8", "#fdbb84", "#e34a33", "#b30000"],
    N=256,
)


def load_ok(path: Path) -> list[dict]:
    with path.open(encoding="utf-8", newline="") as f:
        return [r for r in csv.DictReader(f) if r["status"] == "OK"]


def values(rows, dataset, alg, min_per, metric, scale=1.0) -> list[float]:
    out = []
    for r in rows:
        if r["dataset"] != dataset or r["algorithm"] != alg:
            continue
        if int(float(r["min_per"])) != min_per:
            continue
        out.append(float(r[metric]) * scale)
    return out


def mean_sd(xs: list[float]) -> tuple[float, float]:
    if not xs:
        return math.nan, 0.0
    if len(xs) == 1:
        return xs[0], 0.0
    return statistics.fmean(xs), statistics.stdev(xs)


def setup_style() -> None:
    plt.rcParams.update(
        {
            "font.family": "serif",
            "font.serif": ["STIX Two Text", "Times New Roman", "DejaVu Serif"],
            "mathtext.fontset": "stix",
            "font.size": 8.5,
            "axes.labelsize": 9,
            "axes.titlesize": 9,
            "legend.fontsize": 8,
            "xtick.labelsize": 8,
            "ytick.labelsize": 8,
            "axes.linewidth": 0.7,
            "xtick.major.width": 0.6,
            "ytick.major.width": 0.6,
            "xtick.major.size": 3.0,
            "ytick.major.size": 3.0,
            "pdf.fonttype": 42,
            "ps.fonttype": 42,
            "hatch.linewidth": 0.35,
            "figure.dpi": 140,
            "savefig.bbox": "tight",
            "savefig.pad_inches": 0.03,
            "axes.unicode_minus": False,
        }
    )


def despine(ax) -> None:
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.spines["left"].set_linewidth(0.7)
    ax.spines["bottom"].set_linewidth(0.7)


def legend_handles() -> list[Patch]:
    handles = []
    for alg in ALGS:
        handles.append(
            Patch(
                facecolor=ALG_COLOR[alg],
                edgecolor="#222222",
                linewidth=0.5,
                hatch=ALG_HATCH[alg],
                label=ALG_LABEL[alg],
            )
        )
    return handles


def grouped_bars(
    ax,
    rows,
    datasets,
    min_per,
    metric,
    ylabel,
    scale=1.0,
    logy=False,
):
    n_ds = len(datasets)
    n_alg = len(ALGS)
    group_w = 0.84
    width = group_w / n_alg
    centers = np.arange(n_ds, dtype=float)

    for j, alg in enumerate(ALGS):
        offs = -group_w / 2 + width / 2 + j * width
        xs, ys, lows, highs = [], [], [], []
        for i, ds in enumerate(datasets):
            xs.append(centers[i] + offs)
            vals = values(rows, ds, alg, min_per, metric, scale)
            m, s = mean_sd(vals)
            y = 0.0 if math.isnan(m) else m
            ys.append(y)
            if y <= 0:
                lows.append(0.0)
                highs.append(0.0)
            elif logy:
                # Symmetric ±SD would cross zero when SD > mean; drop the lower whisker.
                lows.append(0.0 if y - s <= 0 else s)
                highs.append(s)
            else:
                lows.append(min(s, y))
                highs.append(s)
        ax.bar(
            xs,
            ys,
            width * 0.92,
            yerr=np.vstack([lows, highs]),
            capsize=1.5,
            error_kw={"linewidth": 0.6, "ecolor": "#333333", "capthick": 0.6, "zorder": 4},
            facecolor=ALG_COLOR[alg],
            edgecolor="#222222",
            linewidth=0.45,
            hatch=ALG_HATCH[alg],
            zorder=3,
        )

    ax.set_xticks(centers, [DS_LABEL[d] for d in datasets], rotation=28, ha="right")
    ax.set_ylabel(ylabel)
    ax.set_axisbelow(True)
    ax.grid(axis="y", linestyle="-", linewidth=0.4, color="0.85", zorder=0)
    despine(ax)
    if logy:
        ax.set_yscale("log")
        ax.yaxis.set_minor_locator(LogLocator(base=10, subs=np.arange(2, 10) * 0.1))
        ax.grid(axis="y", which="minor", linestyle=":", linewidth=0.3, color="0.9", zorder=0)
    else:
        ax.set_ylim(bottom=0)
        ax.yaxis.set_major_locator(MaxNLocator(nbins=5, min_n_ticks=4))
    ax.set_xlim(-0.62, n_ds - 0.38)


def save(fig, stem: str, also: list[str] | None = None) -> None:
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    targets = [MS / f"{stem}.pdf", ARCHIVE / f"{stem}.pdf"]
    if also:
        targets.extend(MS / name for name in also)
        targets.extend(ARCHIVE / name for name in also)
    # unique while preserving order
    seen = set()
    uniq = []
    for t in targets:
        if t in seen:
            continue
        seen.add(t)
        uniq.append(t)
    primary = uniq[0]
    fig.savefig(primary)
    plt.close(fig)
    for t in uniq[1:]:
        shutil.copy2(primary, t)
    print("wrote", primary.name, "->", ", ".join(p.name for p in uniq))


def fig1_quality(rows) -> None:
    fig, axes = plt.subplots(2, 1, figsize=(5.85, 4.85), sharex=True, layout="constrained")
    grouped_bars(axes[0], rows, DS_ORDER, 1, "utilityLoss_pct", "Utility loss (%)")
    grouped_bars(axes[1], rows, DS_ORDER, 1, "MC", "Missing cost")
    axes[0].set_title("(a)  Utility loss", loc="left", fontsize=10, pad=4)
    axes[1].set_title("(b)  Missing cost", loc="left", fontsize=10, pad=4)
    fig.legend(
        handles=legend_handles(),
        ncol=5,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.04),
        frameon=False,
        borderaxespad=0.0,
        columnspacing=1.1,
        handlelength=1.35,
        handleheight=0.85,
    )
    save(fig, "Fig1", also=["fig-theta-quality.pdf"])


def fig2_runtime(rows) -> None:
    fig, ax = plt.subplots(figsize=(5.85, 2.70), layout="constrained")
    grouped_bars(
        ax,
        rows,
        DS_ORDER,
        1,
        "algorithmWall_ms",
        "Wall-clock (s)",
        scale=0.001,
        logy=True,
    )
    ax.set_ylim(0.12, 2500)
    fig.legend(
        handles=legend_handles(),
        ncol=5,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.08),
        frameon=False,
        borderaxespad=0.0,
        columnspacing=1.1,
        handlelength=1.35,
        handleheight=0.85,
    )
    save(fig, "Fig2", also=["fig-theta-time.pdf"])


def fig3_ac_heatmap(rows) -> None:
    fig, axes = plt.subplots(2, 1, figsize=(5.85, 5.05), layout="constrained")
    vmax = 5.0
    for ax, min_per, letter in ((axes[0], 2, "a"), (axes[1], 3, "b")):
        grid = np.full((len(THETAG_DS), len(ALGS)), np.nan)
        for i, ds in enumerate(THETAG_DS):
            for j, alg in enumerate(ALGS):
                m, _ = mean_sd(values(rows, ds, alg, min_per, "AC"))
                grid[i, j] = m
        im = ax.imshow(grid, cmap=AC_CMAP, vmin=0.0, vmax=vmax, aspect="auto")
        ax.set_xticks(range(len(ALGS)), [ALG_LABEL[a] for a in ALGS])
        ax.set_yticks(range(len(THETAG_DS)), [DS_LABEL[d] for d in THETAG_DS])
        ax.tick_params(length=0)
        ax.set_xticks(np.arange(-0.5, len(ALGS), 1), minor=True)
        ax.set_yticks(np.arange(-0.5, len(THETAG_DS), 1), minor=True)
        ax.grid(which="minor", color="white", linestyle="-", linewidth=1.1)
        ax.tick_params(which="minor", bottom=False, left=False)
        agsa_j = ALGS.index("AGSA")
        ax.add_patch(
            Rectangle(
                (agsa_j - 0.5, -0.5),
                1.0,
                len(THETAG_DS),
                fill=False,
                edgecolor=ALG_COLOR["AGSA"],
                linewidth=1.6,
                zorder=5,
            )
        )
        for i in range(len(THETAG_DS)):
            for j in range(len(ALGS)):
                val = grid[i, j]
                if math.isnan(val):
                    txt, color, weight = "—", "#777777", "regular"
                elif val == 0.0:
                    txt = "0"
                    color = ALG_COLOR["AGSA"] if ALGS[j] == "AGSA" else "#666666"
                    weight = "bold" if ALGS[j] == "AGSA" else "regular"
                else:
                    txt = f"{val:.2f}"
                    color = "white" if val >= 2.4 else "#1a1a1a"
                    weight = "regular"
                ax.text(j, i, txt, ha="center", va="center", fontsize=8, color=color, fontweight=weight)
        ax.set_title(
            rf"({letter})  $\mathrm{{minPer}}={min_per}$",
            loc="left",
            pad=6,
            fontsize=10,
        )
        for spine in ax.spines.values():
            spine.set_linewidth(0.6)
            spine.set_color("#444444")
    cbar = fig.colorbar(im, ax=axes, fraction=0.035, pad=0.03)
    cbar.set_label("Mean artificial cost", fontsize=8.5)
    cbar.ax.tick_params(labelsize=8, length=2.5, width=0.6)
    cbar.outline.set_linewidth(0.6)
    save(fig, "Fig3", also=["fig-thetag-ac.pdf"])


def fig_universe(rows) -> None:
    h_count, p_count = {}, {}
    for r in rows:
        if int(float(r["min_per"])) != 1:
            continue
        ds = r["dataset"]
        if ds in h_count:
            continue
        h_count[ds] = float(r["original_HUIs"])
        p_count[ds] = float(r["original_PHUIs"])
    fig, ax = plt.subplots(figsize=(5.85, 2.55), layout="constrained")
    xs = np.arange(len(DS_ORDER), dtype=float)
    width = 0.36
    ax.bar(
        xs - width / 2,
        [h_count[d] for d in DS_ORDER],
        width,
        label=r"$|\mathcal{H}|$ (AGSA universe)",
        facecolor="#0072B2",
        edgecolor="#222222",
        linewidth=0.45,
        zorder=3,
    )
    ax.bar(
        xs + width / 2,
        [p_count[d] for d in DS_ORDER],
        width,
        label=r"$|\mathcal{P}|$ (MPH universe)",
        facecolor="#D55E00",
        edgecolor="#222222",
        linewidth=0.45,
        hatch="///",
        zorder=3,
    )
    ax.set_xticks(xs, [DS_LABEL[d] for d in DS_ORDER], rotation=28, ha="right")
    ax.set_ylabel("Itemset count")
    ax.set_yscale("log")
    ax.set_axisbelow(True)
    ax.grid(axis="y", linestyle="-", linewidth=0.4, color="0.85", zorder=0)
    ax.yaxis.set_minor_locator(LogLocator(base=10, subs=np.arange(2, 10) * 0.1))
    ax.grid(axis="y", which="minor", linestyle=":", linewidth=0.3, color="0.9", zorder=0)
    despine(ax)
    ax.legend(frameon=False, loc="upper left", handlelength=1.3, borderaxespad=0.2)
    save(fig, "fig-universe")


def fig_thetag_quality(rows) -> None:
    fig, axes = plt.subplots(2, 1, figsize=(5.85, 4.85), sharex=True, layout="constrained")
    grouped_bars(axes[0], rows, THETAG_DS, 2, "utilityLoss_pct", "Utility loss (%)")
    grouped_bars(axes[1], rows, THETAG_DS, 3, "utilityLoss_pct", "Utility loss (%)")
    axes[0].set_title(r"(a)  $\mathrm{minPer}=2$", loc="left", fontsize=10, pad=4)
    axes[1].set_title(r"(b)  $\mathrm{minPer}=3$", loc="left", fontsize=10, pad=4)
    fig.legend(
        handles=legend_handles(),
        ncol=5,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.04),
        frameon=False,
        borderaxespad=0.0,
        columnspacing=1.1,
        handlelength=1.35,
        handleheight=0.85,
    )
    save(fig, "fig-thetag-quality")


def fig_scale(rows) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(5.85, 2.85), layout="constrained")
    for ds in SCALE_DS:
        for alg in SCALE_ALGS:
            pts = []
            for r in rows:
                if r["dataset"] != ds or r["algorithm"] != alg or r["status"] != "OK":
                    continue
                pct = 100.0 * int(r["tx_count"]) / SCALE_N[ds]
                pts.append((pct, float(r["algorithmWall_ms"]) / 1000.0, float(r["utilityLoss_pct"])))
            pts.sort()
            if not pts:
                continue
            xs = [p[0] for p in pts]
            style = dict(
                color=ALG_COLOR[alg],
                linestyle=SCALE_LS[ds],
                marker=SCALE_MARK[ds],
                linewidth=1.35,
                markersize=5.2,
                markerfacecolor=ALG_COLOR[alg],
                markeredgecolor="#222222",
                markeredgewidth=0.35,
            )
            axes[0].plot(xs, [p[1] for p in pts], **style)
            axes[1].plot(xs, [p[2] for p in pts], **style)
    axes[0].set_ylabel("Wall-clock (s)")
    axes[1].set_ylabel("Utility loss (%)")
    for ax, letter, title in ((axes[0], "a", "Runtime"), (axes[1], "b", "Utility loss")):
        ax.set_title(f"({letter})  {title}", loc="left", fontsize=10, pad=4)
        ax.set_xlabel("Prefix of $n$ (%)")
        ax.set_xticks([10, 25, 50, 100])
        ax.set_xlim(5, 108)
        ax.set_ylim(bottom=0)
        ax.set_axisbelow(True)
        ax.grid(linestyle="-", linewidth=0.4, color="0.88", zorder=0)
        despine(ax)
    ds_h = [
        Line2D(
            [0],
            [0],
            color="#333333",
            linewidth=1.2,
            linestyle=SCALE_LS[d],
            marker=SCALE_MARK[d],
            markersize=5,
            markerfacecolor="white",
            markeredgecolor="#333333",
            label=DS_LABEL[d],
        )
        for d in SCALE_DS
    ]
    alg_h = [
        Line2D([0], [0], color=ALG_COLOR[a], linewidth=2.2, label=ALG_LABEL[a])
        for a in SCALE_ALGS
    ]
    fig.legend(
        handles=ds_h + alg_h,
        ncol=6,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.08),
        frameon=False,
        fontsize=8,
        handlelength=1.7,
        columnspacing=1.0,
        borderaxespad=0.0,
    )
    save(fig, "fig-scale")


def main() -> None:
    setup_style()
    if not PAPER_CSV.is_file():
        raise SystemExit(f"missing {PAPER_CSV}")
    if not SCALE_CSV.is_file():
        raise SystemExit(f"missing {SCALE_CSV}")
    paper = load_ok(PAPER_CSV)
    scale = list(csv.DictReader(SCALE_CSV.open(encoding="utf-8", newline="")))
    fig1_quality(paper)
    fig2_runtime(paper)
    fig3_ac_heatmap(paper)
    fig_universe(paper)
    fig_thetag_quality(paper)
    fig_scale(scale)
    print("source", PAPER_CSV.name, "rows", len(paper))


if __name__ == "__main__":
    main()
