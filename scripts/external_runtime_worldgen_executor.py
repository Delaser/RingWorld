#!/usr/bin/env python3
"""Fail-closed external executor for the four-stage worldgen qualification."""

from __future__ import annotations

from contextlib import nullcontext
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
from typing import Any, Callable, Mapping

from external_runtime_atlas_recovery_executor import (
    _execution_source_provenance, _read_regular, _sha256_bytes,
    _validate_quick_terminal, _write_new,
)
from external_runtime_executor import (
    CommandExecutor, DownloadResult, ExternalRuntimeExecutionError, ModCopyResult,
    UrlOpen, _assert_contained, _assert_no_symlink_components, _copy_pinned_mod,
    _installed_minecraft_server, _no_redirect_urlopen, _verify_exact_mod_inventory,
    _verify_launcher, _write_planned_file, fetch_pinned_https,
)
from external_runtime_worldgen_plan import ExternalRuntimeWorldgenPlan, WorldgenStagePlan
from external_runtime_worldgen_stage_runner import (
    ExternalRuntimeWorldgenStageError,
    ExternalRuntimeWorldgenStageObservation, ExternalRuntimeWorldgenStagePlan,
    WorldgenSemanticMarker, run_external_runtime_worldgen_stage,
)
from minecraft_atlas_recovery_persistence import parse_persisted_ring_settings
from minecraft_frozen_candidate import FrozenCandidateError, FrozenCandidateInspection, inspect_frozen_candidate
from minecraft_qualification_executor import ExecutedCommand, QualificationLock, create_contained_directories, write_terminal_report
from minecraft_qualification_model import CommandRecord, InvocationError, PhaseName, QualificationPaths, Verdict, is_within
from minecraft_worldgen_qualification import (
    QualificationIdentity, TimedMarker, WorldgenLogFact,
    WorldgenQualificationEvidence, WorldgenStageEvidence,
    validate_worldgen_qualification,
)
from run_worldgen_structure_matrix import MATRIX_RE, MONUMENT_RE, parse_log


class WorldgenExecutionError(ExternalRuntimeExecutionError):
    """The worldgen fixture could not be safely assembled or evidenced."""


@dataclass(frozen=True)
class RuntimeAssembly:
    runtime_root: str
    installer: ExecutedCommand
    downloads: tuple[DownloadResult, ...]
    mods: tuple[ModCopyResult, ...]


@dataclass(frozen=True)
class ExternalWorldgenResult:
    cell_id: str
    loader: str
    minecraft_version: str
    verdict: Verdict
    reason: str | None
    assemblies: tuple[RuntimeAssembly, ...]
    evidence_json: str | None
    evidence_markdown: str | None
    qualification_summary: Mapping[str, Any] | None = None


StageRunner = Callable[..., ExternalRuntimeWorldgenStageObservation]
CandidateInspector = Callable[[Path, str], FrozenCandidateInspection]


def _revalidate_candidate(plan: ExternalRuntimeWorldgenPlan, inspector: CandidateInspector) -> FrozenCandidateInspection:
    candidate = plan.candidate
    if not is_within(candidate.path, plan.frozen_candidate_root) or candidate.path.is_symlink() or not candidate.path.is_file():
        raise WorldgenExecutionError("worldgen qualification requires the retained frozen candidate")
    try:
        observed = inspector(candidate.path, candidate.loader)
    except (OSError, FrozenCandidateError) as error:
        raise WorldgenExecutionError("frozen candidate failed worldgen pre-runtime inspection") from error
    if observed.loader != candidate.loader or observed.sha256 != candidate.sha256 \
            or (candidate.declared_target_range is not None and observed.minecraft_range != candidate.declared_target_range):
        raise WorldgenExecutionError("frozen candidate changed after quick qualification")
    return observed


