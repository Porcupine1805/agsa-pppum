#!/usr/bin/env python3
"""Run the TDP paper matrix with one JVM per (dataset, minPer) row."""
import csv
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HEAP_PAPER = {
    "mushroom": 4,
    "retail": 6,
    "liquor": 2,
    "foodmart": 2,
    "liquor_11": 2,
    "ecommerce_real": 4,
    "fruithut_real": 6,
    "yoochoose_real": 8,
    "chainstore_real": 12,
}
SOLO_HEAP_PAPER = 20
BUDGET_PAPER = 46
MAX_JVMS = 0  # 0 = no cap
# Local Wave 4 (q2_seeds10_small): four small datasets, measured peak < 580 MiB.
# The parent `run_q2_local.ps1 -Wave all` was started at budget=14g; this profile
# overrides that CLI value when the config stem matches. 12 JVMs fill the
# 6C/12T host; heap stays 2g (mushroom AGSA peak 578 MiB). Do not raise
# max_jvms above 12: extra workers only queue on the same six cores.
LOCAL_Q2_PACK = {
    "budget": 24,
    "solo": 12,
    "max_jvms": 12,
    "heap": {},
}
PACK_PROFILES = {
    "q2_seeds10_small": {
        "budget": 24,
        "solo": 4,
        "max_jvms": 12,
        "heap": {
            "foodmart": 2,
            "liquor_11": 2,
            "mushroom": 2,
            "ecommerce_real": 2,
        },
    },
    "local-q2": LOCAL_Q2_PACK,
}
START_ORDER = {
    "foodmart": 0,
    "liquor_11": 1,
    "liquor": 1,
    "ecommerce_real": 2,
    "mushroom": 3,
    "retail": 4,
    "fruithut_real": 5,
    "yoochoose_real": 6,
    "chainstore_real": 7,
}
JAVA_FLAGS = [
    "-XX:ObjectAlignmentInBytes=16",
    "-XX:+UseCompressedOops",
    "-XX:+UseG1GC",
    "-XX:+UseStringDeduplication",
    "-XX:ParallelGCThreads=1",
    "-XX:ConcGCThreads=1",
    "-Dfile.encoding=UTF-8",
]


def log(msg):
    print(msg, flush=True)


def job_heap(dataset, solo=False):
    if solo:
        return SOLO_HEAP_PAPER
    return min(HEAP_PAPER.get(dataset, 4), BUDGET_PAPER)


def pid_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes
        PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
        STILL_ACTIVE = 259
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, int(pid))
        if not handle:
            return False
        try:
            code = wintypes.DWORD()
            ok = kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
            return bool(ok) and code.value == STILL_ACTIVE
        finally:
            kernel32.CloseHandle(handle)
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def acquire_run_lock(lock_path: Path) -> None:
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    my_pid = os.getpid()
    while True:
        try:
            fd = os.open(str(lock_path), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, f"{my_pid}\n".encode("ascii"))
            os.close(fd)
            log(f"[parallel] lock acquired {lock_path.name} pid={my_pid}")
            return
        except FileExistsError:
            other = 0
            try:
                other = int(lock_path.read_text(encoding="ascii").split()[0])
            except (OSError, ValueError, IndexError):
                other = 0
            if other and other != my_pid and pid_alive(other):
                log(f"[parallel] waiting for orchestrator pid={other} ({lock_path.name})")
                time.sleep(5)
                continue
            try:
                lock_path.unlink()
            except OSError:
                time.sleep(1)


def release_run_lock(lock_path: Path) -> None:
    try:
        owner = int(lock_path.read_text(encoding="ascii").split()[0])
    except (OSError, ValueError, IndexError):
        return
    if owner == os.getpid():
        try:
            lock_path.unlink()
        except OSError:
            pass


def apply_pack_profile(config_rel, pack_profile):
    global BUDGET_PAPER, SOLO_HEAP_PAPER, MAX_JVMS, HEAP_PAPER
    if pack_profile == "none":
        return
    stem = Path(config_rel).stem
    if pack_profile == "auto":
        if stem in PACK_PROFILES:
            key = stem
        elif stem.startswith("q2_"):
            key = "local-q2"
        else:
            return
    else:
        key = pack_profile
    prof = PACK_PROFILES.get(key)
    if not prof:
        return
    BUDGET_PAPER = int(prof["budget"])
    SOLO_HEAP_PAPER = int(prof["solo"])
    MAX_JVMS = int(prof["max_jvms"])
    HEAP_PAPER = {**HEAP_PAPER, **prof.get("heap", {})}
    log(
        f"[parallel] pack-profile={key} budget={BUDGET_PAPER}g "
        f"max_jvms={MAX_JVMS} solo={SOLO_HEAP_PAPER}g heaps={prof.get('heap') or 'HEAP_PAPER'}"
    )


def read_rows(path):
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        rows = [r for r in reader if r.get("enabled", "true").lower() != "false"]
    return fieldnames, rows


