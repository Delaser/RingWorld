#!/usr/bin/env python3
"""Run frozen production projection and visual-parity fixtures for one cell."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import sys
from typing import Any, Mapping, Sequence

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
from run_gradle_production_lifecycle_qualification import (
    _source_world, _validate_source_version, _world_inventory, _world_observation,
)
from run_minecraft_qualification import (
    ROOT, stage_gradle_distribution_zip, validate_gradle_dependency_cache,
    validate_gradle_distribution_zip,
)


FIXTURE = "frozen-production-atlas-render"
EVIDENCE_SUBDIRECTORY = "nightly/12-production-atlas-render"
PROJECTION_DESTINATION = "RingWorld Qualified Projection"
PARITY_DESTINATION = "RingWorld Qualified Visual Parity"
ENVIRONMENTS = ("noon", "dusk", "night", "rain")


class GradleProductionRenderError(QualificationExecutionError):
    """Production rendering evidence is unsafe or incomplete."""


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
            "projection": ":runProductionProjectionClient",
            "projection_prepare": ":prepareCopiedProductionProjectionWorld",
            "parity": ":runProductionVisualParityClient",
            "parity_prepare": ":prepareCopiedProductionVisualParityWorld",
        }
    if loader == "neoforge":
        return {
            "assets": ":neoforge:downloadAssets",
            "projection": ":neoforge:runProductionProjectionClient",
            "projection_prepare": ":neoforge:prepareCopiedNeoForgeProductionProjectionWorld",
            "parity": ":neoforge:runProductionVisualParityClient",
            "parity_prepare": ":neoforge:prepareCopiedNeoForgeProductionVisualParityWorld",
        }
    raise GradleProductionRenderError("unsupported loader")


def _record(prepared: Any, arguments: Sequence[str], timeout: int,
            dependency_cache: Path | None) -> CommandRecord:
    environment = (("GRADLE_USER_HOME", str(prepared.paths.gradle_home)),)
    if dependency_cache is not None:
        environment += (("GRADLE_RO_DEP_CACHE", str(dependency_cache)),)
    return CommandRecord(
        PhaseName.DEDICATED_SMOKE, _base_argv(prepared) + tuple(arguments),
        prepared.paths.repository_root, environment, timeout,
    )


def _runtime(prepared: Any, name: str) -> Path:
    value = (prepared.paths.run_directory / name).resolve(strict=False)
    if not value.is_relative_to(prepared.paths.cell_root.resolve(strict=False)):
        raise GradleProductionRenderError("production render runtime escapes the cell")
    return value


def _install_runtime(prepared: Any, runtime: Path) -> dict[str, str]:
    mods = runtime / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    installed = mods / "ringworld-qualification.jar"
    if installed.exists() or installed.is_symlink():
        installed.unlink()
    shutil.copy2(prepared.candidate.path, installed)
    digest = _sha256(installed)
    if installed.is_symlink() or digest != prepared.candidate.sha256:
        raise GradleProductionRenderError("installed frozen candidate hash changed")
    (runtime / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\n", encoding="utf-8")
    if prepared.cell["loader"] == "neoforge":
        fml = runtime / "config/fml.toml"
        fml.parent.mkdir(parents=True, exist_ok=True)
        fml.write_text("# Automated production-render fixture.\nearlyWindowControl = false\n",
                       encoding="utf-8")
    return {"path": str(installed), "sha256": digest}


def _fresh_copy(source: Path, destination: Path, expected: Mapping[str, Any]) -> None:
    if destination.exists():
        if destination.is_symlink() or not destination.is_dir():
            raise GradleProductionRenderError("production render destination is unsafe")
        shutil.rmtree(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, destination, symlinks=True)
    if _world_inventory(destination) != expected:
        raise GradleProductionRenderError("production render world copy differs from source")


def _png(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise GradleProductionRenderError(f"production render capture is missing: {path.name}")
    data = path.read_bytes()
    if len(data) < 128 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise GradleProductionRenderError(f"production render capture is not PNG: {path.name}")
    return {"path": str(path), "sha256": _sha256(path), "bytes": len(data)}


def _verify_patch(text: str, prepared: Any) -> None:
    version = str(prepared.cell["minecraft"]["version"])
    marker = (f"Loading Minecraft {version} " if prepared.cell["loader"] == "fabric"
              else f"Minecraft {version} (minecraft)")
    if marker not in text:
        raise GradleProductionRenderError("production render client patch marker is missing")


def _verify_projection(prepared: Any, environment: str) -> tuple[dict[str, Any], ...]:
    runtime = _runtime(prepared, "run-production-projection")
    log = runtime / "logs/latest.log"
    if log.is_symlink() or not log.is_file():
        raise GradleProductionRenderError("projection client log is missing")
    text = log.read_text(encoding="utf-8", errors="replace")
    _verify_patch(text, prepared)
    if "[projection-capture] result=false" in text \
            or "[projection-capture] result=true, captures complete" not in text:
        raise GradleProductionRenderError(f"{environment} projection did not pass")
    for view in ("tangent", "handoff", "radial-up"):
        if f"[projection-capture] {view} frame metrics:" not in text:
            raise GradleProductionRenderError(f"{environment} {view} frame metrics are missing")
    prefix = "ringworld-projection-" if environment == "noon" \
        else f"ringworld-projection-{environment}-"
    return tuple(_png(runtime / "screenshots" / f"{prefix}{view}.png")
                 for view in ("tangent", "handoff", "up"))


def _verify_parity(prepared: Any) -> tuple[dict[str, Any], ...]:
    runtime = _runtime(prepared, "run-production-visual-parity")
    log = runtime / "logs/latest.log"
    if log.is_symlink() or not log.is_file():
        raise GradleProductionRenderError("visual-parity client log is missing")
    text = log.read_text(encoding="utf-8", errors="replace")
    _verify_patch(text, prepared)
    if "[visual-parity-capture] result=false" in text \
            or "[visual-parity-capture] result=true, captures complete" not in text:
        raise GradleProductionRenderError("visual-parity fixture did not pass")
    metrics = next((line for line in reversed(text.splitlines())
                    if "[visual-parity-capture] seam motion frame metrics:" in line), None)
    if metrics is None or "samples=0" in metrics:
        raise GradleProductionRenderError("visual-parity seam frame metrics are missing")
    return tuple(_png(runtime / "screenshots" / f"ringworld-visual-parity-{view}.png")
                 for view in ("seam", "seam-join", "min-rim", "max-rim"))


def _copy_log(prepared: Any, runtime_name: str, target_name: str) -> dict[str, str]:
    source = _runtime(prepared, runtime_name) / "logs/latest.log"
    target = prepared.paths.logs_directory / target_name
    shutil.copy2(source, target)
    return {"path": str(target), "sha256": _sha256(target)}


def _execute(prepared: Any, source_world: Path, dependency_cache: Path | None,
             distribution_zip: Path | None, loom_seed: Sequence[Path]) -> dict[str, Any]:
    paths, cell = prepared.paths, prepared.cell
    create_contained_directories(paths)
    stage_gradle_distribution_zip(distribution_zip, paths.repository_root, paths)
    _stage_loom_seed(loom_seed, paths.gradle_home, str(cell["minecraft"]["version"]),
                     str(cell["loader"]))
    source_inventory = _world_inventory(source_world)
    source_observation = _world_observation(source_world)
    _validate_source_version(source_observation.get("minecraft_version"),
                             str(cell["minecraft"]["version"]))
    tasks, timeout = _tasks(cell["loader"]), 7_200
    projection_runtime = _runtime(prepared, "run-production-projection")
    parity_runtime = _runtime(prepared, "run-production-visual-parity")
    projection_jar = _install_runtime(prepared, projection_runtime)
    parity_jar = _install_runtime(prepared, parity_runtime)
    projection_world = projection_runtime / "saves" / PROJECTION_DESTINATION
    parity_world = parity_runtime / "saves" / PARITY_DESTINATION
    commands: list[dict[str, Any]] = []
    captures: list[dict[str, Any]] = []
    logs: list[dict[str, str]] = []
    assets = execute_command(_record(prepared, (tasks["assets"],), _timeout(cell), dependency_cache),
                             paths, ordinal=1)
    commands.append(_executed_record("assets", assets))
    if assets.verdict is not Verdict.PASS:
        raise GradleProductionRenderError("serial asset warmup failed")
    ordinal = 2
    for environment in ENVIRONMENTS:
        _fresh_copy(source_world, projection_world, source_inventory)
        arguments = (
            f"-PringProjectionDestination={PROJECTION_DESTINATION}",
            f"-PringProjectionEnvironment={environment}",
            f"-PringNeoForgeProjectionDestination={PROJECTION_DESTINATION}",
            f"-PringNeoForgeProjectionEnvironment={environment}",
            tasks["projection"], "-x", tasks["projection_prepare"],
        )
        result = execute_command(_record(prepared, arguments, timeout, dependency_cache),
                                 paths, ordinal=ordinal)
        commands.append(_executed_record(f"projection-{environment}", result))
        if result.verdict is not Verdict.PASS:
            raise GradleProductionRenderError(f"{environment} projection Gradle run failed")
        captures.extend(_verify_projection(prepared, environment))
        logs.append(_copy_log(prepared, "run-production-projection",
                              f"projection-{environment}-game.log"))
        ordinal += 1
    _fresh_copy(source_world, parity_world, source_inventory)
    parity_args = (
        f"-PringVisualParityDestination={PARITY_DESTINATION}",
        f"-PringNeoForgeVisualParityDestination={PARITY_DESTINATION}",
        tasks["parity"], "-x", tasks["parity_prepare"],
    )
    parity = execute_command(_record(prepared, parity_args, timeout, dependency_cache),
                             paths, ordinal=ordinal)
    commands.append(_executed_record("visual-parity", parity))
    if parity.verdict is not Verdict.PASS:
        raise GradleProductionRenderError("visual-parity Gradle run failed")
    captures.extend(_verify_parity(prepared))
    logs.append(_copy_log(prepared, "run-production-visual-parity", "visual-parity-game.log"))
    if _world_inventory(source_world) != source_inventory:
        raise GradleProductionRenderError("production source changed during rendering qualification")
    return {
        "commands": commands,
        "source_world": {"path": str(source_world), "inventory": source_inventory,
                         **source_observation},
        "installed_candidates": {"projection": projection_jar, "visual_parity": parity_jar},
        "game_logs": logs,
        "captures": captures,
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
                "production_16384x256_atlas": verdict is Verdict.PASS,
                "all_sky_weather_projection_modes": verdict is Verdict.PASS,
                "natural_seam_handoff_rims_and_frame_metrics": verdict is Verdict.PASS,
                "production_launcher": False,
            },
        }
        write_terminal_report(
            prepared.paths.evidence_directory / EVIDENCE_SUBDIRECTORY, payload,
            f"# {prepared.cell['id']} frozen production render qualification\n\n"
            f"Verdict: **{verdict.value}**\n\n"
            "Exact retained-jar production Atlas/projection and visual-parity evidence. "
            "This is not a packaged production-launcher claim.\n",
            stem="terminal",
        )
    return payload


def main(argv: Sequence[str] | None = None) -> int:
    try:
        result = run(parser().parse_args(argv))
    except (QualificationExecutionError, OSError, ValueError) as error:
        print(f"production render qualification rejected: {error}", file=sys.stderr)
        return 2
    print(json.dumps({key: result.get(key) for key in
                      ("cell", "fixture", "loader", "minecraft", "reason", "run_id", "verdict")},
                     sort_keys=True))
    return 0 if result["verdict"] == Verdict.PASS.value else 1


if __name__ == "__main__":
    raise SystemExit(main())
