#!/usr/bin/env python3
"""Run the two-phase frozen-candidate seam-raid fixture for one matrix cell."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from typing import Any, Mapping, Sequence

from minecraft_qualification_executor import (
    QualificationExecutionError, QualificationLock, create_contained_directories,
    execute_command, new_run_id, write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, Verdict
from run_atlas_recovery_qualification import _manifest_path, prepare_invocation
from run_minecraft_qualification import (
    ROOT, stage_gradle_distribution_zip, validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)
from run_gradle_multiplayer_qualification import (
    _base_argv, _configure_rcon, _executed_record, _graceful_rcon_stop,
    _process_record, _read_log, _sha256, _stage_loom_seed, _start, _terminate,
    _timeout, _validated_loom_seed, _wait_marker,
)


FIXTURE = "frozen-raid-seam"
EVIDENCE_SUBDIRECTORY = "nightly/07-raid-seam"
DEFAULT_PHASE_SETTLE_SECONDS = 120
MAXIMUM_PHASE_SETTLE_SECONDS = 600
_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")


class GradleRaidError(QualificationExecutionError):
    """Frozen raid evidence is unsafe or incomplete."""


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True)
    result.add_argument("--quick-run-id", required=True)
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    result.add_argument("--gradle-dependency-cache")
    result.add_argument("--gradle-distribution-zip")
    result.add_argument("--gradle-loom-cache")
    result.add_argument(
        "--phase-settle-seconds", type=int, default=DEFAULT_PHASE_SETTLE_SECONDS,
        help="bounded host settle interval before each raid runtime phase",
    )
    return result


def _phase_settle(seconds: int, *, sleeper: Any = time.sleep) -> None:
    if not isinstance(seconds, int) or isinstance(seconds, bool) or not (
            0 <= seconds <= MAXIMUM_PHASE_SETTLE_SECONDS):
        raise GradleRaidError("raid phase settle interval is outside the bounded range")
    remaining = seconds
    while remaining:
        interval = min(5, remaining)
        sleeper(interval)
        remaining -= interval


def _tasks(loader: str) -> Mapping[str, str]:
    prefix = ":" if loader == "fabric" else ":neoforge:"
    if loader not in {"fabric", "neoforge"}:
        raise GradleRaidError("unsupported loader")
    prepare = "prepareRaidSeamTestWorld" if loader == "fabric" else "prepareNeoForgeRaidSeamTestWorld"
    return {
        "prepare": prefix + prepare,
        "assets": prefix + "downloadAssets",
        "arm": prefix + "runRaidSeamArmServer",
        "reload": prefix + "runRaidSeamReloadServer",
        "client_a": prefix + "runRaidSeamClientA",
        "client_b": prefix + "runRaidSeamClientB",
    }


def _record(prepared: Any, args: Sequence[str], timeout: int,
            dependency_cache: Path | None) -> CommandRecord:
    port = int(prepared.cell["profile"]["server_port"])
    environment = (("GRADLE_USER_HOME", str(prepared.paths.gradle_home)),)
    if dependency_cache is not None:
        environment += (("GRADLE_RO_DEP_CACHE", str(dependency_cache)),)
    argv = _base_argv(prepared) + (f"-PringQualificationRaidSeamPort={port}",) + tuple(args)
    return CommandRecord(PhaseName.DEDICATED_SMOKE, argv, prepared.paths.repository_root,
                         environment, timeout)


def _verify_installed(prepared: Any) -> tuple[dict[str, str], ...]:
    root = prepared.paths.run_directory / "run-raid-seam"
    records = []
    for role in ("server", "client-a", "client-b"):
        mods = root / role / "mods"
        expected = mods / "ringworld-qualification.jar"
        jars = tuple(sorted(mods.glob("*.jar")))
        if jars != (expected,) or expected.is_symlink() or not expected.is_file():
            raise GradleRaidError(f"{role} must contain only the retained RingWorld jar")
        digest = _sha256(expected)
        if digest != prepared.candidate.sha256:
            raise GradleRaidError(f"{role} frozen candidate hash changed")
        records.append({"role": role, "path": str(expected), "sha256": digest})
    return tuple(records)


def _copy_game_logs(prepared: Any, phase: str) -> tuple[dict[str, str], ...]:
    runtime = prepared.paths.run_directory / "run-raid-seam"
    copied = []
    for role in ("server", "client-a", "client-b"):
        source = runtime / role / "logs/latest.log"
        if source.is_symlink() or not source.is_file():
            raise GradleRaidError(f"{phase} {role} game log is missing")
        target = prepared.paths.logs_directory / f"{phase}-{role}-game.log"
        shutil.copy2(source, target)
        copied.append({"phase": phase, "role": role, "path": str(target), "sha256": _sha256(target)})
    return tuple(copied)


def _verify_phase(prepared: Any, phase: str, logs: Sequence[dict[str, str]]) -> str:
    by_role = {item["role"]: Path(item["path"]) for item in logs}
    server = _read_log(by_role["server"])
    version = str(prepared.cell["minecraft"]["version"])
    if f"Starting minecraft server version {version}" not in server or "[raid-seam] FAIL" in server:
        raise GradleRaidError(f"{phase} server identity or failure marker is invalid")
    marker = "[raid-seam] arm-save-ready=true" if phase == "arm" else "[raid-seam] PASS"
    if marker not in server:
        raise GradleRaidError(f"{phase} terminal marker is missing")
    if phase == "arm" and ("saved=true" not in server or "bossbarA=true" not in server or "bossbarB=true" not in server):
        raise GradleRaidError("arm persistence/bossbar evidence is incomplete")
    client_marker = (f"Loading Minecraft {version} " if prepared.cell["loader"] == "fabric"
                     else f"Minecraft {version} (minecraft)")
    for role in ("client-a", "client-b"):
        text = _read_log(by_role[role])
        if client_marker not in text or "client world fully loaded" not in text:
            raise GradleRaidError(f"{phase} {role} patch/startup evidence is incomplete")
    return next(line for line in server.splitlines() if marker in line)


def _run_phase(prepared: Any, tasks: Mapping[str, str], phase: str,
               dependency_cache: Path | None, timeout: int,
               rcon_port: int, rcon_password: str,
               ordinal: int) -> tuple[list[dict[str, Any]], tuple[dict[str, str], ...], str]:
    outputs = prepared.paths.logs_directory
    prepare_task = tasks["prepare"]
    server_record = _record(prepared, (tasks[phase], "-x", prepare_task), timeout, dependency_cache)
    client_a_record = _record(prepared, (tasks["client_a"],), timeout, dependency_cache)
    client_b_record = _record(prepared, (tasks["client_b"],), timeout, dependency_cache)
    specs = (
        ("server", server_record, outputs / f"{ordinal:02d}-{phase}-server-process.log"),
        ("client-a", client_a_record, outputs / f"{ordinal + 1:02d}-{phase}-client-a-process.log"),
        ("client-b", client_b_record, outputs / f"{ordinal + 2:02d}-{phase}-client-b-process.log"),
    )
    running: dict[str, tuple[subprocess.Popen[bytes], Any, Path, str, float]] = {}
    try:
        for index, (name, record, output) in enumerate(specs):
            process, stream = _start(record, output)
            running[name] = (process, stream, output, datetime.now(timezone.utc).isoformat(), time.monotonic())
            if index == 0:
                server_log = prepared.paths.run_directory / "run-raid-seam/server/logs/latest.log"
                _wait_marker(process, server_log, "Done (", min(timeout, 300))
        server = running["server"][0]
        server_log = prepared.paths.run_directory / "run-raid-seam/server/logs/latest.log"
        marker = "[raid-seam] arm-save-ready=true" if phase == "arm" else "[raid-seam] PASS"
        _wait_marker(server, server_log, marker, timeout)
        if phase == "reload":
            _graceful_rcon_stop(rcon_port, rcon_password)
        try:
            server.wait(timeout=60)
        except subprocess.TimeoutExpired as error:
            raise GradleRaidError(f"{phase} server did not stop normally") from error
        if server.returncode != 0:
            raise GradleRaidError(f"{phase} server exited {server.returncode}")
        logs = _copy_game_logs(prepared, phase)
        terminal = _verify_phase(prepared, phase, logs)
    finally:
        for process, _stream, _output, _started, _monotonic in running.values():
            _terminate(process)
        for _process, stream, _output, _started, _monotonic in running.values():
            stream.close()
    records = [_process_record(f"{phase}-{name}", process, output, started,
                               time.monotonic() - monotonic)
               for name, (process, _stream, output, started, monotonic) in running.items()]
    return records, logs, terminal


def _execute(prepared: Any, dependency_cache: Path | None, distribution_zip: Path | None,
             loom_seed: Sequence[Path], phase_settle_seconds: int) -> dict[str, Any]:
    paths, cell = prepared.paths, prepared.cell
    timeout, tasks = _timeout(cell), _tasks(str(cell["loader"]))
    create_contained_directories(paths)
    stage_gradle_distribution_zip(distribution_zip, paths.repository_root, paths)
    _stage_loom_seed(loom_seed, paths.gradle_home, str(cell["minecraft"]["version"]))
    eula = paths.run_directory / "run-raid-seam/server/eula.txt"
    eula.parent.mkdir(parents=True, exist_ok=True)
    eula.write_text("# Disposable qualification runtime only.\neula=true\n", encoding="utf-8")
    commands: list[dict[str, Any]] = []
    prepare = execute_command(_record(prepared, (tasks["prepare"],), timeout, dependency_cache), paths, ordinal=1)
    commands.append(_executed_record("prepare", prepare))
    if prepare.verdict is not Verdict.PASS:
        raise GradleRaidError("fixture preparation failed")
    installed = _verify_installed(prepared)
    assets = execute_command(_record(prepared, (tasks["assets"],), timeout, dependency_cache), paths, ordinal=2)
    commands.append(_executed_record("assets", assets))
    if assets.verdict is not Verdict.PASS:
        raise GradleRaidError("serial asset warmup failed")
    port = int(cell["profile"]["server_port"])
    rcon_port, password = port + 1000, f"ringworld-{paths.run_id[-12:]}"
    _configure_rcon(paths.run_directory / "run-raid-seam/server", rcon_port, password)
    _phase_settle(phase_settle_seconds)
    arm_commands, arm_logs, arm_terminal = _run_phase(
        prepared, tasks, "arm", dependency_cache, timeout, rcon_port, password, 3)
    commands.extend(arm_commands)
    _configure_rcon(paths.run_directory / "run-raid-seam/server", rcon_port, password)
    _phase_settle(phase_settle_seconds)
    reload_commands, reload_logs, reload_terminal = _run_phase(
        prepared, tasks, "reload", dependency_cache, timeout, rcon_port, password, 6)
    commands.extend(reload_commands)
    world = paths.run_directory / "run-raid-seam/server/world/level.dat"
    if world.is_symlink() or not world.is_file():
        raise GradleRaidError("persistent raid world is missing level.dat")
    return {
        "commands": commands,
        "installed_candidates": installed,
        "game_logs": (*arm_logs, *reload_logs),
        "raid_summary": {"arm": arm_terminal, "reload": reload_terminal,
                         "world_level_dat_sha256": _sha256(world)},
    }


def run(arguments: argparse.Namespace, *, repository_root: Path = ROOT) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    dependency_cache = validate_gradle_dependency_cache(
        Path(arguments.gradle_dependency_cache) if arguments.gradle_dependency_cache else None, root)
    distribution = validate_gradle_distribution_zip(
        Path(arguments.gradle_distribution_zip) if arguments.gradle_distribution_zip else None, root)
    run_id = new_run_id()
    if _RUN_ID.fullmatch(run_id) is None:
        raise GradleRaidError("unsafe run ID")
    prepared = prepare_invocation(
        repository_root=root, manifest_path=_manifest_path(root, arguments.manifest),
        cell_id=arguments.cell, quick_run_id=arguments.quick_run_id, run_id=run_id)
    loom_seed = _validated_loom_seed(
        Path(arguments.gradle_loom_cache) if arguments.gradle_loom_cache else None,
        root, str(prepared.cell["minecraft"]["version"]))
    _phase_settle(arguments.phase_settle_seconds, sleeper=lambda _seconds: None)
    with QualificationLock.acquire(prepared.paths.lock_path, run_id):
        try:
            details = _execute(prepared, dependency_cache,
                               distribution.source if distribution else None, loom_seed,
                               arguments.phase_settle_seconds)
            verdict, reason = Verdict.PASS, None
        except (QualificationExecutionError, OSError, ValueError) as error:
            details, verdict, reason = {}, Verdict.FAIL, str(error)
        payload = {
            "format": 1, "fixture": FIXTURE, "cell": prepared.cell["id"],
            "loader": prepared.cell["loader"], "minecraft": prepared.cell["minecraft"]["version"],
            "run_id": run_id, "verdict": verdict.value, "reason": reason,
            "phase_settle_seconds": arguments.phase_settle_seconds,
            "source": prepared.source_provenance,
            "quick_evidence": {"path": str(prepared.quick_terminal_evidence.path),
                               "sha256": prepared.quick_terminal_evidence.sha256},
            "frozen_candidate": {"path": str(prepared.candidate.path),
                                 "sha256": prepared.candidate.sha256,
                                 "minecraft_range": prepared.candidate.declared_target_range},
            **details,
            "claims": {"dedicated_server": verdict is Verdict.PASS,
                       "two_real_clients": verdict is Verdict.PASS,
                       "two_phase_persistence": verdict is Verdict.PASS,
                       "frozen_candidate_jar": verdict is Verdict.PASS,
                       "production_launcher": False},
        }
        write_terminal_report(
            prepared.paths.evidence_directory / EVIDENCE_SUBDIRECTORY,
            payload,
            f"# {prepared.cell['id']} frozen raid qualification\n\n"
            f"Verdict: **{verdict.value}**\n\n"
            "Exact retained-jar, two-phase dedicated-server and two-client raid evidence. "
            "This is not a packaged production-launcher claim.\n",
            stem="terminal",
        )
    return payload


def main(argv: Sequence[str] | None = None) -> int:
    try:
        result = run(parser().parse_args(argv))
    except (QualificationExecutionError, OSError, ValueError) as error:
        print(f"raid qualification rejected: {error}", file=sys.stderr)
        return 2
    print(json.dumps({key: result.get(key) for key in
                      ("cell", "fixture", "loader", "minecraft", "reason", "run_id", "verdict")},
                     sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
