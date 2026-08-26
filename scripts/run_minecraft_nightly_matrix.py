#!/usr/bin/env python3
"""Plan or execute the ordered 26.1.x RingWorld nightly qualification matrix."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
from typing import Any, Mapping, Sequence

from minecraft_qualification_executor import (
    QualificationExecutionError, create_contained_directories, execute_command,
    new_run_id, write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, QualificationPaths, Verdict
from run_gradle_production_lifecycle_qualification import (
    _source_world, _validate_source_version, _world_inventory, _world_observation,
)
from run_atlas_recovery_qualification import prepare_invocation
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
EXACT_CANDIDATE_FIXTURES = {"worldgen", "atlas-recovery", "multiplayer", "raid",
                            "production-lifecycle", "production-render"}
DEFAULT_MULTIPLAYER_COOLDOWN_SECONDS = 120
MAXIMUM_MULTIPLAYER_COOLDOWN_SECONDS = 600
MAXIMUM_RETAINED_ARTIFACT_BYTES = 512 * 1024 * 1024
MAXIMUM_RETAINED_ARTIFACT_FILE_BYTES = 256 * 1024 * 1024
RETAINED_ARTIFACT_SUFFIXES = {".log", ".png"}
_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")
EVIDENCE_DIRECTORY = {
    "creation-ui": "01-creation-settings-ui",
    "worldgen": "02-worldgen-seam-structures",
    "atlas-recovery": "03-atlas-prewarm-recovery",
    "atlas-ui": "04-atlas-ui-revision",
    "multiplayer": "06-seam-gameplay-multiplayer",
    "raid": "07-raid-seam",
    "map-compass": "08-map-compass-reconnect",
    "curved-objects": "10-curved-objects",
    "production-lifecycle": "11-production-lifecycle",
    "production-render": "12-production-atlas-render",
}


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
    result.add_argument("--multiplayer-cooldown-seconds", type=int,
                        default=DEFAULT_MULTIPLAYER_COOLDOWN_SECONDS)
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
    if fixture == "multiplayer":
        command.extend(("--post-prepare-settle-seconds",
                        str(arguments.multiplayer_cooldown_seconds)))
    if fixture == "raid":
        command.extend(("--phase-settle-seconds",
                        str(arguments.multiplayer_cooldown_seconds)))
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


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _child_cell_root(root: Path, cell: Mapping[str, Any],
                     payload: Mapping[str, Any]) -> Path:
    run_id = payload.get("run_id")
    qualification_root = (root / "dist/qualification").resolve(strict=False)
    if not isinstance(run_id, str) or not run_id:
        direct = payload.get("terminal_evidence")
        if not isinstance(direct, str) or not direct:
            raise NightlyMatrixError("nightly child result has no run ID")
        terminal = Path(direct).resolve(strict=False)
        if terminal.is_symlink() or not terminal.is_relative_to(qualification_root):
            raise NightlyMatrixError("nightly child terminal escapes qualification state")
        parts = terminal.relative_to(qualification_root).parts
        expected = ("ringworld", str(cell["minecraft"]["version"]),
                    str(cell["loader"]))
        if (len(parts) < 8 or parts[:3] != expected or parts[4] != str(cell["id"])
                or parts[5:7] != ("evidence", "nightly")):
            raise NightlyMatrixError("nightly child terminal path has invalid cell identity")
        run_id = parts[3]
    if _RUN_ID.fullmatch(run_id) is None:
        raise NightlyMatrixError("nightly child result has unsafe run ID")
    cell_root = (qualification_root / "ringworld" / str(cell["minecraft"]["version"])
                 / str(cell["loader"]) / run_id / str(cell["id"])).resolve(strict=False)
    if not cell_root.is_relative_to(qualification_root):
        raise NightlyMatrixError("nightly child state escapes qualification root")
    return cell_root


def _delete_disposable_tree(path: Path) -> None:
    if path.is_symlink():
        path.unlink()
        return
    if not path.exists():
        return
    if not path.is_dir():
        raise NightlyMatrixError(f"nightly disposable path is not a directory: {path}")
    with os.scandir(path) as entries:
        for entry in entries:
            child = Path(entry.path)
            if entry.is_symlink():
                child.unlink()
            elif entry.is_dir(follow_symlinks=False):
                _delete_disposable_tree(child)
            else:
                child.unlink()
    path.rmdir()


def _cleanup_disposable_child_state(root: Path, cell: Mapping[str, Any],
                                    payload: Mapping[str, Any]) -> tuple[str, ...]:
    cell_root = _child_cell_root(root, cell, payload)
    if cell_root.is_symlink():
        raise NightlyMatrixError("nightly child cell root is a symlink")
    removed = []
    for name in ("gradle-home", "cache", "build", "run"):
        target = cell_root / name
        if target.exists() or target.is_symlink():
            _delete_disposable_tree(target)
            removed.append(str(target))
    return tuple(removed)


def _cooldown(seconds: int, *, sleeper: Any = time.sleep) -> None:
    if not isinstance(seconds, int) or isinstance(seconds, bool) or not (
            0 <= seconds <= MAXIMUM_MULTIPLAYER_COOLDOWN_SECONDS):
        raise NightlyMatrixError("multiplayer cooldown is outside the bounded range")
    remaining = seconds
    while remaining:
        interval = min(5, remaining)
        sleeper(interval)
        remaining -= interval


def _terminal_paths(root: Path, cell: Mapping[str, Any], fixture: str,
                    payload: Mapping[str, Any]) -> tuple[Path, ...]:
    direct = payload.get("terminal_evidence")
    if isinstance(direct, str) and direct:
        paths = (Path(direct),)
    else:
        run_id = payload.get("run_id")
        if not isinstance(run_id, str) or not run_id:
            raise NightlyMatrixError("nightly child result has no run ID or terminal path")
        base = (root / "dist/qualification/ringworld" / str(cell["minecraft"]["version"])
                / str(cell["loader"]) / run_id / str(cell["id"]) / "evidence/nightly")
        paths = (base / EVIDENCE_DIRECTORY[fixture] / "terminal.json",)
        if fixture == "atlas-ui":
            paths += (base / "05-client-handshake/terminal.json",)
    qualification_root = (root / "dist/qualification").resolve(strict=False)
    resolved = tuple(path.resolve(strict=False) for path in paths)
    if any(not path.is_relative_to(qualification_root) for path in resolved):
        raise NightlyMatrixError("nightly terminal evidence escapes qualification state")
    return resolved


def _verify_terminals(root: Path, cell: Mapping[str, Any], fixture: str,
                      payload: Mapping[str, Any], source_commit: str,
                      candidate_hash: str, quick_hash: str) -> tuple[dict[str, str], ...]:
    records = []
    for path in _terminal_paths(root, cell, fixture, payload):
        if path.is_symlink() or not path.is_file():
            raise NightlyMatrixError(f"nightly terminal evidence is missing: {path}")
        try:
            terminal = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise NightlyMatrixError("nightly terminal evidence is not valid JSON") from error
        terminal_cell = terminal.get("cell", terminal.get("cell_id"))
        if terminal.get("verdict") != "PASS" or terminal_cell != cell["id"]:
            raise NightlyMatrixError("nightly terminal verdict/cell identity is invalid")
        qualification = terminal.get("qualification")
        source = terminal.get("source")
        if isinstance(source, Mapping):
            recorded_commit = source.get("commit")
        elif isinstance(qualification, Mapping) and isinstance(
                qualification.get("executionSourceProvenance"), Mapping):
            recorded_commit = qualification["executionSourceProvenance"].get("commit")
        else:
            recorded_commit = None
        if recorded_commit != source_commit:
            raise NightlyMatrixError("nightly terminal source commit differs from coordinator")
        if fixture in EXACT_CANDIDATE_FIXTURES:
            if isinstance(qualification, Mapping):
                recorded_candidate = qualification.get("frozenCandidateSha256")
                recorded_quick = qualification.get("quickTerminalEvidenceSha256")
            else:
                frozen = terminal.get("frozen_candidate")
                quick = terminal.get("quick_evidence")
                recorded_candidate = frozen.get("sha256") if isinstance(frozen, Mapping) else None
                recorded_quick = quick.get("sha256") if isinstance(quick, Mapping) else None
            if recorded_candidate != candidate_hash or recorded_quick != quick_hash:
                raise NightlyMatrixError("nightly exact-candidate/quick identity is invalid")
        records.append({"path": str(path), "sha256": _sha256(path)})
    return tuple(records)


def _hash_bound_artifacts(value: Any) -> tuple[tuple[str, str], ...]:
    records: list[tuple[str, str]] = []
    if isinstance(value, Mapping):
        path, digest = value.get("path"), value.get("sha256")
        if isinstance(path, str) and isinstance(digest, str):
            records.append((path, digest))
        for child in value.values():
            records.extend(_hash_bound_artifacts(child))
    elif isinstance(value, (list, tuple)):
        for child in value:
            records.extend(_hash_bound_artifacts(child))
    return tuple(records)


def _retain_terminal_artifacts(root: Path, cell: Mapping[str, Any],
                               payload: Mapping[str, Any],
                               terminals: Sequence[Mapping[str, str]]) -> tuple[dict[str, Any], ...]:
    """Retain hash-bound captures/logs before the disposable run tree is removed."""
    cell_root = _child_cell_root(root, cell, payload)
    run_root = (cell_root / "run").resolve(strict=False)
    retained_root = cell_root / "evidence/retained-artifacts"
    records: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    total = 0
    for terminal_record in terminals:
        terminal_path = Path(str(terminal_record["path"]))
        terminal = json.loads(terminal_path.read_text(encoding="utf-8"))
        for raw_path, expected_hash in _hash_bound_artifacts(terminal):
            key = (raw_path, expected_hash)
            if key in seen:
                continue
            seen.add(key)
            source = Path(raw_path).resolve(strict=False)
            if not source.is_relative_to(run_root) or source.suffix.lower() not in RETAINED_ARTIFACT_SUFFIXES:
                continue
            if source.is_symlink() or not source.is_file():
                raise NightlyMatrixError(f"hash-bound runtime artifact is missing: {source}")
            size = source.stat().st_size
            if size > MAXIMUM_RETAINED_ARTIFACT_FILE_BYTES \
                    or total + size > MAXIMUM_RETAINED_ARTIFACT_BYTES:
                raise NightlyMatrixError("hash-bound runtime artifacts exceed retention bound")
            actual_hash = _sha256(source)
            if actual_hash != expected_hash:
                raise NightlyMatrixError("hash-bound runtime artifact digest is invalid")
            retained_root.mkdir(parents=True, exist_ok=True)
            target = retained_root / f"{expected_hash}-{source.name}"
            if target.exists() or target.is_symlink():
                if target.is_symlink() or not target.is_file() or _sha256(target) != expected_hash:
                    raise NightlyMatrixError("retained runtime artifact destination is invalid")
            else:
                with source.open("rb") as incoming, target.open("xb") as outgoing:
                    for block in iter(lambda: incoming.read(1024 * 1024), b""):
                        outgoing.write(block)
            records.append({
                "source_path": str(source), "retained_path": str(target),
                "sha256": expected_hash, "bytes": size,
            })
            total += size
    return tuple(records)


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
    _cooldown(arguments.multiplayer_cooldown_seconds, sleeper=lambda _seconds: None)
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
        "multiplayer_cooldown_seconds": arguments.multiplayer_cooldown_seconds,
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
    expected: dict[str, dict[str, str]] = {}
    for cell_id in planned["cells"]:
        prepared = prepare_invocation(
            repository_root=root, manifest_path=manifest_path, cell_id=str(cell_id),
            quick_run_id=arguments.quick_run_id, run_id=new_run_id())
        expected[str(cell_id)] = {
            "frozen_candidate_sha256": prepared.candidate.sha256,
            "quick_terminal_sha256": prepared.quick_terminal_evidence.sha256,
        }
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
        cooldown_seconds = (int(planned["multiplayer_cooldown_seconds"])
                            if fixture in {"multiplayer", "raid"} else 0)
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
        terminals: tuple[dict[str, str], ...] = ()
        retained_artifacts: tuple[dict[str, Any], ...] = ()
        if verdict == "PASS":
            try:
                identity = expected[cell_id]
                terminals = _verify_terminals(
                    root, by_id[cell_id], fixture, payload, provenance.commit,
                    identity["frozen_candidate_sha256"], identity["quick_terminal_sha256"])
                retained_artifacts = _retain_terminal_artifacts(
                    root, by_id[cell_id], payload, terminals)
            except NightlyMatrixError as error:
                verdict, child_reason = "FAIL", str(error)
        removed: tuple[str, ...] = ()
        if payload.get("run_id") or payload.get("terminal_evidence"):
            try:
                removed = _cleanup_disposable_child_state(
                    root, by_id[cell_id], payload)
            except (NightlyMatrixError, OSError) as error:
                verdict, child_reason = "FAIL", f"disposable child cleanup failed: {error}"
        if verdict != "PASS":
            blocked_cells.add(cell_id)
        results.append({
            "cell": cell_id, "fixture": fixture, "verdict": verdict or "FAIL",
            "reason": child_reason, "child": payload,
            "expected_identity": expected[cell_id], "terminal_evidence": terminals,
            "retained_artifacts": list(retained_artifacts),
            "discarded_disposable_paths": list(removed),
            "pre_fixture_cooldown_seconds": 0,
            "post_prepare_settle_seconds": (cooldown_seconds
                                             if fixture == "multiplayer" else 0),
            "phase_settle_seconds": cooldown_seconds if fixture == "raid" else 0,
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
