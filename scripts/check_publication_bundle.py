#!/usr/bin/env python3
"""Audit the reproducibility bundle required for the paper.

The script never invents results.  It inventories files, checks matrix coverage and
core correctness invariants, and writes both a machine-readable CSV and a concise
Markdown readiness report.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METRICS = ("algorithmWall_ms", "utilityLoss_pct", "MC_pct", "AC_pct", "modifiedTx_pct")
PROFILE_EXPECTATIONS = {"paper": (72, 360), "scaling": (12, 60)}
REQUIRED_RESULT_FIELDS = {
    "config_id", "dataset", "replicate", "seed", "timeline", "sensitive_hash",
    "algorithm", "status", "HF", "MC", "AC", "utilityLoss_pct",
    "modifiedTx_pct", "algorithmWall_ms", "algorithmPeak_MiB",
}


@dataclass(frozen=True)
class Item:
    category: str
    path: str
    required: bool
    description: str


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def expected_by_config(config_path: Path) -> dict[str, int]:
    expected: dict[str, int] = {}
    for row in read_csv(config_path):
        if row.get("enabled", "true").lower() == "false":
            continue
        algorithms = [value.strip() for value in row["algorithms"].replace(",", "|").split("|") if value.strip()]
        expected[row["configId"]] = len(algorithms)
    return expected


def audit_matrix(profile: str) -> tuple[list[str], list[str]]:
    config = ROOT / f"experiments/ready_{profile}.csv"
    result = ROOT / f"results/ready_{profile}_runs.csv"
    errors: list[str] = []
    notes: list[str] = []
    if not config.exists() or not result.exists():
        if not config.exists():
            errors.append(f"missing config: {config.relative_to(ROOT)}")
        if not result.exists():
            errors.append(f"missing result: {result.relative_to(ROOT)}")
        return errors, notes

    expected = expected_by_config(config)
    rows = read_csv(result)
    expected_configs, expected_rows = PROFILE_EXPECTATIONS[profile]
    if len(expected) != expected_configs:
        errors.append(f"{profile}: registered config count is {len(expected)}, expected {expected_configs}")
    if sum(expected.values()) != expected_rows:
        errors.append(
            f"{profile}: registered algorithm-row count is {sum(expected.values())}, expected {expected_rows}"
        )
    present_fields = set(rows[0]) if rows else set()
    missing_fields = sorted(REQUIRED_RESULT_FIELDS - present_fields)
    if missing_fields:
        errors.append(f"{profile}: required result fields missing: {missing_fields}")
    counts = Counter(row.get("config_id", "") for row in rows)
    missing = sorted(set(expected) - set(counts))
    extra = sorted(set(counts) - set(expected))
    wrong = sorted(key for key, count in expected.items() if counts.get(key) != count)
    if missing:
        errors.append(f"{profile}: {len(missing)} config(s) missing; first={missing[:3]}")
    if extra:
        errors.append(f"{profile}: unexpected config(s): {extra[:3]}")
    if wrong:
        errors.append(f"{profile}: wrong row count for {len(wrong)} config(s); first={wrong[:3]}")

    groups: dict[tuple[str, str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        key = tuple(row.get(name, "") for name in ("config_id", "dataset", "replicate", "seed", "timeline"))
        groups[key].append(row)
    infeasible = 0
    for key, group in groups.items():
        hashes = {row.get("sensitive_hash", "") for row in group}
        if len(hashes) != 1:
            errors.append(f"{profile}: unpaired sensitive sets at {key}")
        for row in group:
            status = row.get("status", "")
            if status in {"INFEASIBLE", "NO_PHUI"}:
                infeasible += 1
                continue
            if status != "OK":
                errors.append(f"{profile}: {row.get('config_id')}/{row.get('algorithm')} status={status!r}")
                continue
            if int(row.get("HF", "-1")) != 0:
                errors.append(f"{profile}: {row.get('config_id')}/{row.get('algorithm')} has HF != 0")
            if (row.get("timeline") == "fixed" and row.get("algorithm", "").startswith("AGSA")
                    and "NOGUARD" not in row.get("algorithm", "") and int(row.get("AC", "-1")) != 0):
                errors.append(f"{profile}: {row.get('config_id')}/{row.get('algorithm')} violates AC=0 guard")
    notes.append(
        f"{profile}: configs={len(expected)}, expected_rows={sum(expected.values())}, "
        f"actual_rows={len(rows)}, infeasible_or_no_phui={infeasible}"
    )
    return errors, notes


def suite_ok(folder: str) -> tuple[bool, str]:
    summary = ROOT / folder / "summary.txt"
    failures = ROOT / folder / "failures.csv"
    if not summary.exists() or not failures.exists():
        return False, f"{folder}: summary/failures missing"
    text = summary.read_text(encoding="utf-8-sig").strip()
    failure_rows = read_csv(failures)
    ok = "failures=0" in text and not failure_rows
    return ok, f"{folder}: {text}; failure_rows={len(failure_rows)}"


def audit_environment() -> tuple[list[str], str]:
    path = ROOT / "results/run_environment.txt"
    if not path.exists():
        return ["environment manifest missing"], "environment: missing"
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    required = {
        "timestamp_utc", "os_name", "os_arch", "java_version", "java_vm",
        "available_processors", "jvm_max_heap_mib", "physical_memory_mib",
        "project_git_commit",
    }
    errors = [f"environment field missing: {key}" for key in sorted(required - set(values))]
    if values.get("project_git_commit", "").startswith("UNAVAILABLE"):
        errors.append("project_git_commit is unavailable; create a reproducibility commit before the final run")
    note = (
        f"environment: java={values.get('java_version', '?')}, "
        f"cpus={values.get('available_processors', '?')}, "
        f"physical_memory_mib={values.get('physical_memory_mib', '?')}, "
        f"commit={values.get('project_git_commit', '?')}"
    )
    return errors, note


def inventory() -> list[Item]:
    items = [
        Item("provenance", "datasets/ready/manifest.csv", True, "Dataset dimensions, semantics, sources and SHA-256"),
        Item("provenance", "results/run_environment.txt", True, "JVM, OS, CPU and memory used for the run"),
        Item("configuration", "experiments/ready_paper.csv", True, "Registered main comparison matrix"),
        Item("configuration", "experiments/ready_scaling.csv", True, "Registered scalability matrix"),
        Item("correctness", "datasets/adversarial_200/summary.txt", True, "Summary of the 200-case suite"),
        Item("correctness", "datasets/adversarial_200/failures.csv", True, "Must contain header only"),
        Item("correctness", "datasets/adversarial_2000/summary.txt", True, "Summary of the 2,000-case suite"),
        Item("correctness", "datasets/adversarial_2000/failures.csv", True, "Must contain header only"),
        Item("results", "results/ready_paper_runs.csv", True, "Raw paired rows for all main configurations and algorithms"),
        Item("results", "results/ready_scaling_runs.csv", True, "Raw paired rows for the scalability experiment"),
        Item("software", "scripts/run_publication_parallel.py", True, "Checkpointed experiment runner"),
        Item("software", "scripts/validate_results.py", True, "Scientific invariant and schema validator"),
        Item("software", "scripts/plot_experiment_results.py", True, "Table and figure generator"),
        Item("software", "RESULT_SCHEMA.md", True, "Definitions of raw output columns"),
        Item("supplement", "results/parts/ready_paper", False, "Per-configuration inputs, logs and checkpoints"),
        Item("supplement", "results/parts/ready_scaling", False, "Per-configuration inputs, logs and checkpoints"),
        Item("supplement", "results/sensitive_sets/ready_paper", False, "Exact sensitive sets for reproducibility"),
        Item("supplement", "results/sensitive_sets/ready_scaling", False, "Exact sensitive sets for reproducibility"),
    ]
    for profile in ("paper", "scaling"):
        base = f"results/figures/{profile}"
        items.extend([
            Item("aggregate", f"{base}/summary.csv", True, f"Mean/SD table for {profile}"),
            Item("aggregate", f"{base}/summary_table.tex", True, f"LaTeX table for {profile}"),
        ])
        for metric in METRICS:
            items.append(Item("figure", f"{base}/{metric}.pdf", True, f"Vector {metric} comparison"))
            items.append(Item("figure", f"{base}/{metric}.png", False, f"300-dpi preview of {metric}"))
    return items


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", default="results/publication_inventory.csv")
    parser.add_argument("--report", default="results/publication_bundle_check.md")
    parser.add_argument("--strict", action="store_true", help="exit nonzero when the publication bundle is incomplete")
    args = parser.parse_args()

    items = inventory()
    records = []
    missing_required = []
    for item in items:
        path = ROOT / item.path
        exists = path.exists()
        if item.required and not exists:
            missing_required.append(item.path)
        records.append({
            "category": item.category,
            "path": item.path,
            "required": str(item.required).lower(),
            "exists": str(exists).lower(),
            "bytes": path.stat().st_size if exists and path.is_file() else "",
            "sha256": sha256(path) if exists and path.is_file() else "",
            "description": item.description,
        })

    scientific_errors: list[str] = []
    notes: list[str] = []
    for profile in ("paper", "scaling"):
        errors, profile_notes = audit_matrix(profile)
        scientific_errors.extend(errors)
        notes.extend(profile_notes)
    for folder in ("datasets/adversarial_200", "datasets/adversarial_2000"):
        ok, note = suite_ok(folder)
        notes.append(note)
        if not ok:
            scientific_errors.append(f"correctness suite failed: {folder}")
    environment_errors, environment_note = audit_environment()
    scientific_errors.extend(environment_errors)
    notes.append(environment_note)

    inventory_path = ROOT / args.inventory
    inventory_path.parent.mkdir(parents=True, exist_ok=True)
    with inventory_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=records[0].keys(), lineterminator="\n")
        writer.writeheader()
        writer.writerows(records)

    ready = not missing_required and not scientific_errors
    report_path = ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Publication bundle readiness",
        "",
        f"Status: **{'READY' if ready else 'NOT READY'}**",
        "",
        f"Required files: {sum(item.required for item in items)}; missing: {len(missing_required)}.",
        f"Scientific/coverage errors: {len(scientific_errors)}.",
        "",
        "## Matrix and correctness notes",
        "",
        *[f"- {note}" for note in notes],
        "",
        "## Missing required files",
        "",
        *([f"- `{path}`" for path in missing_required] or ["- None"]),
        "",
        "## Scientific or coverage errors",
        "",
        *([f"- {error}" for error in scientific_errors] or ["- None"]),
        "",
        f"Full file inventory and SHA-256 values: `{Path(args.inventory).as_posix()}`.",
        "",
    ]
    report_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"publication_bundle={'READY' if ready else 'NOT_READY'} missing={len(missing_required)} errors={len(scientific_errors)}")
    print(f"inventory={inventory_path.relative_to(ROOT)} report={report_path.relative_to(ROOT)}")
    if args.strict and not ready:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
