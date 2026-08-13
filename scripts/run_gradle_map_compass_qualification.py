#!/usr/bin/env python3
"""Run the existing map/compass persistence fixture in one pinned Gradle cell.

This is source-ABI graphical/gameplay evidence for one exact Minecraft/loader
cell. It does not claim a production launcher or frozen candidate jar.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Callable, Mapping

from minecraft_qualification_executor import (
    QualificationExecutionError, QualificationLock, create_contained_directories,
    execute_command, new_run_id, write_terminal_report,
)
from minecraft_qualification_model import CommandRecord, PhaseName, QualificationPaths, Verdict, gradle_properties
from run_gradle_creation_ui_qualification import _RUN_ID, _one_cell, _sha256
from run_minecraft_qualification import (
    ROOT, SourceProvenance, collect_source_provenance, load_manifest,
    stage_gradle_distribution_zip, validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURE = "map-compass-reconnect"
PASS_MARKER = "[map-compass-capture] PASS:"
FAIL_MARKER = "[map-compass-capture] FAIL:"
ORDERED_MARKERS = (
    "[map-compass-capture] requesting normal save-and-disconnect",
    "[map-compass-capture] reopening 'RingWorld Map Compass Capture' after clean client teardown",
    "[map-compass-capture] reopened targets spawn=true lodestone=true recovery=true stateCleared=true",
    PASS_MARKER,
)
CAPTURE_PREFIXES = (
    "ringworld-map-scale", "ringworld-map-banner-removed",
    "ringworld-map-high-to-low", "ringworld-map-low-to-high",
    "ringworld-compass-high-to-low", "ringworld-compass-low-to-high",
    "ringworld-map-reopened", "ringworld-compass-reopened",
)


class GradleMapCompassError(QualificationExecutionError):
    """The pinned map/compass fixture could not produce trustworthy evidence."""


def _command(cell: Mapping[str, Any], paths: QualificationPaths,
             dependency_cache: Path | None = None) -> CommandRecord:
    loader = cell.get("loader")
    task = ":runMapCompassCaptureClient" if loader == "fabric" else ":neoforge:runMapCompassCaptureClient"
    if loader not in {"fabric", "neoforge"}:
        raise GradleMapCompassError("unsupported loader")
    profile = cell.get("profile")
    timeout = profile.get("timeout_seconds") if isinstance(profile, Mapping) else None
    if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout < 1:
        raise GradleMapCompassError("cell has no valid timeout")
    properties = tuple(f"-P{name}={value}" for name, value in gradle_properties(cell, paths))
    environment = (("GRADLE_USER_HOME", str(paths.gradle_home)),)
    if dependency_cache is not None:
        environment += (("GRADLE_RO_DEP_CACHE", str(dependency_cache)),)
    return CommandRecord(
        PhaseName.BUILD_AND_UNIT,
        (str(paths.repository_root / "gradlew"), "--console=plain", "--no-daemon", "--max-workers=1",
         "--project-cache-dir", str(paths.cache_directory / "gradle-project"), *properties, task),
        paths.repository_root, environment, timeout,
    )


def _acknowledgement(loader: object) -> str:
    if loader == "fabric":
        return "RingWorld settings acknowledged by MapCompassTester: 2048x416, format 3"
    if loader == "neoforge":
        return "RingWorld settings acknowledged by MapCompassTester on NeoForge: format 3"
    raise GradleMapCompassError("unsupported loader")


def _verify_outputs(paths: QualificationPaths, loader: object) -> tuple[Path, tuple[Path, ...], Path]:
    run_root = paths.run_directory / "run-map-compass-capture"
    log = run_root / "logs/latest.log"
    if not log.is_file() or log.is_symlink():
        raise GradleMapCompassError("map/compass fixture did not write a safe latest.log")
    text = log.read_text(encoding="utf-8", errors="replace")
    if FAIL_MARKER in text:
        raise GradleMapCompassError("map/compass fixture emitted FAIL")
    previous = -1
    for marker in ORDERED_MARKERS:
        position = text.find(marker)
        if position <= previous:
            raise GradleMapCompassError(f"missing or unordered map/compass marker: {marker}")
        previous = position
    acknowledgement = _acknowledgement(loader)
    first_ack = text.find(acknowledgement)
    second_ack = text.find(acknowledgement, first_ack + 1)
    if first_ack < 0 or second_ack < text.find(ORDERED_MARKERS[1]):
        raise GradleMapCompassError("initial/reopened format-3 acknowledgement was not observed")
    screenshots = run_root / "screenshots"
    captures: list[Path] = []
    for prefix in CAPTURE_PREFIXES:
        matches = tuple(sorted(screenshots.glob(prefix + "*.png")))
        if len(matches) != 1 or matches[0].is_symlink() or not matches[0].is_file():
            raise GradleMapCompassError(f"capture is missing or ambiguous: {prefix}")
        data = matches[0].read_bytes()
        if len(data) < 128 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise GradleMapCompassError(f"capture is not a valid PNG: {matches[0].name}")
        captures.append(matches[0])
    worlds = tuple((run_root / "saves").glob("**/level.dat"))
    if len(worlds) != 1 or worlds[0].is_symlink() or not worlds[0].is_file():
        raise GradleMapCompassError("fixture did not create exactly one disposable world")
    return log, tuple(captures), worlds[0]


def run(cell_id: str, *, repository_root: Path = ROOT,
        manifest_relative: str = "config/minecraft-version-matrix.json",
        run_id_factory: Callable[[], str] = new_run_id,
        provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
        command_executor: Callable[..., Any] = execute_command,
        gradle_dependency_cache: Path | None = None,
        gradle_distribution_zip: Path | None = None) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    manifest_path = (root / manifest_relative).resolve(strict=False)
    cell = _one_cell(load_manifest(manifest_path), cell_id)
    dependency_cache = validate_gradle_dependency_cache(gradle_dependency_cache, root)
    distribution_seed = validate_gradle_distribution_zip(gradle_distribution_zip, root)
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise GradleMapCompassError("unsafe run ID")
    paths = QualificationPaths.from_cell(root, cell, run_id)
    provenance = provenance_provider(root, manifest_path)
    create_contained_directories(paths)
    command = _command(cell, paths, dependency_cache)
    with QualificationLock.acquire(paths.lock_path, run_id):
        stage_gradle_distribution_zip(distribution_seed.source if distribution_seed else None, root, paths)
        result = command_executor(command, paths, ordinal=1)
    verdict, reason = result.verdict, result.reason
    log = paths.run_directory / "run-map-compass-capture/logs/latest.log"
    captures: tuple[Path, ...] = ()
    world: Path | None = None
    if verdict is Verdict.PASS:
        try:
            log, captures, world = _verify_outputs(paths, cell["loader"])
        except GradleMapCompassError as error:
            verdict, reason = Verdict.FAIL, str(error)
    payload = {
        "format": 1, "fixture": FIXTURE, "evidence_kind": "source-abi-graphical-gameplay",
        "cell": cell["id"], "loader": cell["loader"], "minecraft": cell["minecraft"]["version"],
        "run_id": run_id, "verdict": verdict.value, "reason": reason,
        "source": {"commit": provenance.commit, "branch": provenance.branch,
                   "upstream": provenance.upstream, "origin": provenance.origin,
                   "manifest_sha256": provenance.manifest_sha256,
                   "gradlew_sha256": provenance.gradle_wrapper_sha256, "java": provenance.java_version},
        "command": {"argv": list(result.argv), "exit_code": result.return_code,
                    "started_at": result.started_at_utc, "elapsed_seconds": result.elapsed_seconds,
                    "stdout": result.stdout_log, "stderr": result.stderr_log},
        "game_log": {"path": str(log), "sha256": _sha256(log)} if log.is_file() else None,
        "world": {"level_dat": str(world), "sha256": _sha256(world)} if world else None,
        "captures": [{"path": str(path), "sha256": _sha256(path), "bytes": path.stat().st_size}
                     for path in captures],
        "claims": {
            "actual_minecraft_client": verdict is Verdict.PASS,
            "exact_patch_dependencies": verdict is Verdict.PASS,
            "map_pixels_and_seam_markers": verdict is Verdict.PASS,
            "compass_targets_and_nearest_images": verdict is Verdict.PASS,
            "item_frame_and_banner_persistence": verdict is Verdict.PASS,
            "normal_disconnect_reopen_and_state_clear": verdict is Verdict.PASS,
            "format3_acknowledged_before_and_after_reopen": verdict is Verdict.PASS,
            "production_launcher": False, "frozen_candidate_jar": False,
        },
    }
    evidence = paths.evidence_directory / "nightly/08-map-compass-reconnect"
    write_terminal_report(
        evidence, payload,
        f"# {cell_id} map/compass qualification\n\nVerdict: **{verdict.value}**\n\n"
        "This is source-ABI graphical/gameplay evidence, not a production-launcher claim.\n",
        stem="terminal",
    )
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cell", required=True)
    parser.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    parser.add_argument("--gradle-dependency-cache")
    parser.add_argument("--gradle-distribution-zip")
    args = parser.parse_args(argv)
    try:
        result = run(
            args.cell, manifest_relative=args.manifest,
            gradle_dependency_cache=Path(args.gradle_dependency_cache) if args.gradle_dependency_cache else None,
            gradle_distribution_zip=Path(args.gradle_distribution_zip) if args.gradle_distribution_zip else None,
        )
    except (GradleMapCompassError, QualificationExecutionError, OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