def write_rows(path, fieldnames, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def group_configs(rows):
    order, groups = [], {}
    for row in rows:
        name = row["configId"]
        if name not in groups:
            order.append(name)
            groups[name] = []
        groups[name].append(row)
    order.sort(key=lambda n: (START_ORDER.get(groups[n][0]["dataset"], 9), n))
    return order, groups


def launch(job_name, heap, config, out, log_path, sensitive_dir, xms):
    cmd = [
        "java", f"-Xms{xms}", f"-Xmx{heap}g", *JAVA_FLAGS,
        "-jar", "build/agsa-pppum.jar", "batch",
        "--config", str(config), "--out", str(out),
        "--continueOnError", "true", "--saveArtifacts", "false",
        "--sensitiveDir", str(sensitive_dir),
    ]
    log_path.parent.mkdir(parents=True, exist_ok=True)
    handle = log_path.open("w", encoding="utf-8")
    proc = subprocess.Popen(cmd, cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT)
    return proc, handle


def merge_outputs(parts, dest):
    header = None
    body = []
    for part in parts:
        if not part.exists() or part.stat().st_size == 0:
            continue
        lines = part.read_text(encoding="utf-8").splitlines()
        if not lines:
            continue
        if header is None:
            header = lines[0]
        elif lines[0] != header:
            raise SystemExit(f"header mismatch: {part}")
        body.extend(lines[1:])
    dest.parent.mkdir(parents=True, exist_ok=True)
    if header is None:
        raise SystemExit(f"no rows to merge into {dest}")
    dest.write_text(header + "\n" + "\n".join(body) + "\n", encoding="utf-8")


def config_ids(path):
    with path.open(encoding="utf-8", newline="") as f:
        return {r["config_id"] for r in csv.DictReader(f)}


def part_is_complete(path, expected_config, expected_algorithms):
    if not path.exists() or path.stat().st_size == 0:
        return False
    try:
        with path.open(encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))
        valid_statuses = {"OK", "INFEASIBLE", "NO_PHUI"}
        hashes = {r.get("sensitive_hash", "") for r in rows}
        return (len(rows) == expected_algorithms and
                all(r.get("config_id") == expected_config for r in rows) and
                all(r.get("status") in valid_statuses for r in rows) and
                len(hashes) == 1)
    except (OSError, csv.Error):
        return False


