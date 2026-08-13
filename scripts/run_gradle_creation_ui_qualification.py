#!/usr/bin/env python3
"""Run the existing graphical creation-UI fixture in one pinned Gradle cell.

This is source-ABI evidence for one exact Minecraft/loader cell. It does not
claim that a packaged frozen jar was launched by a production launcher; that
separate release gate needs an authenticated disposable launcher profile.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
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
from minecraft_qualification_model import (
    CommandRecord,
    PhaseName,
    QualificationPaths,
    Verdict,
    gradle_properties,
    select_cells,
)
from run_minecraft_qualification import (
    ROOT,
    SourceProvenance,
    collect_source_provenance,
    load_manifest,
    stage_gradle_distribution_zip,
    validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURE = "creation-settings-ui"
PASS_MARKER = "[creation-ui-test] PASS"
FAIL_MARKER = "[creation-ui-test] FAIL"
CAPTURE_PREFIXES = (
    "creation-ui-01-footer-scale1",
    "creation-ui-02-default-scale1",
    "creation-ui-03-default-scale2",
    "creation-ui-04-default-scale3",
    "creation-ui-05-default-scale4",
    "creation-ui-06-large-narrow-scale4",
    "creation-ui-07-invalid-five-errors-narrow-scale4",
    "creation-ui-08-small-scale4",
    "creation-ui-09-medium-scale4",
    "creation-ui-10-large-scale4",
    "creation-ui-11-custom-monument-scale4",
    "creation-ui-12-confirm-layout-scale4",
    "creation-ui-13-footer-applied-scale4",
)
_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")


class GradleCreationUiError(QualificationExecutionError):
    """The pinned graphical fixture could not produce trustworthy evidence."""


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _one_cell(manifest: Mapping[str, Any], cell_id: str) -> Mapping[str, Any]:
    selected = select_cells(manifest, (cell_id,))
    if len(selected) != 1:
        raise GradleCreationUiError("creation UI qualification requires exactly one cell")
    return selected[0]


def _command(
    cell: Mapping[str, Any], paths: QualificationPaths, dependency_cache: Path | None = None,
) -> CommandRecord:
    loader = cell.get("loader")
    task = ":runCreationUiClient" if loader == "fabric" else ":neoforge:runCreationUiClient"
    if loader not in {"fabric", "neoforge"}:
        raise GradleCreationUiError("unsupported loader")
    profile = cell.get("profile")
    timeout = profile.get("timeout_seconds") if isinstance(profile, Mapping) else None
    if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout < 1:
        raise GradleCreationUiError("cell has no valid timeout")
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


def _verify_outputs(paths: QualificationPaths) -> tuple[Path, tuple[Path, ...]]:
    run = paths.run_directory / "run-creation-ui"
    log = run / "logs" / "latest.log"
    if not log.is_file() or log.is_symlink():
        raise GradleCreationUiError("creation UI fixture did not write a safe latest.log")
    text = log.read_text(encoding="utf-8", errors="replace")
    if PASS_MARKER not in text or FAIL_MARKER in text:
        raise GradleCreationUiError("creation UI fixture did not emit its terminal PASS marker")
    captures: list[Path] = []
    screenshots = run / "screenshots"
    for prefix in CAPTURE_PREFIXES:
        matches = tuple(sorted(screenshots.glob(prefix + "*.png")))
        if len(matches) != 1 or matches[0].is_symlink() or not matches[0].is_file():
            raise GradleCreationUiError(f"creation UI capture is missing or ambiguous: {prefix}")
        data = matches[0].read_bytes()
        if len(data) < 128 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise GradleCreationUiError(f"creation UI capture is not a valid PNG: {matches[0].name}")
        captures.append(matches[0])
    if tuple((run / "saves").glob("**/level.dat")):
        raise GradleCreationUiError("menu-only creation UI fixture created a world")
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
            "production_launcher": False,
            "frozen_candidate_jar": False,
            "world_created": False if verdict is Verdict.PASS else None,
        },
    }


def run(
    cell_id: str, *, repository_root: Path = ROOT, manifest_relative: str = "config/minecraft-version-matrix.json",
    run_id_factory: Callable[[], str] = new_run_id,
    provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
    command_executor: Callable[..., Any] = execute_command,
    gradle_dependency_cache: Path | None = None,
    gradle_distribution_zip: Path | None = None,
) -> dict[str, Any]:
    root = repository_root.resolve(strict=False)
    manifest_path = (root / manifest_relative).resolve(strict=False)
    manifest = load_manifest(manifest_path)
    cell = _one_cell(manifest, cell_id)
    dependency_cache = validate_gradle_dependency_cache(gradle_dependency_cache, root)
    distribution_seed = validate_gradle_distribution_zip(gradle_distribution_zip, root)
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise GradleCreationUiError("unsafe run ID")
    paths = QualificationPaths.from_cell(root, cell, run_id)
    provenance = provenance_provider(root, manifest_path)
    create_contained_directories(paths)
    command = _command(cell, paths, dependency_cache)
    log = paths.run_directory / "run-creation-ui" / "logs" / "latest.log"
    captures: tuple[Path, ...] = ()
    with QualificationLock.acquire(paths.lock_path, run_id):
        stage_gradle_distribution_zip(
            distribution_seed.source if distribution_seed is not None else None, root, paths,
        )
        result = command_executor(command, paths, ordinal=1)
    verdict = result.verdict
    reason = result.reason
    if verdict is Verdict.PASS:
        try:
            log, captures = _verify_outputs(paths)
        except GradleCreationUiError as error:
            verdict, reason = Verdict.FAIL, str(error)
    payload = _payload(cell, paths, provenance, result, log, captures, verdict, reason)
    evidence = paths.evidence_directory / "nightly" / "01-creation-settings-ui"
    write_terminal_report(
        evidence,
        payload,
        f"# {cell_id} creation UI qualification\n\nVerdict: **{verdict.value}**\n\n"
        "This is source-ABI graphical evidence, not a production-launcher claim.\n",
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
            gradle_dependency_cache=(
                Path(arguments.gradle_dependency_cache) if arguments.gradle_dependency_cache else None
            ),
            gradle_distribution_zip=(
                Path(arguments.gradle_distribution_zip) if arguments.gradle_distribution_zip else None
            ),
        )
    except (GradleCreationUiError, QualificationExecutionError, OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
