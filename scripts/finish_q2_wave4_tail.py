#!/usr/bin/env python3
"""Finish Wave 4 mushroom p1 r7/r9 in parallel with the already-running r8 AGSA."""
import csv
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from run_publication_parallel import (  # noqa: E402
    ROOT,
    launch,
    log,
    merge_outputs,
    part_is_complete,
    read_rows,
    group_configs,
)

PART = ROOT / "results" / "parts" / "q2_seeds10_small"
SENS = ROOT / "results" / "sensitive_sets" / "q2_seeds10_small"
DEST = ROOT / "results" / "q2" / "q2_seeds10_small_runs.csv"
CONFIG = ROOT / "experiments" / "q2_seeds10_small.csv"
NEED = [
    "q2s10-mushroom-n8416-p1-r7",
    "q2s10-mushroom-n8416-p1-r9",
]
WAIT = [
    "q2s10-mushroom-n8416-p1-r7",
    "q2s10-mushroom-n8416-p1-r8",
    "q2s10-mushroom-n8416-p1-r9",
]
ALGS = 6


def complete(name: str) -> bool:
    return part_is_complete(PART / f"{name}.csv", name, ALGS)


def main():
    fieldnames, rows = read_rows(CONFIG)
    order, groups = group_configs(rows)
    running = []
    for name in NEED:
        if complete(name):
            log(f"[tail] already complete {name}")
            continue
        cfg = PART / f"{name}.config.csv"
        out = PART / f"{name}.csv"
        worker_log = PART / f"{name}.log"
        if out.exists():
            out.unlink()
        proc, handle = launch(name, 4, cfg, out, worker_log, SENS, "256m")
        log(f"[tail] start {name} heap=4g pid={proc.pid}")
        running.append((name, proc, handle, time.time()))

    while True:
        still = []
        for name, proc, handle, t0 in running:
            code = proc.poll()
            elapsed = int(time.time() - t0)
            if code is None:
                still.append((name, proc, handle, t0))
                continue
            handle.close()
            log(f"[tail] done {name} exit={code} elapsed={elapsed}s complete={complete(name)}")
        running = still
        if all(complete(n) for n in WAIT):
            break
        if not running and not all(complete(n) for n in WAIT):
            missing = [n for n in WAIT if not complete(n)]
            raise SystemExit(f"[tail] workers exited with incomplete parts: {missing}")
        time.sleep(5)

    parts = []
    for name in order:
        part = PART / f"{name}.csv"
        if part_is_complete(part, name, ALGS) or (part.exists() and part.stat().st_size > 0):
            parts.append(part)
    merge_outputs(parts, DEST)
    log(f"[tail] merged {DEST} parts={len(parts)}")


if __name__ == "__main__":
    import os
    os.chdir(ROOT)
    main()