def _assemble(smoke, paths: QualificationPaths, ordinal: int, opener: UrlOpen,
              command_executor: CommandExecutor) -> RuntimeAssembly:
    _assert_contained(smoke, paths)
    smoke.layout.root.mkdir(parents=True, exist_ok=False)
    downloads = [fetch_pinned_https(smoke.minecraft_server, paths, opener=opener)]
    downloads.extend(fetch_pinned_https(item, paths, opener=opener) for item in smoke.downloads)
    record = CommandRecord(
        PhaseName.DEDICATED_SMOKE, smoke.installer.argv, smoke.installer.cwd, (),
        smoke.launch.timeout_seconds,
    )
    installer = command_executor(record, paths, ordinal=ordinal)
    if installer.verdict is not Verdict.PASS:
        raise WorldgenExecutionError("worldgen runtime installer failed: " + (installer.reason or "unknown"))
    _assert_no_symlink_components(smoke.layout.root, paths.cell_root, "installed worldgen runtime")
    _verify_launcher(smoke)
    _installed_minecraft_server(smoke)
    for file in smoke.files:
        _write_planned_file(file.path, file.contents, smoke.layout.root)
    copied = tuple(_copy_pinned_mod(mod, smoke.layout.root) for mod in smoke.mods)
    _verify_exact_mod_inventory(smoke)
    return RuntimeAssembly(str(smoke.layout.root), installer, tuple(downloads), copied)


_RINGWORLD_JVM = re.compile(r"^-Dringworld\.(?:strongholdTest|worldgenMatrix|strongholdTestResume)=")


def _neoforge_jvm_properties(stage: WorldgenStagePlan) -> None:
    args = stage.smoke.layout.neoforge_user_jvm_args
    if args is None or not args.is_absolute() or not is_within(args, stage.runtime_root):
        raise WorldgenExecutionError("NeoForge worldgen stage has no contained user_jvm_args.txt")
    _assert_no_symlink_components(args, stage.runtime_root, "NeoForge worldgen JVM arguments")
    descriptor = -1
    try:
        descriptor = os.open(args, os.O_RDWR | getattr(os, "O_NOFOLLOW", 0))
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise WorldgenExecutionError("NeoForge worldgen JVM arguments are not a regular file")
        with os.fdopen(descriptor, "r+", encoding="utf-8", newline="") as output:
            descriptor = -1
            lines = output.read().splitlines()
            retained = [line for line in lines if not _RINGWORLD_JVM.match(line)]
            if len(lines) - len(retained) not in {0, 3}:
                raise WorldgenExecutionError("NeoForge worldgen JVM arguments contain a partial or duplicate property set")
            output.seek(0)
            output.truncate()
            output.write("\n".join((*retained, *stage.jvm_properties)) + "\n")
            output.flush()
            os.fsync(output.fileno())
    except (OSError, UnicodeError) as error:
        raise WorldgenExecutionError("cannot update NeoForge worldgen JVM arguments") from error
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _stage_process_plan(stage: WorldgenStagePlan) -> ExternalRuntimeWorldgenStagePlan:
    launch = stage.smoke.launch
    if stage.smoke.loader == "fabric":
        try:
            jar = launch.argv.index("-jar")
        except ValueError as error:
            raise WorldgenExecutionError("Fabric worldgen launch has no -jar boundary") from error
        argv = launch.argv[:jar] + stage.jvm_properties + launch.argv[jar:]
    elif stage.smoke.loader == "neoforge":
        _neoforge_jvm_properties(stage)
        argv = launch.argv
    else:
        raise WorldgenExecutionError("worldgen stage uses an unsupported loader")
    port = int(dict(stage.server_properties)["server-port"])
    return ExternalRuntimeWorldgenStagePlan(
        stage.name, stage.runtime_root, argv, stage.runtime_root,
        launch.timeout_seconds, port,
        (
            WorldgenSemanticMarker("worldgen-record", "[worldgen-matrix] seed="),
            WorldgenSemanticMarker("monument-record", "[worldgen-matrix] monumentStatus="),
            WorldgenSemanticMarker("fixture-pass", "[stronghold-test] PASS"),
        ),
    )


