#!/usr/bin/env python3
"""Run the frozen-candidate production lifecycle fixture for one matrix cell."""

from __future__ import annotations

import argparse
from dataclasses import asdict
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import sys
from typing import Any, Mapping, Sequence

from minecraft_atlas_recovery_persistence import _NbtReader, parse_persisted_ring_settings
from minecraft_qualification_executor import (
    QualificationExecutionError, QualificationLock, create_contained_directories,
    execute_command, new_run_id, write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, Verdict
from run_atlas_recovery_qualification import _manifest_path, prepare_invocation
from run_gradle_multiplayer_qualification import (
    _base_argv, _executed_record, _sha256, _stage_loom_seed, _timeout,
    _validated_loom_seed,
)
from run_minecraft_qualification import (
    ROOT, stage_gradle_distribution_zip, validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURE = "frozen-production-lifecycle"
EVIDENCE_SUBDIRECTORY = "nightly/11-production-lifecycle"
DESTINATION = "RingWorld Qualified Lifecycle"
MAX_WORLD_FILES = 100_000
MAX_WORLD_BYTES = 8 * 1024 * 1024 * 1024
MAX_ATLAS_COMPRESSED_BYTES = 32 * 1024 * 1024
MAX_ATLAS_UNCOMPRESSED_BYTES = 64 * 1024 * 1024
MAX_LEVEL_UNCOMPRESSED_BYTES = 16 * 1024 * 1024
ATLAS_MAGIC = 0x52574154
ATLAS_VERSION = 6


class GradleProductionLifecycleError(QualificationExecutionError):
    """Production lifecycle evidence is unsafe or incomplete."""


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True)
    result.add_argument("--quick-run-id", required=True)
    result.add_argument("--source-world", required=True)
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    result.add_argument("--gradle-dependency-cache")
    result.add_argument("--gradle-distribution-zip")
    result.add_argument("--gradle-loom-cache")
    return result


def _tasks(loader: object) -> Mapping[str, str]:
    if loader == "fabric":
        return {
            "assets": ":downloadAssets",
            "run": ":runProductionLifecycleClient",
            "prepare": ":prepareCopiedProductionLifecycleWorld",
        }
    if loader == "neoforge":
        return {
            "assets": ":neoforge:downloadAssets",
            "run": ":neoforge:runProductionLifecycleClient",
            "prepare": ":neoforge:prepareCopiedNeoForgeProductionLifecycleWorld",
        }
    raise GradleProductionLifecycleError("unsupported loader")


def _record(prepared: Any, arguments: Sequence[str], timeout: int,
            dependency_cache: Path | None) -> CommandRecord:
    environment = (("GRADLE_USER_HOME", str(prepared.paths.gradle_home)),)
    if dependency_cache is not None:
        environment += (("GRADLE_RO_DEP_CACHE", str(dependency_cache)),)
    return CommandRecord(
        PhaseName.DEDICATED_SMOKE,
        _base_argv(prepared) + tuple(arguments),
        prepared.paths.repository_root,
        environment,
        timeout,
    )


def _world_inventory(root: Path) -> dict[str, Any]:
    if root.is_symlink() or not root.is_dir():
        raise GradleProductionLifecycleError("production source must be a regular directory")
    digest = hashlib.sha256()
    files = 0
    total = 0
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        relative = path.relative_to(root).as_posix()
        if path.is_symlink():
            raise GradleProductionLifecycleError(f"production world contains a symlink: {relative}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise GradleProductionLifecycleError(f"production world contains a special file: {relative}")
        files += 1
        size = path.stat().st_size
        total += size
        if files > MAX_WORLD_FILES or total > MAX_WORLD_BYTES:
            raise GradleProductionLifecycleError("production world exceeds the bounded inventory limits")
        file_hash = _sha256(path)
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(str(size).encode("ascii"))
        digest.update(b"\0")
        digest.update(file_hash.encode("ascii"))
        digest.update(b"\n")
    if files == 0:
        raise GradleProductionLifecycleError("production world is empty")
    return {"files": files, "bytes": total, "sha256": digest.hexdigest()}


def _read_bounded_gzip(path: Path) -> bytes:
    raw = path.read_bytes()
    if not raw or len(raw) > MAX_ATLAS_COMPRESSED_BYTES:
        raise GradleProductionLifecycleError("production Atlas compressed size is invalid")
    try:
        with gzip.open(path, "rb") as stream:
            data = stream.read(MAX_ATLAS_UNCOMPRESSED_BYTES + 1)
    except (OSError, EOFError) as error:
        raise GradleProductionLifecycleError("production Atlas is not valid gzip") from error
    if len(data) > MAX_ATLAS_UNCOMPRESSED_BYTES:
        raise GradleProductionLifecycleError("production Atlas exceeds its bounded size")
    return data


def _atlas_observation(path: Path, width: int, circumference: int) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise GradleProductionLifecycleError("production Atlas is missing")
    data = _read_bounded_gzip(path)
    header_format = ">IIQIIIIIQ"
    header_size = struct.calcsize(header_format)
    if len(data) < header_size:
        raise GradleProductionLifecycleError("production Atlas header is truncated")
    magic, version, world_hash, actual_width, actual_circumference, step, columns, rows, revision = \
        struct.unpack(header_format, data[:header_size])
    if magic != ATLAS_MAGIC or version != ATLAS_VERSION:
        raise GradleProductionLifecycleError("production Atlas magic/version is invalid")
    if (actual_width, actual_circumference) != (width, circumference):
        raise GradleProductionLifecycleError("production Atlas geometry does not match saved settings")
    if step <= 0 or 16 % step != 0 or columns != circumference // step or rows != width // step:
        raise GradleProductionLifecycleError("production Atlas sampling geometry is invalid")
    cells = columns * rows
    if len(data) != header_size + cells * 7:
        raise GradleProductionLifecycleError("production Atlas payload size is invalid")
    flags = data[header_size::7]
    if len(flags) != cells or any(flag not in (0, 1) for flag in flags):
        raise GradleProductionLifecycleError("production Atlas presence map is invalid")
    present = sum(flag == 1 for flag in flags)
    if present != cells:
        raise GradleProductionLifecycleError(
            f"production Atlas must be complete ({present}/{cells} cells present)")
    return {
        "path": str(path), "sha256": _sha256(path), "format": version,
        "world_hash": str(world_hash), "width": width, "circumference": circumference,
        "sample_step": step, "columns": columns, "rows": rows, "revision": revision,
        "present_cells": present, "total_cells": cells,
    }


def _world_observation(root: Path) -> dict[str, Any]:
    level = root / "level.dat"
    settings_path = root / "dimensions/minecraft/overworld/data/ringworld/settings.dat"
    atlas_path = root / "dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz"
    for label, path in (("level.dat", level), ("saved settings", settings_path)):
        if path.is_symlink() or not path.is_file():
            raise GradleProductionLifecycleError(f"production world {label} is missing")
    try:
        with gzip.open(level, "rb") as stream:
            level_data = stream.read(MAX_LEVEL_UNCOMPRESSED_BYTES + 1)
    except (OSError, EOFError) as error:
        raise GradleProductionLifecycleError("production level.dat is not valid gzip") from error
    if len(level_data) > MAX_LEVEL_UNCOMPRESSED_BYTES:
        raise GradleProductionLifecycleError("production level.dat exceeds its bounded size")
    reader = _NbtReader(level_data)
    if reader.read(1)[0] != 10:
        raise GradleProductionLifecycleError("production level.dat root is invalid")
    reader.string()
    level_root = reader.payload(10)
    values = level_root.get("Data")
    version_record = values.get("Version") if isinstance(values, dict) else None
    version_name = version_record.get("Name") if isinstance(version_record, dict) else None
    if not isinstance(version_name, str) or not version_name:
        raise GradleProductionLifecycleError("production level.dat has no saved version name")
    settings = parse_persisted_ring_settings(settings_path.read_bytes(), settings_path)
    if (settings.circumference_blocks, settings.width_blocks) != (16_384, 256):
        raise GradleProductionLifecycleError("production source must use the 16384x256 layout")
    if settings.format_version != 3 or settings.terrain_noise_mapping != 4:
        raise GradleProductionLifecycleError("production source must use format 3 and mapping 4")
    atlas = _atlas_observation(atlas_path, settings.width_blocks, settings.circumference_blocks)
    return {
        "level_dat_sha256": _sha256(level),
        "minecraft_version": version_name,
        "settings": {**asdict(settings), "settings_path": str(settings.settings_path)},
        "atlas": atlas,
    }


def _source_world(value: str) -> Path:
    raw = Path(value).expanduser()
    if not raw.is_absolute() or raw.is_symlink():
        raise GradleProductionLifecycleError("--source-world must be an absolute non-symlink directory")
    try:
        resolved = raw.resolve(strict=True)
    except OSError as error:
        raise GradleProductionLifecycleError("--source-world does not exist") from error
    if not resolved.is_dir():
        raise GradleProductionLifecycleError("--source-world is not a directory")
    return resolved


def _validate_source_version(saved_version: object, target_version: object) -> None:
    from minecraft_qualification_ranges import parse_minecraft_version
    from minecraft_support_contract import LEGACY_CONTRACT
    try:
        saved, target = parse_minecraft_version(saved_version), parse_minecraft_version(target_version)
        if min(saved, target) < parse_minecraft_version(LEGACY_CONTRACT.oldest):
            raise ValueError("older than the source compatibility floor")
    except ValueError as error:
        raise GradleProductionLifecycleError("production source/target version is outside stable supported-era releases") from error
    if saved > target:
        raise GradleProductionLifecycleError(
            f"production source {saved_version} cannot be opened as older target {target_version}")


def _prepare_world(prepared: Any, source: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    before = _world_inventory(source)
    source_observation = _world_observation(source)
    _validate_source_version(source_observation.get("minecraft_version"),
                             str(prepared.cell["minecraft"]["version"]))
    runtime = prepared.paths.run_directory / "run-production-lifecycle"
    destination = runtime / "saves" / DESTINATION
    if destination.exists() or destination.is_symlink():
        raise GradleProductionLifecycleError("production lifecycle destination already exists")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, destination, symlinks=True)
    after = _world_inventory(source)
    copied = _world_inventory(destination)
    if before != after or before != copied:
        raise GradleProductionLifecycleError("production source changed or copied world differs")
    mods = runtime / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    installed = mods / "ringworld-qualification.jar"
    shutil.copy2(prepared.candidate.path, installed)
    if installed.is_symlink() or _sha256(installed) != prepared.candidate.sha256:
        raise GradleProductionLifecycleError("installed frozen candidate hash changed")
    (runtime / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\n", encoding="utf-8")
    if prepared.cell["loader"] == "neoforge":
        fml = runtime / "config/fml.toml"
        fml.parent.mkdir(parents=True, exist_ok=True)
        fml.write_text("# Automated lifecycle fixture.\nearlyWindowControl = false\n", encoding="utf-8")
    return ({"path": str(source), "inventory": before, **source_observation},
            {"path": str(destination), "baseline_inventory": copied,
             "installed_candidate": {"path": str(installed), "sha256": _sha256(installed)}})


def _verify_outputs(prepared: Any, destination: Path) -> tuple[Path, dict[str, Any]]:
    log = prepared.paths.run_directory / "run-production-lifecycle/logs/latest.log"
    if log.is_symlink() or not log.is_file():
        raise GradleProductionLifecycleError("production lifecycle client log is missing")
    text = log.read_text(encoding="utf-8", errors="replace")
    if "[production-lifecycle] result=false" in text:
        raise GradleProductionLifecycleError("production lifecycle emitted FAIL")
    markers = (
        "[production-lifecycle] baseline geometry=16384x256",
        "[production-lifecycle] nether RingWorld-active=false",
        "[production-lifecycle] intermediate overworld return restored baseline",
        "[production-lifecycle] end RingWorld-active=false",
        "[production-lifecycle] server-transfer result=true sequence=nether,overworld,end,overworld",
        "[production-lifecycle] overworld return restored baseline; requesting normal save-and-disconnect",
        "client state cleared=true",
        "[production-lifecycle] result=true reopened geometry=16384x256",
    )
    previous = -1
    for marker in markers:
        position = text.find(marker)
        if position <= previous:
            raise GradleProductionLifecycleError(f"missing or unordered lifecycle marker: {marker}")
        previous = position
    version = str(prepared.cell["minecraft"]["version"])
    patch_marker = (f"Loading Minecraft {version} " if prepared.cell["loader"] == "fabric"
                    else f"Minecraft {version} (minecraft)")
    if patch_marker not in text:
        raise GradleProductionLifecycleError("production lifecycle client patch marker is missing")
    final_world = _world_observation(destination)
    return log, final_world


def _execute(prepared: Any, source_world: Path, dependency_cache: Path | None,
             distribution_zip: Path | None, loom_seed: Sequence[Path]) -> dict[str, Any]:
    paths, cell = prepared.paths, prepared.cell
    create_contained_directories(paths)
    stage_gradle_distribution_zip(distribution_zip, paths.repository_root, paths)
    _stage_loom_seed(loom_seed, paths.gradle_home, str(cell["minecraft"]["version"]),
                     str(cell["loader"]))
    source, destination_record = _prepare_world(prepared, source_world)
    destination = Path(destination_record["path"])
    tasks = _tasks(cell["loader"])
    timeout = _timeout(cell)
    commands: list[dict[str, Any]] = []
    assets = execute_command(_record(prepared, (tasks["assets"],), timeout, dependency_cache), paths, ordinal=1)
    commands.append(_executed_record("assets", assets))
    if assets.verdict is not Verdict.PASS:
        raise GradleProductionLifecycleError("serial asset warmup failed")
    run_args = (
        f"-PringProductionLifecycleDestination={DESTINATION}",
        f"-PringNeoForgeProductionLifecycleDestination={DESTINATION}",
        tasks["run"], "-x", tasks["prepare"],
    )
    result = execute_command(_record(prepared, run_args, timeout, dependency_cache), paths, ordinal=2)
    commands.append(_executed_record("lifecycle", result))
    if result.verdict is not Verdict.PASS:
        raise GradleProductionLifecycleError("production lifecycle Gradle run failed")
    log, final_world = _verify_outputs(prepared, destination)
    return {
        "commands": commands,
        "source_world": source,
        "copied_world": {**destination_record, "final": final_world},
        "game_log": {"path": str(log), "sha256": _sha256(log)},
    }


def run(arguments: argparse.Namespace, *, repository_root: Path = ROOT) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    dependency_cache = validate_gradle_dependency_cache(
        Path(arguments.gradle_dependency_cache) if arguments.gradle_dependency_cache else None, root)
    distribution = validate_gradle_distribution_zip(
        Path(arguments.gradle_distribution_zip) if arguments.gradle_distribution_zip else None, root)
    source_world = _source_world(arguments.source_world)
    run_id = new_run_id()
    prepared = prepare_invocation(
        repository_root=root, manifest_path=_manifest_path(root, arguments.manifest),
        cell_id=arguments.cell, quick_run_id=arguments.quick_run_id, run_id=run_id)
    loom_seed = _validated_loom_seed(
        Path(arguments.gradle_loom_cache) if arguments.gradle_loom_cache else None,
        root, str(prepared.cell["minecraft"]["version"]))
    with QualificationLock.acquire(prepared.paths.lock_path, run_id):
        try:
            details = _execute(prepared, source_world, dependency_cache,
                               distribution.source if distribution else None, loom_seed)
            verdict, reason = Verdict.PASS, None
        except (QualificationExecutionError, OSError, ValueError) as error:
            details, verdict, reason = {}, Verdict.FAIL, str(error)
        payload = {
            "format": 1, "fixture": FIXTURE, "cell": prepared.cell["id"],
            "loader": prepared.cell["loader"], "minecraft": prepared.cell["minecraft"]["version"],
            "run_id": run_id, "verdict": verdict.value, "reason": reason,
            "source": prepared.source_provenance,
            "quick_evidence": {"path": str(prepared.quick_terminal_evidence.path),
                               "sha256": prepared.quick_terminal_evidence.sha256},
            "frozen_candidate": {"path": str(prepared.candidate.path),
                                 "sha256": prepared.candidate.sha256,
                                 "minecraft_range": prepared.candidate.declared_target_range},
            **details,
            "claims": {
                "actual_minecraft_client": verdict is Verdict.PASS,
                "exact_patch_dependencies": verdict is Verdict.PASS,
                "frozen_candidate_jar": verdict is Verdict.PASS,
                "production_16384x256_world": verdict is Verdict.PASS,
                "dimension_transfer_save_disconnect_reopen": verdict is Verdict.PASS,
                "production_launcher": False,
            },
        }
        write_terminal_report(
            prepared.paths.evidence_directory / EVIDENCE_SUBDIRECTORY,
            payload,
            f"# {prepared.cell['id']} frozen production lifecycle qualification\n\n"
            f"Verdict: **{verdict.value}**\n\n"
            "Exact retained-jar production-world lifecycle evidence. "
            "This is not a packaged production-launcher claim.\n",
            stem="terminal",
        )
    return payload


def main(argv: Sequence[str] | None = None) -> int:
    try:
        result = run(parser().parse_args(argv))
    except (QualificationExecutionError, OSError, ValueError) as error:
        print(f"production lifecycle qualification rejected: {error}", file=sys.stderr)
        return 2
    print(json.dumps({key: result.get(key) for key in
                      ("cell", "fixture", "loader", "minecraft", "reason", "run_id", "verdict")},
                     sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
