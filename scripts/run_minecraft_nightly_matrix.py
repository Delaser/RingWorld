#!/usr/bin/env python3
"""Plan or execute the ordered 26.1.x RingWorld nightly qualification matrix."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
from typing import Any, Mapping, Sequence

from minecraft_qualification_executor import (
    QualificationExecutionError, create_contained_directories, execute_command,
    new_run_id, write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, QualificationPaths, Verdict
from run_gradle_production_lifecycle_qualification import (
    _source_world, _validate_source_version, _world_inventory, _world_observation,
)
from run_minecraft_qualification import (
    ROOT, collect_source_provenance, load_manifest, validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURES = (
    "creation-ui", "worldgen", "atlas-recovery", "atlas-ui", "multiplayer",
    "raid", "map-compass", "production-lifecycle", "curved-objects", "production-render",
)
SCRIPT_BY_FIXTURE = {
    "creation-ui": "run_gradle_creation_ui_qualification.py",
    "worldgen": "run_worldgen_qualification.py",
    "atlas-recovery": "run_atlas_recovery_qualification.py",
    "atlas-ui": "run_gradle_atlas_ui_qualification.py",
    "multiplayer": "run_gradle_multiplayer_qualification.py",
    "raid": "run_gradle_raid_qualification.py",
    "map-compass": "run_gradle_map_compass_qualification.py",
    "production-lifecycle": "run_gradle_production_lifecycle_qualification.py",
    "curved-objects": "run_gradle_curved_objects_qualification.py",
    "production-render": "run_gradle_production_render_qualification.py",
}
QUICK_FIXTURES = {"worldgen", "atlas-recovery", "multiplayer", "raid",
                  "production-lifecycle", "production-render"}
PRODUCTION_FIXTURES = {"production-lifecycle", "production-render"}
GRADLE_FIXTURES = {"creation-ui", "atlas-ui", "multiplayer", "raid", "map-compass",
                   "production-lifecycle", "curved-objects", "production-render"}
LOOM_SEED_FIXTURES = {"multiplayer", "raid", "production-lifecycle", "production-render"}


class NightlyMatrixError(QualificationExecutionError):
    """The unattended nightly matrix could not produce trustworthy evidence."""


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--quick-run-id", required=True)
    result.add_argument("--production-world", required=True)
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    result.add_argument("--cell", action="append", dest="cells")
    result.add_argument("--fixture", action="append", choices=FIXTURES, dest="fixtures")
    result.add_argument("--gradle-dependency-cache")
    result.add_argument("--gradle-distribution-zip")
    result.add_argument("--gradle-loom-cache")
    result.add_argument("--execute", action="store_true")
    return result


def _cells(manifest: Mapping[str, Any], selected: Sequence[str] | None) -> tuple[Mapping[str, Any], ...]:
    raw = manifest.get("cells")
    if not isinstance(raw, list):
        raise NightlyMatrixError("manifest has no cells")
    by_id = {cell.get("id"): cell for cell in raw if isinstance(cell, Mapping)}
    ids = tuple(selected) if selected else tuple(by_id)
    if len(ids) != len(set(ids)) or any(cell_id not in by_id for cell_id in ids):
        raise NightlyMatrixError("nightly matrix cell selection is invalid")
    return tuple(by_id[cell_id] for cell_id in ids)


def _selected_fixtures(selected: Sequence[str] | None) -> tuple[str, ...]:
    values = tuple(selected) if selected else FIXTURES
    if len(values) != len(set(values)):
        raise NightlyMatrixError("nightly matrix fixture selection contains duplicates")
    return tuple(fixture for fixture in FIXTURES if fixture in values)


def _optional_argument(arguments: argparse.Namespace, name: str, flag: str) -> tuple[str, ...]:
    value = getattr(arguments, name)
    return (flag, value) if value else ()


def _child_argv(root: Path, cell_id: str, fixture: str,
                arguments: argparse.Namespace, source: Path) -> tuple[str, ...]:
    command = [sys.executable, str(root / "scripts" / SCRIPT_BY_FIXTURE[fixture]),
               "--cell", cell_id, "--manifest", arguments.manifest]
    if fixture in QUICK_FIXTURES:
        command.extend(("--quick-run-id", arguments.quick_run_id))
    if fixture in PRODUCTION_FIXTURES:
        command.extend(("--source-world", str(source)))
    if fixture in GRADLE_FIXTURES:
        command.extend(_optional_argument(arguments, "gradle_dependency_cache",
                                          "--gradle-dependency-cache"))
        command.extend(_optional_argument(arguments, "gradle_distribution_zip",
                                          "--gradle-distribution-zip"))
    if fixture in LOOM_SEED_FIXTURES:
        command.extend(_optional_argument(arguments, "gradle_loom_cache", "--gradle-loom-cache"))
    return tuple(command)


def _last_json(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise NightlyMatrixError("nightly child stdout log is missing")
    for line in reversed(path.read_text(encoding="utf-8", errors="replace").splitlines()):
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            return value
    raise NightlyMatrixError("nightly child emitted no JSON result")


def plan(arguments: argparse.Namespace, *, repository_root: Path = ROOT) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    manifest_path = (root / arguments.manifest).resolve(strict=False)
    manifest = load_manifest(manifest_path)
    cells = _cells(manifest, arguments.cells)
    fixtures = _selected_fixtures(arguments.fixtures)
    source = _source_world(arguments.production_world)
    source_root = (root / "dist/qualification").resolve(strict=False)
    if not source.is_relative_to(source_root):
        raise NightlyMatrixError("coordinator production world must be below dist/qualification")
    observation = _world_observation(source)
    inventory = _world_inventory(source)
    for cell in cells:
        _validate_source_version(observation.get("minecraft_version"),
                                 str(cell["minecraft"]["version"]))
    commands = [
        {"cell": cell["id"], "fixture": fixture,
         "argv": list(_child_argv(root, str(cell["id"]), fixture, arguments, source))}
        for cell in cells for fixture in fixtures
    ]
    complete = len(cells) == len(manifest["cells"]) and fixtures == FIXTURES
    return {
        "format": 1, "mode": "EXECUTE" if arguments.execute else "DRY_RUN",
        "quick_run_id": arguments.quick_run_id, "cells": [cell["id"] for cell in cells],
        "fixtures": list(fixtures), "complete_matrix_selection": complete,
        "production_world": {"path": str(source), "inventory": inventory, **observation},
        "commands": commands,
    }


def execute(arguments: argparse.Namespace, planned: Mapping[str, Any], *,
            repository_root: Path = ROOT) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    manifest_path = (root / arguments.manifest).resolve(strict=False)
    provenance = collect_source_provenance(root, manifest_path)
    validate_gradle_dependency_cache(
        Path(arguments.gradle_dependency_cache) if arguments.gradle_dependency_cache else None, root)
    validate_gradle_distribution_zip(
        Path(arguments.gradle_distribution_zip) if arguments.gradle_distribution_zip else None, root)
    run_id = new_run_id()
    manifest = load_manifest(manifest_path)
    by_id = {cell["id"]: cell for cell in manifest["cells"]}
    results: list[dict[str, Any]] = []
    ordinal = 1
    blocked_cells: set[str] = set()
    for item in planned["commands"]:
        cell_id, fixture = str(item["cell"]), str(item["fixture"])
        if cell_id in blocked_cells:
            results.append({"cell": cell_id, "fixture": fixture, "verdict": "INCOMPLETE",
                            "reason": "earlier fixture failed"})
            continue
        paths = QualificationPaths.from_cell(root, by_id[cell_id], run_id)
        create_contained_directories(paths)
        timeout = 7_500 if fixture == "production-render" else 3_900
        record = CommandRecord(PhaseName.INPUT_PLAN, tuple(item["argv"]), root, (), timeout)
        child = execute_command(record, paths, ordinal=ordinal)
        ordinal += 1
        try:
            payload = _last_json(Path(child.stdout_log))
        except NightlyMatrixError as error:
            payload = {}
            if child.verdict is Verdict.PASS:
                child_reason = str(error)
            else:
                child_reason = child.reason
        else:
            child_reason = child.reason or payload.get("reason")
        verdict = payload.get("verdict") if child.verdict is Verdict.PASS else "FAIL"
        if verdict != "PASS":
            blocked_cells.add(cell_id)
        results.append({
            "cell": cell_id, "fixture": fixture, "verdict": verdict or "FAIL",
            "reason": child_reason, "child": payload,
            "command": {"argv": list(child.argv), "exit_code": child.return_code,
                        "started_at": child.started_at_utc, "elapsed_seconds": child.elapsed_seconds,
                        "stdout": child.stdout_log, "stderr": child.stderr_log},
        })
    all_pass = all(item["verdict"] == "PASS" for item in results)
    verdict = "PASS" if all_pass and planned["complete_matrix_selection"] else (
        "INCOMPLETE" if all_pass else "FAIL")
    report = {
        **planned, "mode": "EXECUTED", "run_id": run_id,
        "started_source_commit": provenance.commit,
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "verdict": verdict, "results": results,
    }
    evidence = root / "dist/qualification/nightly-matrix" / run_id
    write_terminal_report(
        evidence, report,
        f"# RingWorld six-cell nightly matrix\n\nVerdict: **{verdict}**\n\n"
        "Ordered isolated qualification only; no publication or deployment was performed.\n",
        stem="terminal",
    )
    return report


def main(argv: Sequence[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    try:
        planned = plan(arguments)
        result = execute(arguments, planned) if arguments.execute else planned
    except (QualificationExecutionError, OSError, ValueError) as error:
        print(f"nightly matrix rejected: {error}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    if not arguments.execute:
        return 0
    return 0 if result["verdict"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
