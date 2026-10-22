#!/usr/bin/env python3
"""Strictly validate the canonical two-file experiment layout."""
from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="datasets/ready")
    args = parser.parse_args()
    base = (ROOT / args.root).resolve()
    with (base / "manifest.csv").open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    errors = []
    for meta in rows:
        name = meta["dataset"]
        folder = base / name
        db, table = folder / "item_count.txt", folder / "item_utility.txt"
        profits = {}
        if not db.exists() or not table.exists():
            errors.append(f"{name}: missing canonical input file")
            continue
        with table.open(encoding="utf-8-sig") as f:
            for line_no, raw in enumerate(f, 1):
                line = raw.strip()
                parts = line.split(":")
                if len(parts) != 2 or not parts[0] or not parts[1].isdigit() or int(parts[1]) <= 0:
                    errors.append(f"{name}: bad item:profit at line {line_no}: {line}")
                    continue
                if parts[0] in profits:
                    errors.append(f"{name}: duplicate profit item {parts[0]}")
                profits[parts[0]] = int(parts[1])
        tx_count = 0
        with db.open(encoding="utf-8-sig") as f:
            for tx_count, raw in enumerate(f, 1):
                seen = set()
                for token in raw.split():
                    if token == f"T{tx_count}:":
                        continue
                    parts = token.split(":")
                    if len(parts) != 2 or not parts[0] or not parts[1].isdigit() or int(parts[1]) <= 0:
                        errors.append(f"{name}: bad item:quantity at T{tx_count}: {token}")
                        continue
                    item = parts[0]
                    if item in seen:
                        errors.append(f"{name}: duplicate item {item} at T{tx_count}")
                    if item not in profits:
                        errors.append(f"{name}: missing profit for item {item} at T{tx_count}")
                    seen.add(item)
        if tx_count != int(meta["transactions"]):
            errors.append(f"{name}: transactions {tx_count} != manifest {meta['transactions']}")
        if len(profits) != int(meta["items"]):
            errors.append(f"{name}: profit items {len(profits)} != manifest {meta['items']}")
        if digest(db) != meta["item_count_sha256"] or digest(table) != meta["item_utility_sha256"]:
            errors.append(f"{name}: SHA-256 mismatch")
        print(f"[ok] {name}: tx={tx_count} items={len(profits)}")
    print(f"datasets={len(rows)} errors={len(errors)}")
    for error in errors[:100]:
        print("ERROR", error)
    if errors:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
