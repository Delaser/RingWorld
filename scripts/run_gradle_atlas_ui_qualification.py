#!/usr/bin/env python3
"""Run the existing Atlas UI/revision fixture in one pinned Gradle cell.

This is source-ABI graphical evidence for one exact Minecraft/loader cell. It
launches the real integrated client but does not claim a frozen candidate jar
or production launcher.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Callable, Mapping

from minecraft_qualification_executor import (
    QualificationExecutionError,
    QualificationLock,
    create_contained_directories,
    execute_command,
    new_run_id,
    write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, QualificationPaths, Verdict, gradle_properties
from run_gradle_creation_ui_qualification import _RUN_ID, _one_cell, _sha256
from run_minecraft_qualification import (
    ROOT,
    SourceProvenance,
    collect_source_provenance,
    load_manifest,
    stage_gradle_distribution_zip,
    validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURE = "atlas-ui-revision"
HANDSHAKE_FIXTURE = "client-handshake"
PASS_MARKER = "[atlas-ui-test] PASS"
FAIL_MARKER = "[atlas-ui-test] FAIL"
CAPTURE_PREFIXES = tuple(f"atlas-ui-{index:02d}-" for index in range(1, 12))
HANDSHAKE_MARKERS = (
    "[atlas-ui-test] client-ready",
    "[atlas-ui-test] settings-v3-mapping-4",
    "[atlas-ui-test] disconnect-clear",
)


class GradleAtlasUiError(QualificationExecutionError):
    """The pinned Atlas UI fixture could not produce trustworthy evidence."""


def _command(
    cell: Mapping[str, Any], paths: QualificationPaths, dependency_cache: Path | None = None,
) -> CommandRecord:
    loader = cell.get("loader")
    task = ":runAtlasUiClient" if loader == "fabric" else ":neoforge:runAtlasUiClient"
    if loader not in {"fabric", "neoforge"}:
        raise GradleAtlasUiError("unsupported loader")
    profile = cell.get("profile")
    timeout = profile.get("timeout_seconds") if isinstance(profile, Mapping) else None
    if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout < 1:
        raise GradleAtlasUiError("cell has no valid timeout")
    properties = tuple(f"-P{name}={value}" for name, value in gradle_properties(cell, paths))
    environment = (("GRADLE_USER_HOME", str(paths.gradle_home)),)
    if dependency_cache is not None:
        environment += (("GRADLE_RO_DEP_CACHE", str(dependency_cache)),)
    return CommandRecord(
        PhaseName.BUILD_AND_UNIT,
        (
            str(paths.repository_root / "gradlew"), "--console=plain", "--no-daemon", "--max-workers=1",
            "--project-cache-dir", str(paths.cache_directory / "gradle-project"),
            *properties, task,
        ),
        paths.repository_root,
        environment,
        timeout,
    )


def _server_ack_marker(loader: object) -> str:
    if loader == "fabric":
        return "RingWorld settings acknowledged by AtlasUiTester: 2048x128, format 3"
    if loader == "neoforge":
        return "RingWorld settings acknowledged by AtlasUiTester on NeoForge: format 3"
    raise GradleAtlasUiError("unsupported loader")


def _verify_outputs(paths: QualificationPaths, loader: object) -> tuple[Path, tuple[Path, ...]]:
    run_root = paths.run_directory / "run-atlas-ui"
    log = run_root / "logs" / "latest.log"
    if not log.is_file() or log.is_symlink():
        raise GradleAtlasUiError("Atlas UI fixture did not write a safe latest.log")
    text = log.read_text(encoding="utf-8", errors="replace")
    if PASS_MARKER not in text or FAIL_MARKER in text:
        raise GradleAtlasUiError("Atlas UI fixture did not emit its terminal PASS marker")
    previous = -1
    for marker in HANDSHAKE_MARKERS:
        position = text.find(marker)
        if position <= previous:
            raise GradleAtlasUiError(f"Atlas UI fixture did not emit ordered handshake marker: {marker}")
        previous = position
    if text.find(PASS_MARKER) <= previous:
        raise GradleAtlasUiError("Atlas UI fixture terminal PASS preceded disconnect clear")
    if text.find(_server_ack_marker(loader)) < 0 \
            or text.find(_server_ack_marker(loader)) >= text.find(HANDSHAKE_MARKERS[0]):
        raise GradleAtlasUiError("Atlas UI integrated server did not accept format-3 settings acknowledgement")
    captures: list[Path] = []
    screenshots = run_root / "screenshots"
    for prefix in CAPTURE_PREFIXES:
        matches = tuple(sorted(screenshots.glob(prefix + "*.png")))
        if len(matches) != 1 or matches[0].is_symlink() or not matches[0].is_file():
            raise GradleAtlasUiError(f"Atlas UI capture is missing or ambiguous: {prefix}")
        data = matches[0].read_bytes()
        if len(data) < 128 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise GradleAtlasUiError(f"Atlas UI capture is not a valid PNG: {matches[0].name}")
        captures.append(matches[0])
    level_data = tuple((run_root / "saves").glob("**/level.dat"))
    if len(level_data) != 1 or level_data[0].is_symlink() or not level_data[0].is_file():
        raise GradleAtlasUiError("Atlas UI fixture did not create exactly one disposable world")
    return log, tuple(captures)


def _payload(
    cell: Mapping[str, Any], paths: QualificationPaths, provenance: SourceProvenance,
    command_result: Any, log: Path, captures: tuple[Path, ...], verdict: Verdict, reason: str | None,
) -> dict[str, Any]:
    return {
        "format": 1,
        "fixture": FIXTURE,
        "evidence_kind": "source-abi-graphical",
        "cell": cell["id"],
        "loader": cell["loader"],
        "minecraft": cell["minecraft"]["version"],
        "run_id": paths.run_id,
        "verdict": verdict.value,
        "reason": reason,
        "source": {
            "commit": provenance.commit,
            "branch": provenance.branch,
            "upstream": provenance.upstream,
            "origin": provenance.origin,
            "manifest_sha256": provenance.manifest_sha256,
            "gradlew_sha256": provenance.gradle_wrapper_sha256,
            "java": provenance.java_version,
        },
        "command": {
            "argv": list(command_result.argv),
            "exit_code": command_result.return_code,
            "started_at": command_result.started_at_utc,
            "elapsed_seconds": command_result.elapsed_seconds,
            "stdout": command_result.stdout_log,
            "stderr": command_result.stderr_log,
        },
        "game_log": {"path": str(log), "sha256": _sha256(log)} if log.is_file() else None,
        "captures": [
            {"path": str(capture), "sha256": _sha256(capture), "bytes": capture.stat().st_size}
            for capture in captures
        ],
        "claims": {
            "actual_minecraft_client": verdict is Verdict.PASS,
            "exact_patch_dependencies": verdict is Verdict.PASS,
            "integrated_server": verdict is Verdict.PASS,
            "atlas_completed": verdict is Verdict.PASS,
            "revisioned_edit_verified": verdict is Verdict.PASS,
            "format3_mapping4_handshake": verdict is Verdict.PASS,
            "normal_disconnect_cleared_client_state": verdict is Verdict.PASS,
            "production_launcher": False,
            "frozen_candidate_jar": False,
            "disposable_world_created": True if verdict is Verdict.PASS else None,
        },
    }


def _handshake_payload(payload: Mapping[str, Any]) -> dict[str, Any]:
    """Derive fixture-05 evidence from the same atomic client session."""
    result = dict(payload)
    result["fixture"] = HANDSHAKE_FIXTURE
    result["derived_from_fixture"] = FIXTURE
    result["captures"] = []
    result["claims"] = {
        "actual_minecraft_client": payload["claims"]["actual_minecraft_client"],
        "exact_patch_dependencies": payload["claims"]["exact_patch_dependencies"],
        "integrated_server": payload["claims"]["integrated_server"],
        "format3_mapping4_handshake": payload["claims"]["format3_mapping4_handshake"],
        "resources_and_render_pipeline_exercised": payload["claims"]["actual_minecraft_client"],
        "normal_disconnect_cleared_client_state": payload["claims"]["normal_disconnect_cleared_client_state"],
        "production_launcher": False,
        "frozen_candidate_jar": False,
    }
    return result


def run(
    cell_id: str, *, repository_root: Path = ROOT,
    manifest_relative: str = "config/minecraft-version-matrix.json",
    run_id_factory: Callable[[], str] = new_run_id,
    provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
    command_executor: Callable[..., Any] = execute_command,
    gradle_dependency_cache: Path | None = None,
    gradle_distribution_zip: Path | None = None,
) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    manifest_path = (root / manifest_relative).resolve(strict=False)
    cell = _one_cell(load_manifest(manifest_path), cell_id)
    dependency_cache = validate_gradle_dependency_cache(gradle_dependency_cache, root)
    distribution_seed = validate_gradle_distribution_zip(gradle_distribution_zip, root)
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise GradleAtlasUiError("unsafe run ID")
    paths = QualificationPaths.from_cell(root, cell, run_id)
    provenance = provenance_provider(root, manifest_path)
    create_contained_directories(paths)
    command = _command(cell, paths, dependency_cache)
    log = paths.run_directory / "run-atlas-ui" / "logs" / "latest.log"
    captures: tuple[Path, ...] = ()
    with QualificationLock.acquire(paths.lock_path, run_id):
        stage_gradle_distribution_zip(
            distribution_seed.source if distribution_seed is not None else None, root, paths,
        )
        result = command_executor(command, paths, ordinal=1)
    verdict, reason = result.verdict, result.reason
    if verdict is Verdict.PASS:
        try:
            log, captures = _verify_outputs(paths, cell["loader"])
        except GradleAtlasUiError as error:
            verdict, reason = Verdict.FAIL, str(error)
    payload = _payload(cell, paths, provenance, result, log, captures, verdict, reason)
    evidence = paths.evidence_directory / "nightly" / "04-atlas-ui-revision"
    write_terminal_report(
        evidence,
        payload,
        f"# {cell_id} Atlas UI qualification\n\nVerdict: **{verdict.value}**\n\n"
        "This is source-ABI graphical evidence, not a production-launcher claim.\n",
        stem="terminal",
    )
    handshake_payload = _handshake_payload(payload)
    write_terminal_report(
        paths.evidence_directory / "nightly" / "05-client-handshake",
        handshake_payload,
        f"# {cell_id} client handshake qualification\n\nVerdict: **{verdict.value}**\n\n"
        "This is the handshake/disconnect tail of the same source-ABI Atlas UI session.\n",
        stem="terminal",
    )
    return payload


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True)
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    result.add_argument("--gradle-dependency-cache")
    result.add_argument("--gradle-distribution-zip")
    return result


def main(argv: list[str] | None = None) -> int:
    arguments = parser().parse_args(argv)
    try:
        result = run(
            arguments.cell,
            manifest_relative=arguments.manifest,
            gradle_dependency_cache=(Path(arguments.gradle_dependency_cache) if arguments.gradle_dependency_cache else None),
            gradle_distribution_zip=(Path(arguments.gradle_distribution_zip) if arguments.gradle_distribution_zip else None),
        )
    except (GradleAtlasUiError, QualificationExecutionError, OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