def _parse_one_stage(stage: WorldgenStagePlan, observation: ExternalRuntimeWorldgenStageObservation,
                     plan: ExternalRuntimeWorldgenPlan, paths: QualificationPaths) -> WorldgenStageEvidence:
    runtime_log = Path(observation.server_log)
    raw_log = _read_regular(runtime_log, paths.logs_directory, stage.name + " runtime log")
    try:
        text = raw_log.decode("utf-8")
    except UnicodeError as error:
        raise WorldgenExecutionError(stage.name + " runtime log is not UTF-8") from error
    if len(list(MATRIX_RE.finditer(text))) != 1 or len(list(MONUMENT_RE.finditer(text))) != 1 \
            or text.count("[stronghold-test] PASS") != 1:
        raise WorldgenExecutionError(stage.name + " did not emit one exact worldgen record set")
    try:
        record = parse_log(text)
    except ValueError as error:
        raise WorldgenExecutionError(stage.name + " has no complete parsed worldgen facts") from error
    settings_path = stage.world_root / "dimensions/minecraft/overworld/data/ringworld/settings.dat"
    settings_raw = _read_regular(settings_path, stage.world_root, stage.name + " persisted settings")
    settings = parse_persisted_ring_settings(settings_raw, settings_path)
    _write_new(stage.captured_log_path, raw_log, plan.evidence_root, stage.name + " captured log")
    captured_hash = _sha256_bytes(raw_log)
    summary = {
        "stage": stage.name,
        "resume": stage.resume,
        "settingsSha256": settings.settings_sha256,
        "runtimeLogSha256": observation.server_log_sha256,
        "capturedLogSha256": captured_hash,
        "record": record,
    }
    _write_new(stage.summary_path, json.dumps(summary, sort_keys=True, indent=2).encode("utf-8") + b"\n",
               plan.evidence_root, stage.name + " summary")
    events = {event.name: event.timestamp_epoch_ms for event in observation.marker_events}
    if tuple(event.name for event in observation.marker_events) != (
        "server-ready", "worldgen-record", "monument-record", "fixture-pass",
    ):
        raise WorldgenExecutionError(stage.name + " returned a wrong process marker ledger")
    markers = (
        TimedMarker("worldgen-matrix-record", events["worldgen-record"]),
        TimedMarker("stronghold-test-pass", events["fixture-pass"]),
    )
    return WorldgenStageEvidence(
        stage.runtime_root, stage.world_root, settings,
        WorldgenLogFact(
            stage.name, stage.resume, stage.seed, int(record["numeric_seed"]),
            stage.circumference_blocks, stage.width_blocks,
            settings.terrain_noise_mapping, settings.format_version, record,
            runtime_log, stage.captured_log_path, observation.server_log_sha256,
            captured_hash, markers, observation.return_code,
        ),
    )


def _result_payload(result: ExternalWorldgenResult) -> dict[str, Any]:
    return {
        "fixture": "worldgen-seam-structures", "cell_id": result.cell_id,
        "loader": result.loader, "minecraft_version": result.minecraft_version,
        "verdict": result.verdict.value, "reason": result.reason,
        "assemblies": [
            {
                "runtimeRoot": item.runtime_root,
                "installer": {"argv": list(item.installer.argv), "returnCode": item.installer.return_code},
                "downloads": [value.__dict__ for value in item.downloads],
                "mods": [value.__dict__ for value in item.mods],
            } for item in result.assemblies
        ],
        "qualification": result.qualification_summary,
    }


def _record(result: ExternalWorldgenResult, plan: ExternalRuntimeWorldgenPlan) -> ExternalWorldgenResult:
    markdown = (
        "# External worldgen and structure qualification\n\n"
        f"- Verdict: `{result.verdict.value}`\n- Cell: `{result.cell_id}`\n"
        f"- Loader: `{result.loader}`\n- Reason: `{result.reason or 'none'}`\n"
    )
    json_path, markdown_path = write_terminal_report(plan.evidence_root, _result_payload(result), markdown, stem="terminal")
    return ExternalWorldgenResult(
        result.cell_id, result.loader, result.minecraft_version, result.verdict,
        result.reason, result.assemblies, str(json_path), str(markdown_path),
        result.qualification_summary,
    )


