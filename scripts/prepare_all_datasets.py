#!/usr/bin/env python3
"""Download, convert, validate, and collocate all publication datasets.

SPMF HUIM files store an internal utility for every item occurrence.  The
project uses quantity x external-profit input.  We preserve utilities exactly
by writing quantity=internal-utility and external-profit=1 for each item.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import shutil
import sys
import tempfile
import urllib.request
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
READY = ROOT / "datasets" / "ready"
SPMF_BASE = "https://www.philippe-fournier-viger.com/spmf/publicdatasets/"

SPMF_DATASETS = {
    "ecommerce_real": {
        "url": SPMF_BASE + "ecommerce_utility_no_timestamps.txt",
        "expected_tx": 14975,
        "expected_items": 3468,
        "utility_origin": "real",
        "citation": "SPMF public datasets; UK Online Retail",
    },
    "fruithut_real": {
        "url": SPMF_BASE + "fruithut_utility.txt",
        "expected_tx": 181970,
        "expected_items": 1265,
        "expected_prepared_items": 1263,
        "utility_origin": "real",
        "citation": "SPMF public datasets; Fruithut retail",
    },
    "yoochoose_real": {
        "url": SPMF_BASE + "yoochoose-buys_Utility.dat",
        "expected_tx": 234300,
        "expected_items": 16004,
        "utility_origin": "real",
        "citation": "SPMF public datasets; RecSys 2015 Yoochoose buys",
    },
    "chainstore_real": {
        "url": SPMF_BASE + "chainstore.txt",
        "expected_tx": 1112949,
        "expected_items": 46086,
        "utility_origin": "real",
        "citation": "SPMF public datasets; NU-MineBench Chainstore",
    },
}

QUANTITATIVE = {
    "foodmart": ("foodmart.txt", "foodmart.txt", 4141, 1559, "real"),
    "retail": ("retail.txt", "retail.txt", 88162, 16470, "synthetic"),
    "mushroom": ("mushrooms.txt", "mushrooms.txt", 8416, 119, "synthetic"),
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def download(url: str, dest: Path, force: bool) -> None:
    if dest.exists() and dest.stat().st_size and not force:
        print(f"[reuse] {dest}", flush=True)
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "AGSA-PPPUM-reproducibility/1.0"})
    fd, tmp_name = tempfile.mkstemp(dir=dest.parent)
    os.close(fd)
    tmp_path = Path(tmp_name)
    try:
        print(f"[download] {url}", flush=True)
        with urllib.request.urlopen(request, timeout=120) as response, tmp_path.open("wb") as tmp:
            shutil.copyfileobj(response, tmp, length=1 << 20)
    except Exception:
        tmp_path.unlink(missing_ok=True)
        raise
    tmp_path.replace(dest)


def convert_spmf(source: Path, out_dir: Path) -> dict:
    db = out_dir / "item_count.txt"
    profits = out_dir / "item_utility.txt"
    meta = out_dir / "metadata.json"
    source_hash = sha256(source)
    if db.exists() and profits.exists() and meta.exists():
        old = json.loads(meta.read_text(encoding="utf-8"))
        if old.get("source_sha256") == source_hash and old.get("conversion_version") == 2:
            print(f"[reuse converted] {out_dir.name}", flush=True)
            return old

    out_dir.mkdir(parents=True, exist_ok=True)
    items_seen: set[str] = set()
    tx_count = nonempty = total_utility = cells = 0
    with source.open(encoding="utf-8-sig", errors="strict") as src, db.open("w", encoding="utf-8", newline="\n") as dst:
        for raw in src:
            line = raw.strip()
            if not line or line.startswith(("#", "%", "@")):
                continue
            fields = line.split(":")
            if len(fields) >= 3:
                item_tokens = fields[0].split()
                utility_tokens = fields[2].split()
            elif len(fields) == 2 and source.name == "yoochoose-buys_Utility.dat":
                # The published Yoochoose conversion concatenates the transaction
                # utility to the final nine-digit item id: "... 2148348805857:...".
                item_tokens = fields[0].split()
                utility_tokens = fields[1].split()
                tail = item_tokens[-1]
                if len(tail) <= 9 or not tail.isdigit():
                    raise ValueError(f"Cannot split Yoochoose item/TU token: {tail}")
                published_tu = int(tail[9:])
                item_tokens[-1] = tail[:9]
                if published_tu != sum(map(int, utility_tokens)):
                    raise ValueError(f"Yoochoose transaction utility mismatch at transaction {tx_count + 1}")
            else:
                raise ValueError(f"Bad SPMF HUIM row in {source}: {line[:160]}")
            if len(item_tokens) != len(utility_tokens):
                raise ValueError(f"Item/utility length mismatch at transaction {tx_count + 1}")
            merged: dict[str, int] = defaultdict(int)
            for item, value in zip(item_tokens, utility_tokens):
                utility = int(value)
                if utility < 0:
                    raise ValueError(f"Negative internal utility {utility} for item {item}")
                # The Java model intentionally assumes strictly positive utilities.
                # Zero-utility cells contribute nothing and are projected out.
                if utility == 0:
                    continue
                merged[item] += utility
            dst.write(" ".join(f"{item}:{utility}" for item, utility in merged.items()) + "\n")
            tx_count += 1
            if merged:
                nonempty += 1
            cells += len(merged)
            total_utility += sum(merged.values())
            items_seen.update(merged)
    with profits.open("w", encoding="utf-8", newline="\n") as f:
        for item in sorted(items_seen, key=lambda x: (int(x) if x.lstrip("-").isdigit() else sys.maxsize, x)):
            f.write(f"{item}:1\n")
    result = {
        "transactions": tx_count,
        "nonempty_transactions": nonempty,
        "items": len(items_seen),
        "cells": cells,
        "average_length": cells / tx_count if tx_count else 0,
        "total_utility": total_utility,
        "source_sha256": source_hash,
        "conversion_version": 2,
        "item_count_sha256": sha256(db),
        "item_utility_sha256": sha256(profits),
        "conversion": "positive-utility projection; quantity=SPMF internal utility; external profit=1",
    }
    meta.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def copy_quantitative(ppum_root: Path, name: str, spec: tuple) -> dict:
    count_name, utility_name, expected_tx, expected_items, origin = spec
    base = ppum_root / "pppum_database" / "experiment" / "database" / "beforeDB"
    src_db = base / "item_count" / count_name
    src_profit = base / "item_utility" / utility_name
    if not src_db.exists() or not src_profit.exists():
        raise FileNotFoundError(f"Missing PPUM files for {name} under {base}")
    out = READY / name
    out.mkdir(parents=True, exist_ok=True)
    normalize_database_file(src_db, out / "item_count.txt")
    normalize_profit_file(src_profit, out / "item_utility.txt")
    return inspect_quantitative(out / "item_count.txt", out / "item_utility.txt", expected_tx, expected_items, origin)


def normalize_profit_file(source: Path, destination: Path) -> None:
    """Write the canonical publication form `item:profit`, rejecting duplicates."""
    values: dict[str, int] = {}
    with source.open(encoding="utf-8-sig") as f:
        for raw in f:
            parts = raw.replace(":", " ").replace("=", " ").split()
            if not parts:
                continue
            if len(parts) < 2:
                raise ValueError(f"Bad profit row in {source}: {raw.rstrip()}")
            item, profit = parts[0], int(parts[1])
            if profit <= 0:
                raise ValueError(f"Non-positive profit for {item} in {source}")
            if item in values:
                raise ValueError(f"Duplicate profit for {item} in {source}")
            values[item] = profit
    with destination.open("w", encoding="utf-8", newline="\n") as f:
        for item, profit in values.items():
            f.write(f"{item}:{profit}\n")


def normalize_database_file(source: Path, destination: Path) -> None:
    """Canonicalize each transaction and sum repeated item quantities."""
    with source.open(encoding="utf-8-sig") as src, destination.open("w", encoding="utf-8", newline="\n") as dst:
        for tid, raw in enumerate(src, 1):
            merged: dict[str, int] = {}
            for token in raw.split():
                if token == f"T{tid}:":
                    continue
                sep = ":" if ":" in token else "=" if "=" in token else None
                if sep is None:
                    raise ValueError(f"Bad transaction token at T{tid} in {source}: {token}")
                item, text = token.split(sep, 1)
                if not item or not text:
                    continue
                quantity = int(text)
                if quantity <= 0:
                    continue
                merged[item] = merged.get(item, 0) + quantity
                if merged[item] > 2_147_483_647:
                    raise ValueError(f"Quantity overflow for {item} at T{tid}")
            if merged:
                dst.write(" ".join(f"{item}:{quantity}" for item, quantity in merged.items()) + "\n")
            else:
                dst.write(f"T{tid}:\n")


def inspect_quantitative(db: Path, profits: Path, expected_tx: int, expected_items: int, origin: str) -> dict:
    profit_items = set()
    with profits.open(encoding="utf-8-sig") as f:
        for raw in f:
            parts = raw.replace(":", " ").replace("=", " ").split()
            if len(parts) >= 2:
                profit_items.add(parts[0])
    tx_count = nonempty = cells = total_utility = 0
    profit_map = {}
    with profits.open(encoding="utf-8-sig") as f:
        for raw in f:
            parts = raw.replace(":", " ").replace("=", " ").split()
            if len(parts) >= 2:
                profit_map[parts[0]] = int(parts[1])
    with db.open(encoding="utf-8-sig") as f:
        for raw in f:
            tokens = raw.split()
            valid = 0
            for token in tokens:
                sep = ":" if ":" in token else "="
                if sep not in token:
                    continue
                item, qty = token.split(sep, 1)
                if not qty:
                    continue
                q = int(qty)
                if q > 0:
                    valid += 1
                    total_utility += q * profit_map[item]
            tx_count += 1
            cells += valid
            nonempty += int(valid > 0)
    if tx_count != expected_tx or len(profit_items) != expected_items:
        raise ValueError(f"Unexpected dimensions for {db}: tx={tx_count}, items={len(profit_items)}")
    return {
        "transactions": tx_count, "nonempty_transactions": nonempty,
        "items": len(profit_items), "cells": cells,
        "average_length": cells / tx_count, "total_utility": total_utility,
        "item_count_sha256": sha256(db), "item_utility_sha256": sha256(profits),
        "utility_origin": origin, "conversion": "native quantitative utility format",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ppum-root", default=os.environ.get("PPUM_ROOT"))
    parser.add_argument("--force-download", action="store_true")
    parser.add_argument("--skip-download", action="store_true")
    args = parser.parse_args()
    if not args.ppum_root:
        raise SystemExit("Pass --ppum-root or set PPUM_ROOT")
    ppum_root = Path(args.ppum_root).resolve()
    READY.mkdir(parents=True, exist_ok=True)
    records = []

    for name, spec in QUANTITATIVE.items():
        stats = copy_quantitative(ppum_root, name, spec)
        stats.update(dataset=name, source="DSI-Lab1/PPUM", citation="DSI-Lab1/PPUM quantitative datasets",
                     quantity_semantics="upstream quantity", profit_semantics="upstream external profit")
        records.append(stats)

    liquor_out = READY / "liquor_11"
    liquor_out.mkdir(parents=True, exist_ok=True)
    normalize_database_file(ROOT / "datasets/liquor_11/item_count.txt", liquor_out / "item_count.txt")
    normalize_profit_file(ROOT / "datasets/liquor_11/item_utility.txt", liquor_out / "item_utility.txt")
    stats = inspect_quantitative(liquor_out / "item_count.txt", liquor_out / "item_utility.txt", 9283, 2626, "real")
    stats.update(dataset="liquor_11", source="SPMF", citation="SPMF Liquor_11",
                 quantity_semantics="prepared quantitative decomposition", profit_semantics="prepared external profit")
    records.append(stats)

    raw_dir = READY / "_downloads"
    for name, spec in SPMF_DATASETS.items():
        raw = raw_dir / Path(spec["url"]).name
        if not args.skip_download:
            download(spec["url"], raw, args.force_download)
        if not raw.exists():
            raise FileNotFoundError(f"Download missing: {raw}")
        stats = convert_spmf(raw, READY / name)
        prepared_items = spec.get("expected_prepared_items", spec["expected_items"])
        if stats["transactions"] != spec["expected_tx"] or stats["items"] != prepared_items:
            raise ValueError(f"Unexpected published dimensions for {name}: {stats}")
        stats.update(dataset=name, source=spec["url"], utility_origin=spec["utility_origin"],
                     source_items=spec["expected_items"], citation=spec["citation"],
                     quantity_semantics="encoded positive internal utility",
                     profit_semantics="unit profit (1)")
        records.append(stats)

    fields = ["dataset", "transactions", "nonempty_transactions", "items", "source_items", "cells", "average_length",
              "total_utility", "utility_origin", "quantity_semantics", "profit_semantics",
              "source", "citation", "conversion",
              "item_count_sha256", "item_utility_sha256", "source_sha256"]
    with (READY / "manifest.csv").open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fields, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        writer.writerows(records)
    print(f"[done] {len(records)} datasets in {READY}")


if __name__ == "__main__":
    main()
