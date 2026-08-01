#!/usr/bin/env python3
"""Run the disposable multi-seed RingWorld worldgen/structure regression matrix."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUN_LOG = ROOT / "run-stronghold-test" / "logs" / "latest.log"
REPORT_DIR = ROOT / "build" / "reports" / "ringworld-worldgen-matrix"

MATRIX_RE = re.compile(
    r"\[worldgen-matrix] seed=(?P<numeric_seed>-?\d+) "
    r"layout=(?P<circumference>\d+)x(?P<width>\d+) "
    r"biomeFamilies=\[(?P<families>[^]]*)] biomeIds=\[(?P<biomes>[^]]*)] "
    r"chunks=(?P<chunks>\d+) caveAir=(?P<cave_air>\d+) ores=(?P<ores>\d+) "
    r"logs=(?P<logs>\d+) starts=(?P<starts>\d+) structureIds=\[(?P<structures>[^]]*)] "
    r"crossingStarts=(?P<crossing_starts>\d+) "
    r"crossingStructureIds=\[(?P<crossing_structures>[^]]*)] "
    r"references=(?P<references>\d+) lootContainers=(?P<loot>\d+) "
    r"structuresWithSpawnOverrides=(?P<spawn_override_structures>\d+) "
    r"spawnOverrideStructureIds=\[(?P<spawn_override_ids>[^]]*)]"
)
MONUMENT_RE = re.compile(
    r"\[worldgen-matrix] monumentStatus=(?P<status>\w+) "
    r"monumentReason=(?P<reason>\S+) monumentCandidate=(?P<candidate>\S+)"
    r"(?: spawnOverrideEntries=(?P<spawn_override_entries>\d+))?"
)

REQUIRED_MAJOR_FAMILIES = {
    "badlands", "beach", "cave", "desert", "forest", "jungle",
    "mountain", "ocean", "plains", "river", "savanna", "snowy",
    "swamp", "taiga",
}


@dataclass(frozen=True)
class Case:
    name: str
    seed: str
    circumference: int
    width: int
    wall_height: int = 160


DEFAULT_CASES = (
    Case("production", "ringworld-regression-1", 16384, 256),
    Case("seam-crossing", "ringworld-matrix-0", 2048, 416),
    Case("terminal-policy", "ringworld-matrix-3", 2048, 416),
)


def parse_list(value: str) -> list[str]:
    return [] if not value.strip() else [part.strip() for part in value.split(",")]


def parse_log(text: str) -> dict[str, object]:
    matrix = MATRIX_RE.search(text)
    monument = MONUMENT_RE.search(text)
    if matrix is None or monument is None or "[stronghold-test] PASS" not in text:
        raise ValueError("runtime log lacks a complete passing worldgen-matrix record")
    values: dict[str, object] = {
        key: int(value) if key in {
            "numeric_seed", "circumference", "width", "chunks", "cave_air",
            "ores", "logs", "starts", "crossing_starts", "references", "loot",
            "spawn_override_structures",
        } else value
        for key, value in matrix.groupdict().items()
    }
    for key in ("families", "biomes", "structures", "crossing_structures", "spawn_override_ids"):
        values[key] = parse_list(str(values[key]))
    values["monument_status"] = monument.group("status")
    values["monument_reason"] = monument.group("reason")
    values["monument_candidate"] = monument.group("candidate")
    values["monument_spawn_override_entries"] = int(monument.group("spawn_override_entries") or 0)
    return values


def validate_aggregate(records: list[dict[str, object]]) -> None:
    families = {family for record in records for family in record["families"]}
    missing = sorted(REQUIRED_MAJOR_FAMILIES - families)
    if missing:
        raise ValueError(f"major biome-family coverage is incomplete: {', '.join(missing)}")
    if sum(int(record["crossing_starts"]) for record in records) == 0:
        raise ValueError("no deliberately seam-crossing structure was generated")
    for field in ("cave_air", "ores", "logs", "starts", "references", "loot"):
        if sum(int(record[field]) for record in records) == 0:
            raise ValueError(f"aggregate worldgen evidence has zero {field}")
    statuses = {str(record["monument_status"]) for record in records}
    if "SATISFIED" not in statuses or "UNSATISFIED" not in statuses:
        raise ValueError("matrix must exercise both satisfied and unsatisfied saved monument policy")
    if not any(int(record["monument_spawn_override_entries"]) > 0 for record in records):
        raise ValueError("matrix did not observe structure-controlled mob spawn metadata")


def validate_reload(fresh: dict[str, object], resumed: dict[str, object]) -> None:
    stable_fields = (
        "numeric_seed", "circumference", "width", "families", "biomes", "chunks",
        "cave_air", "ores", "logs", "starts", "structures", "crossing_starts",
        "crossing_structures", "references", "loot", "monument_status",
        "spawn_override_structures", "spawn_override_ids",
        "monument_reason", "monument_candidate", "monument_spawn_override_entries",
    )
    changed = [field for field in stable_fields if fresh[field] != resumed[field]]
    if changed:
        raise ValueError("fresh/reload worldgen evidence changed: " + ", ".join(changed))


def run_case(case: Case, resume: bool, report_dir: Path) -> dict[str, object]:
    phase = "resume" if resume else "fresh"
    command = [
        "./gradlew", "runStrongholdTestServer", "--console=plain",
        f"-PringStrongholdTestSeed={case.seed}",
        f"-PringStrongholdTestCircumference={case.circumference}",
        f"-PringStrongholdTestWidth={case.width}",
        f"-PringStrongholdTestWallHeight={case.wall_height}",
        "-PringWorldgenMatrix=true",
        f"-PringStrongholdTestResume={'true' if resume else 'false'}",
    ]
    print(f"[worldgen-matrix-runner] {case.name} {phase}", flush=True)
    completed = subprocess.run(command, cwd=ROOT, env=os.environ.copy(), check=False)
    if completed.returncode != 0:
        raise RuntimeError(f"{case.name} {phase} failed with exit code {completed.returncode}")
    text = RUN_LOG.read_text(encoding="utf-8")
    destination = report_dir / f"{case.name}-{phase}.log"
    shutil.copy2(RUN_LOG, destination)
    record = parse_log(text)
    if record["circumference"] != case.circumference or record["width"] != case.width:
        raise RuntimeError(
            f"{case.name} {phase} loaded layout {record['circumference']}x{record['width']} "
            f"instead of {case.circumference}x{case.width}"
        )
    record.update({"case": asdict(case), "phase": phase, "log": destination.name})
    return record


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report-dir", type=Path, default=REPORT_DIR)
    parser.add_argument("--skip-resume", action="store_true",
                        help="Skip the production save/reload pass (discovery only).")
    parser.add_argument("--allow-incomplete-coverage", action="store_true",
                        help="Write results without enforcing aggregate release coverage.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    report_dir = args.report_dir.resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    records: list[dict[str, object]] = []
    production_fresh = run_case(DEFAULT_CASES[0], False, report_dir)
    records.append(production_fresh)
    if not args.skip_resume:
        production_resume = run_case(DEFAULT_CASES[0], True, report_dir)
        validate_reload(production_fresh, production_resume)
        records.append(production_resume)
    records.extend(run_case(case, False, report_dir) for case in DEFAULT_CASES[1:])
    try:
        if not args.allow_incomplete_coverage:
            validate_aggregate(records)
        result = {"status": "PASS", "cases": records}
        exit_code = 0
    except ValueError as failure:
        result = {"status": "FAIL", "reason": str(failure), "cases": records}
        exit_code = 1
    summary = report_dir / "summary.json"
    summary.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"[worldgen-matrix-runner] {result['status']} {summary}")
    if exit_code:
        print(result["reason"], file=sys.stderr)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