def execute_external_runtime_worldgen(
    plan: ExternalRuntimeWorldgenPlan, paths: QualificationPaths, run_id: str, *,
    canonical_cells: Mapping[str, Mapping[str, Any]],
    range_identities: Mapping[str, Mapping[str, str]],
    opener: UrlOpen = _no_redirect_urlopen,
    command_executor: CommandExecutor | None = None,
    stage_runner: StageRunner = run_external_runtime_worldgen_stage,
    candidate_inspector: CandidateInspector = inspect_frozen_candidate,
    held_lock: QualificationLock | None = None,
    execution_source_provenance: Mapping[str, Any] | None = None,
) -> ExternalWorldgenResult:
    """Install three clean runtimes and execute the exact four-stage matrix."""
    if command_executor is None:
        from minecraft_qualification_executor import execute_command
        command_executor = execute_command
    source = _execution_source_provenance(execution_source_provenance)
    if plan.lock_path != paths.lock_path or plan.fixture_root != paths.run_directory / "nightly/02-worldgen-seam-structures" \
            or plan.evidence_root != paths.evidence_directory / "nightly/02-worldgen-seam-structures":
        raise WorldgenExecutionError("worldgen plan does not use exact nightly paths")
    lock_context = QualificationLock.acquire(plan.lock_path, run_id) if held_lock is None else nullcontext(held_lock)
    if held_lock is not None:
        held_lock.require_held_for(plan.lock_path, run_id)
    assemblies: list[RuntimeAssembly] = []
    with lock_context:
        if plan.fixture_root.exists() or plan.evidence_root.exists():
            raise WorldgenExecutionError("worldgen nightly fixture paths must be absent")
        _assert_no_symlink_components(paths.cell_root, paths.repository_root, "qualification cell")
        _revalidate_candidate(plan, candidate_inspector)
        quick_raw = _read_regular(plan.quick_terminal_evidence.path, plan.quick_evidence_root, "quick terminal evidence")
        if _sha256_bytes(quick_raw) != plan.quick_terminal_evidence.sha256:
            raise WorldgenExecutionError("quick terminal evidence changed before worldgen execution")
        # Reuse the strict quick schema used by Atlas recovery.
        class _QuickPlan:
            smoke = type("Smoke", (), {
                "cell_id": plan.cell_id, "loader": plan.loader,
                "minecraft_version": plan.minecraft_version, "candidate": plan.candidate,
            })()
        _validate_quick_terminal(quick_raw, _QuickPlan(), canonical_cells, range_identities)  # type: ignore[arg-type]
        create_contained_directories(paths)
        plan.fixture_root.mkdir(parents=True, exist_ok=False)
        plan.evidence_root.mkdir(parents=True, exist_ok=False)
        unique: dict[Path, RuntimeAssembly] = {}
        for stage in plan.stages:
            if stage.runtime_root not in unique:
                unique[stage.runtime_root] = _assemble(stage.smoke, paths, len(unique) + 1, opener, command_executor)
                assemblies.append(unique[stage.runtime_root])
        stages: list[WorldgenStageEvidence] = []
        for stage in plan.stages:
            try:
                observation = stage_runner(
                    _stage_process_plan(stage), cell_root=paths.cell_root,
                    logs_directory=paths.logs_directory,
                )
            except ExternalRuntimeWorldgenStageError as error:
                return _record(ExternalWorldgenResult(
                    plan.cell_id, plan.loader, plan.minecraft_version, Verdict.FAIL,
                    "WORLDGEN_STAGE:" + str(error), tuple(assemblies), None, None,
                ), plan)
            stages.append(_parse_one_stage(stage, observation, plan, paths))
        identity = QualificationIdentity(
            plan.cell_id, plan.loader, plan.minecraft_version,
            plan.candidate.sha256, plan.quick_terminal_evidence.sha256,
        )
        try:
            qualification = validate_worldgen_qualification(
                canonical_cells[plan.cell_id], identity,
                WorldgenQualificationEvidence(plan.fixture_root, plan.evidence_root, paths.logs_directory, tuple(stages)),  # type: ignore[arg-type]
            )
        except (InvocationError, KeyError) as error:
            return _record(ExternalWorldgenResult(
                plan.cell_id, plan.loader, plan.minecraft_version, Verdict.FAIL,
                "WORLDGEN_CONTRACT:" + str(error), tuple(assemblies), None, None,
            ), plan)
        summary = qualification.as_dict()
        summary["frozenCandidateSha256"] = plan.candidate.sha256
        summary["quickTerminalEvidenceSha256"] = plan.quick_terminal_evidence.sha256
        summary["executionSourceProvenance"] = source
        summary["captures"] = {
            stage.name: {
                "log": str(stage.captured_log_path.relative_to(paths.cell_root)),
                "logSha256": _sha256_bytes(_read_regular(stage.captured_log_path, plan.evidence_root, stage.name + " capture")),
                "summary": str(stage.summary_path.relative_to(paths.cell_root)),
            } for stage in plan.stages
        }
        return _record(ExternalWorldgenResult(
            plan.cell_id, plan.loader, plan.minecraft_version, Verdict.PASS, None,
            tuple(assemblies), None, None, summary,
        ), plan)


execute_external_worldgen = execute_external_runtime_worldgen