def run_matrix(config_rel, out_rel, fresh=False, rerun_configs=None, timeout_sec=0):
    config = ROOT / config_rel
    dest = ROOT / out_rel
    stem = config.stem
    fieldnames, rows = read_rows(config)
    order, groups = group_configs(rows)
    rerun_configs = set(rerun_configs or [])
    unknown = sorted(rerun_configs - set(order))
    if unknown:
        raise SystemExit(f"unknown --rerun-config value(s): {', '.join(unknown)}")
    part_dir = ROOT / "results" / "parts" / stem
    sensitive_dir = ROOT / "results" / "sensitive_sets" / stem
    cap = MAX_JVMS if MAX_JVMS else "unlimited"
    log(
        f"[parallel] {config_rel} jobs={len(order)} rows={len(rows)} "
        f"budget={BUDGET_PAPER}g max_jvms={cap} timeout={timeout_sec}s"
    )
    pending = []
    running = []
    done = {}
    retries = []

    for job_name in order:
        part = part_dir / f"{job_name}.csv"
        mark = part_dir / f"{job_name}.timeout"
        algorithm_count = len([x for x in groups[job_name][0]["algorithms"].replace(",", "|").split("|") if x.strip()])
        if not fresh and job_name not in rerun_configs and mark.exists() and part.exists():
            done[job_name] = part
            log(f"[parallel] resume-timeout {job_name} from {part.relative_to(ROOT)}")
        elif not fresh and job_name not in rerun_configs and part_is_complete(part, job_name, algorithm_count):
            done[job_name] = part
            log(f"[parallel] resume {job_name} from {part.relative_to(ROOT)}")
        else:
            pending.append(job_name)

    def start(job_name, solo):
        dataset = groups[job_name][0]["dataset"]
        heap = job_heap(dataset, solo)
        cfg = part_dir / f"{job_name}.config.csv"
        out = part_dir / f"{job_name}.csv"
        worker_log = part_dir / f"{job_name}.log"
        write_rows(cfg, fieldnames, groups[job_name])
        if out.exists():
            out.unlink()
        proc, handle = launch(job_name, heap, cfg, out, worker_log, sensitive_dir, "256m")
        log(f"[parallel] start {job_name} dataset={dataset} heap={heap}g pid={proc.pid}")
        running.append({
            "dataset": job_name, "heap": heap, "solo": solo, "proc": proc,
            "handle": handle, "out": out, "log": worker_log, "t0": time.time(),
        })

    while pending or running or retries:
        still = []
        for job in running:
            code = job["proc"].poll()
            elapsed = int(time.time() - job["t0"])
            timed_out = timeout_sec > 0 and code is None and elapsed >= timeout_sec
            if timed_out:
                log(f"[parallel] timeout {job['dataset']} after {elapsed}s; killing pid={job['proc'].pid}")
                job["proc"].terminate()
                try:
                    job["proc"].wait(timeout=30)
                except subprocess.TimeoutExpired:
                    job["proc"].kill()
                    job["proc"].wait(timeout=10)
                code = job["proc"].poll()
                mark = job["out"].with_suffix(".timeout")
                mark.write_text(f"timeout_sec={timeout_sec} elapsed={elapsed}\n", encoding="utf-8")
                job["handle"].close()
                if job["out"].exists() and job["out"].stat().st_size > 0:
                    done[job["dataset"]] = job["out"]
                    log(f"[parallel] kept partial {job['out'].relative_to(ROOT)}")
                continue
            if code is None:
                still.append(job)
                continue
            job["handle"].close()
            log(f"[parallel] done {job['dataset']} exit={code} elapsed={elapsed}s heap={job['heap']}g")
            algorithm_count = len([
                x for x in groups[job["dataset"]][0]["algorithms"].replace(",", "|").split("|")
                if x.strip()
            ])
            if code == 0 and part_is_complete(
                    job["out"], job["dataset"], algorithm_count):
                done[job["dataset"]] = job["out"]
            elif not job["solo"]:
                log(f"[parallel] retry {job['dataset']} alone at {SOLO_HEAP_PAPER}g")
                retries.append(job["dataset"])
            else:
                raise SystemExit(f"{job['dataset']} failed twice; see {job['log']}")
        running = still
        used = sum(job["heap"] for job in running)
        if retries:
            rest = []
            for nxt in retries:
                if MAX_JVMS and len(running) >= MAX_JVMS:
                    rest.append(nxt)
                    continue
                heap = job_heap(groups[nxt][0]["dataset"], True)
                if used + heap > BUDGET_PAPER:
                    rest.append(nxt)
                    continue
                log(f"[parallel] retry {nxt} heap={heap}g alongside {len(running)} running")
                start(nxt, True)
                used += heap
            retries = rest
        if pending and not any(job["solo"] for job in running):
            rest = []
            for dataset in pending:
                if MAX_JVMS and len(running) >= MAX_JVMS:
                    rest.append(dataset)
                    continue
                ds = groups[dataset][0]["dataset"]
                heap = job_heap(ds, False)
                if used + heap > BUDGET_PAPER:
                    rest.append(dataset)
                    continue
                start(dataset, False)
                used += heap
            pending = rest
        if running:
            time.sleep(5)

    parts = [done[name] for name in order if name in done and done[name].exists()]
    if not parts:
        raise SystemExit(f"no completed parts to merge into {dest}")
    merge_outputs(parts, dest)
    expected = {row["configId"] for row in rows}
    got = config_ids(dest)
    missing = sorted(expected - got)
    if missing:
        log(f"[parallel] warning {dest.name} missing {len(missing)} configs, first={missing[:5]}")
    log(f"[parallel] merged {dest.name} configs={len(got)}")
    subprocess.run(
        [sys.executable, "scripts/validate_results.py", str(dest), "--allow-infeasible", "--allow-hf"],
        cwd=ROOT, check=False,
    )


def main():
    global BUDGET_PAPER, SOLO_HEAP_PAPER, MAX_JVMS
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="experiments/tdp_paper.csv")
    parser.add_argument("--out", default="results/tdp_paper_runs.csv")
    parser.add_argument("--budget-gb", type=int, default=BUDGET_PAPER)
    parser.add_argument("--solo-heap-gb", type=int, default=SOLO_HEAP_PAPER)
    parser.add_argument("--fresh", action="store_true", help="ignore completed per-config checkpoints")
    parser.add_argument(
        "--rerun-config", action="append", default=[], metavar="CONFIG_ID",
        help="rerun only this config checkpoint; may be supplied more than once",
    )
    parser.add_argument(
        "--timeout-sec", type=int, default=0,
        help="kill a worker after this many seconds; 0 disables the cap",
    )
    parser.add_argument(
        "--max-jvms", type=int, default=0,
        help="cap concurrent workers; 0 means no cap (pack profiles may set one)",
    )
    parser.add_argument(
        "--pack-profile", default="auto",
        help="auto: apply PACK_PROFILES[config stem]; none: keep CLI budget/heaps",
    )
    args = parser.parse_args()
    BUDGET_PAPER = args.budget_gb
    SOLO_HEAP_PAPER = args.solo_heap_gb
    MAX_JVMS = args.max_jvms
    os.chdir(ROOT)
    if args.fresh and args.rerun_config:
        parser.error("--fresh and --rerun-config are mutually exclusive")
    apply_pack_profile(args.config, args.pack_profile)
    if args.max_jvms:
        MAX_JVMS = args.max_jvms
    lock_path = (ROOT / args.out).with_suffix(".lock")
    acquire_run_lock(lock_path)
    try:
        run_matrix(args.config, args.out, args.fresh, args.rerun_config, args.timeout_sec)
    finally:
        release_run_lock(lock_path)
    log(f"Matrix finished: {args.out}")
    log("validate_results.py exits nonzero if any feasible row has HF>0 or violates the result schema.")


if __name__ == "__main__":
    main()
