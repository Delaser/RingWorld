#!/usr/bin/env python3
"""Fail-closed foundation for the external nightly Atlas recovery fixture.

The adapter intentionally has no default Minecraft process implementation yet.
An orchestration layer must provide the bounded stage runner that observes a
real headless server, waits for a durable partial Atlas, sends ``stop``, and
then observes the self-halting recovery run.  Without that injected runner this
module records no PASS.  The assembly, capture, independent parsers, identity
binding, exclusive evidence writes, and exact lent-lock checks are real now.
"""

from __future__ import annotations

from contextlib import nullcontext
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import stat
import hashlib
from typing import Any, Callable, Mapping

from external_runtime_atlas_recovery_plan import AtlasRecoveryStagePlan, ExternalRuntimeAtlasRecoveryPlan
from external_runtime_executor import (
    CommandExecutor,
    DownloadResult,
    ExternalRuntimeExecutionError,
    ModCopyResult,
    UrlOpen,
    _assert_contained,
    _assert_no_symlink_components,
    _copy_pinned_mod,
    _installed_minecraft_server,
    _no_redirect_urlopen,
    _verify_exact_mod_inventory,
    _verify_launcher,
    _write_planned_file,
    fetch_pinned_https,
)
from minecraft_atlas_recovery_persistence import parse_persisted_ring_settings, parse_ring_terrain_atlas
from minecraft_atlas_recovery_qualification import (
    AtlasRecoveryEvidence,
    AtlasReportFact,
    FreshRuntimeObservation,
    MarkerLedger,
    QualificationIdentity,
    TimedMarker,
    validate_atlas_recovery_qualification,
)
from minecraft_qualification_executor import ExecutedCommand, QualificationLock, create_contained_directories, write_terminal_report
from minecraft_qualification_model import CommandRecord, InvocationError, PhaseName, QualificationPaths, Verdict, is_within
from minecraft_frozen_candidate import FrozenCandidateError, FrozenCandidateInspection, inspect_frozen_candidate
from minecraft_qualification_evidence import TerminalEvidenceError, validate_terminal_evidence


class AtlasRecoveryExecutionError(ExternalRuntimeExecutionError):
    """The two-stage external Atlas fixture could not be safely evidenced."""


@dataclass(frozen=True)
class AtlasRecoveryStageResult:
    """Facts the injected live-stage runner observed before it returned."""

    exit_code: int
    marker_events: tuple[TimedMarker, ...]
    graceful_stop_sent: bool
    self_halted: bool
    server_log: str


@dataclass(frozen=True)
class ExternalAtlasRecoveryResult:
    cell_id: str
    loader: str
    minecraft_version: str
    verdict: Verdict
    reason: str | None
    installer: ExecutedCommand | None
    downloads: tuple[DownloadResult, ...]
    mods: tuple[ModCopyResult, ...]
    evidence_json: str | None
    evidence_markdown: str | None
    qualification_summary: Mapping[str, Any] | None = None


StageRunner = Callable[[AtlasRecoveryStagePlan, ExternalRuntimeAtlasRecoveryPlan, QualificationPaths], AtlasRecoveryStageResult]
CandidateInspector = Callable[[Path, str], FrozenCandidateInspection]


def _execution_source_provenance(value: Mapping[str, Any] | None) -> dict[str, str]:
    """Normalize the clean source identity that executed this nightly gate.

    The frozen jar and quick terminal record deliberately come from an earlier
    quick-qualification run.  The recovery runner itself is still executable
    source, so a passing nightly record must say exactly which clean, pushed
    Java-25 checkout ran it.  Keeping this small schema here avoids a circular
    import from the process executor back into the CLI scheduler.
    """
    required = {
        "commit", "branch", "upstream", "origin", "manifest_sha256",
        "gradle_wrapper_sha256", "java_version",
    }
    if not isinstance(value, Mapping) or set(value) != required:
        raise AtlasRecoveryExecutionError("Atlas recovery requires exact current execution source provenance")
    normalized: dict[str, str] = {}
    for name in required:
        candidate = value.get(name)
        if not isinstance(candidate, str) or not candidate:
            raise AtlasRecoveryExecutionError(f"execution source provenance has no valid {name}")
        normalized[name] = candidate
    if any(re.fullmatch(r"[0-9a-f]{40}", normalized[name]) is None for name in ("commit", "upstream")) \
            or normalized["commit"] != normalized["upstream"]:
        raise AtlasRecoveryExecutionError("execution source provenance is not synchronized to one full commit")
    if any(re.fullmatch(r"[0-9a-f]{64}", normalized[name]) is None
           for name in ("manifest_sha256", "gradle_wrapper_sha256")):
        raise AtlasRecoveryExecutionError("execution source provenance has invalid input hashes")
    if normalized["origin"] != "https://github.com/Delaser/RingWorld.git":
        raise AtlasRecoveryExecutionError("execution source provenance has an unexpected origin")
    if not re.search(r"(?:version\s+\"?25(?:[.\"]|$)|openjdk\s+25(?:[.\s]|$))",
                     normalized["java_version"], re.IGNORECASE):
        raise AtlasRecoveryExecutionError("execution source provenance does not prove Java 25")
    return normalized


def _unimplemented_stage_runner(
    stage: AtlasRecoveryStagePlan,
    plan: ExternalRuntimeAtlasRecoveryPlan,
    paths: QualificationPaths,
) -> AtlasRecoveryStageResult:
    del stage, plan, paths
    raise AtlasRecoveryExecutionError(
        "ATLAS_RECOVERY_STAGE_RUNNER_NOT_CONFIGURED: this foundation cannot claim a runtime PASS"
    )


def _read_regular(path: Path, root: Path, label: str) -> bytes:
    if not is_within(path, root):
        raise AtlasRecoveryExecutionError(f"{label} must be a regular contained file")
    _assert_no_symlink_components(path, root, label)
    descriptor = -1
    try:
        descriptor = os.open(
            path, os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0),
        )
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise AtlasRecoveryExecutionError(f"{label} must be a regular contained file")
        chunks: list[bytes] = []
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                return b"".join(chunks)
            chunks.append(block)
    except OSError as error:
        raise AtlasRecoveryExecutionError(f"cannot read {label}: {error}") from error
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _write_new(path: Path, raw: bytes, root: Path, label: str) -> None:
    if not isinstance(raw, bytes) or not is_within(path, root) or path.exists() or path.is_symlink():
        raise AtlasRecoveryExecutionError(f"refusing to replace or escape {label}")
    _assert_no_symlink_components(path.parent, root, label)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(
            path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        with os.fdopen(descriptor, "wb") as output:
            output.write(raw)
            output.flush()
            os.fsync(output.fileno())
    except OSError as error:
        raise AtlasRecoveryExecutionError(f"cannot exclusively capture {label}: {error}") from error


def _append_neoforge_headless_properties(plan: ExternalRuntimeAtlasRecoveryPlan) -> None:
    """Append only to the installer-created regular NeoForge JVM-args file."""
    smoke = plan.smoke
    if smoke.loader != "neoforge":
        return
    args = smoke.layout.neoforge_user_jvm_args
    if args is None or args.is_symlink() or not args.is_file() or not is_within(args, plan.runtime_root):
        raise AtlasRecoveryExecutionError("NeoForge installer did not create a contained regular user_jvm_args.txt")
    _assert_no_symlink_components(args, plan.runtime_root, "NeoForge user_jvm_args.txt")
    descriptor = -1
    try:
        descriptor = os.open(args, os.O_RDWR | os.O_APPEND | getattr(os, "O_NOFOLLOW", 0))
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise AtlasRecoveryExecutionError("NeoForge user_jvm_args.txt is not a regular file")
        with os.fdopen(descriptor, "r+", encoding="utf-8", newline="") as output:
            descriptor = -1
            output.seek(0)
            old = output.read()
            if "-Dringworld.headlessPrewarm" in old:
                raise AtlasRecoveryExecutionError("NeoForge user_jvm_args.txt already contains RingWorld headless properties")
            properties = "-Dringworld.headlessPrewarm=true\n-Dringworld.headlessPrewarmReport=result.json\n"
            if old and not old.endswith("\n"):
                properties = "\n" + properties
            output.seek(0, os.SEEK_END)
            output.write(properties)
            output.flush()
            os.fsync(output.fileno())
    except (OSError, UnicodeError) as error:
        raise AtlasRecoveryExecutionError("cannot append NeoForge headless JVM properties") from error
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _require_json_integer(raw: Mapping[str, Any], name: str) -> int:
    value = raw.get(name)
    if not isinstance(value, int) or isinstance(value, bool):
        raise AtlasRecoveryExecutionError(f"headless report field {name} must be an integer")
    return value


def _require_json_string(raw: Mapping[str, Any], name: str) -> str:
    value = raw.get(name)
    if not isinstance(value, str) or not value:
        raise AtlasRecoveryExecutionError(f"headless report field {name} must be a non-empty string")
    return value


def _parse_report(
    raw: bytes,
    stage: AtlasRecoveryStagePlan,
    plan: ExternalRuntimeAtlasRecoveryPlan,
) -> AtlasReportFact:
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as error:
        raise AtlasRecoveryExecutionError("headless Atlas report is not valid UTF-8 JSON") from error
    if not isinstance(parsed, Mapping):
        raise AtlasRecoveryExecutionError("headless Atlas report is not an object")
    if parsed.get("identityAvailable") is not True:
        raise AtlasRecoveryExecutionError("headless Atlas report must have identityAvailable=true")
    status = _require_json_string(parsed, "status")
    if status != stage.expected_status:
        raise AtlasRecoveryExecutionError(f"headless Atlas report expected {stage.expected_status}, got {status}")
    reported_atlas = _require_json_string(parsed, "atlasPath")
    relative = plan.atlas_path.relative_to(plan.runtime_root).as_posix()
    accepted_paths = {str(plan.atlas_path), relative, "./" + relative}
    if reported_atlas not in accepted_paths:
        raise AtlasRecoveryExecutionError("headless Atlas report names an unexpected Atlas path")
    failure = parsed.get("failureReason")
    if failure is not None and not isinstance(failure, str):
        raise AtlasRecoveryExecutionError("headless Atlas report has an invalid failureReason")
    return AtlasReportFact(
        _require_json_integer(parsed, "schemaVersion"), status, True,
        _require_json_string(parsed, "worldHash"), _require_json_string(parsed, "layoutFingerprint"),
        _require_json_integer(parsed, "terrainNoiseMapping"),
        _require_json_integer(parsed, "circumferenceBlocks"), _require_json_integer(parsed, "widthBlocks"),
        _require_json_integer(parsed, "completedChunks"), _require_json_integer(parsed, "totalChunks"),
        _require_json_integer(parsed, "completedCells"), _require_json_integer(parsed, "totalCells"),
        _require_json_integer(parsed, "elapsedMillis"), plan.atlas_path, stage.runtime_report_path,
        stage.captured_report_path, failure,
    )


def _ledger(
    stage: AtlasRecoveryStagePlan,
    result: AtlasRecoveryStageResult,
    paths: QualificationPaths,
) -> MarkerLedger:
    expected = ("atlas-started", "atlas-interrupted") if stage.name == "interrupted" else (
        "atlas-restarted", "atlas-recovered", "atlas-complete", "fixture-pass",
    )
    if not isinstance(result, AtlasRecoveryStageResult) \
            or tuple(event.name for event in result.marker_events) != expected:
        raise AtlasRecoveryExecutionError(f"{stage.name} stage did not return its exact ordered markers")
    if not isinstance(result.exit_code, int) or isinstance(result.exit_code, bool):
        raise AtlasRecoveryExecutionError(f"{stage.name} stage has an invalid exit code")
    log = Path(result.server_log)
    if not result.server_log or not log.is_absolute() or not is_within(log, paths.logs_directory) \
            or log.is_symlink() or not log.is_file():
        raise AtlasRecoveryExecutionError(f"{stage.name} stage did not retain a regular immutable contained log")
    _assert_no_symlink_components(log, paths.logs_directory, f"{stage.name} stage log")
    if stage.name == "interrupted":
        if result.exit_code != 0 or not result.graceful_stop_sent or result.self_halted:
            raise AtlasRecoveryExecutionError("interrupted stage must stop gracefully and exit cleanly without self-halt")
    elif result.exit_code != 0 or result.graceful_stop_sent or not result.self_halted:
        raise AtlasRecoveryExecutionError("recovery stage must self-halt and exit cleanly without an injected stop")
    previous = -1
    for event in result.marker_events:
        if not isinstance(event, TimedMarker) or not isinstance(event.timestamp_millis, int) \
                or isinstance(event.timestamp_millis, bool) or event.timestamp_millis <= previous:
            raise AtlasRecoveryExecutionError(f"{stage.name} stage markers were not genuinely timestamped in order")
        previous = event.timestamp_millis
    return MarkerLedger(stage.name, stage.marker_path, result.marker_events)


def _revalidate_candidate(plan: ExternalRuntimeAtlasRecoveryPlan, inspector: CandidateInspector) -> FrozenCandidateInspection:
    candidate = plan.smoke.candidate
    if plan.smoke.candidate_origin != "frozen-candidate" or not is_within(candidate.path, plan.frozen_candidate_root) \
            or candidate.path.is_symlink() or not candidate.path.is_file():
        raise AtlasRecoveryExecutionError("Atlas recovery requires the retained regular frozen candidate")
    try:
        observed = inspector(candidate.path, candidate.loader)
    except (OSError, FrozenCandidateError) as error:
        raise AtlasRecoveryExecutionError("frozen candidate failed its pre-runtime inspection") from error
    if observed.sha256 != candidate.sha256 or observed.loader != candidate.loader \
            or (candidate.declared_target_range is not None and observed.minecraft_range != candidate.declared_target_range):
        raise AtlasRecoveryExecutionError("frozen candidate changed after quick qualification")
    return observed


def _sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _validate_quick_terminal(
    raw: bytes,
    plan: ExternalRuntimeAtlasRecoveryPlan,
    canonical_cells: Mapping[str, Mapping[str, Any]],
    range_identities: Mapping[str, Mapping[str, str]],
) -> None:
    try:
        parsed = json.loads(raw.decode("utf-8"))
        if not isinstance(parsed, Mapping):
            raise TerminalEvidenceError("quick terminal evidence is not an object")
        terminal = validate_terminal_evidence(parsed, canonical_cells, range_identities)
    except (UnicodeError, json.JSONDecodeError, TerminalEvidenceError) as error:
        raise AtlasRecoveryExecutionError("quick terminal evidence is not a valid strict PASS record") from error
    expected = canonical_cells.get(plan.smoke.cell_id)
    if not isinstance(expected, Mapping) \
            or (expected.get("id"), expected.get("loader"), expected.get("minecraft_version")) != (
                plan.smoke.cell_id, plan.smoke.loader, plan.smoke.minecraft_version,
            ):
        raise AtlasRecoveryExecutionError("canonical quick cell does not match the Atlas recovery plan")
    if terminal.verdict != "PASS" or terminal.cell_id != plan.smoke.cell_id \
            or terminal.candidate_sha256 != plan.smoke.candidate.sha256:
        raise AtlasRecoveryExecutionError("quick terminal evidence does not bind this cell and frozen candidate")


def _result_payload(result: ExternalAtlasRecoveryResult) -> dict[str, Any]:
    return {
        "fixture": "atlas-prewarm-recovery",
        "cell_id": result.cell_id,
        "loader": result.loader,
        "minecraft_version": result.minecraft_version,
        "verdict": result.verdict.value,
        "reason": result.reason,
        "installer": None if result.installer is None else {
            "verdict": result.installer.verdict.value,
            "argv": list(result.installer.argv),
            "return_code": result.installer.return_code,
        },
        "downloads": [item.__dict__ for item in result.downloads],
        "mods": [item.__dict__ for item in result.mods],
        "qualification": result.qualification_summary,
    }


def _record(result: ExternalAtlasRecoveryResult, plan: ExternalRuntimeAtlasRecoveryPlan) -> ExternalAtlasRecoveryResult:
    markdown = (
        "# External Atlas prewarm recovery\n\n"
        f"- Verdict: `{result.verdict.value}`\n"
        f"- Cell: `{result.cell_id}`\n"
        f"- Loader: `{result.loader}`\n"
        f"- Reason: `{result.reason or 'none'}`\n"
    )
    json_path, markdown_path = write_terminal_report(plan.evidence_root, _result_payload(result), markdown, stem="terminal")
    return ExternalAtlasRecoveryResult(
        result.cell_id, result.loader, result.minecraft_version, result.verdict, result.reason,
        result.installer, result.downloads, result.mods, str(json_path), str(markdown_path),
        result.qualification_summary,
    )


def execute_external_runtime_atlas_recovery(
    plan: ExternalRuntimeAtlasRecoveryPlan,
    paths: QualificationPaths,
    run_id: str,
    *,
    canonical_cells: Mapping[str, Mapping[str, Any]],
    range_identities: Mapping[str, Mapping[str, str]],
    opener: UrlOpen = _no_redirect_urlopen,
    command_executor: CommandExecutor = None,  # type: ignore[assignment]
    stage_runner: StageRunner = _unimplemented_stage_runner,
    candidate_inspector: CandidateInspector = inspect_frozen_candidate,
    held_lock: QualificationLock | None = None,
    execution_source_provenance: Mapping[str, Any] | None = None,
) -> ExternalAtlasRecoveryResult:
    """Assemble, execute via injected stages, and validate one recovery fixture.

    The default stage runner intentionally raises.  This prevents accidental
    claims that planning/assembly alone proved the runtime recovery behaviour.
    """
    if command_executor is None:
        from minecraft_qualification_executor import execute_command
        command_executor = execute_command
    execution_provenance = _execution_source_provenance(execution_source_provenance)
    smoke = plan.smoke
    if plan.runtime_root != smoke.layout.root or plan.world_root != plan.runtime_root / "world" \
            or plan.evidence_root != paths.evidence_directory / f"nightly/{plan.fixture_root.name}" \
            or plan.lock_path != paths.lock_path or plan.fixture_root != paths.run_directory / f"nightly/{plan.fixture_root.name}":
        raise AtlasRecoveryExecutionError("Atlas recovery plan does not use the exact nightly fixture paths")
    _assert_contained(smoke, paths)
    if held_lock is None:
        lock_context = QualificationLock.acquire(plan.lock_path, run_id)
    else:
        held_lock.require_held_for(plan.lock_path, run_id)
        lock_context = nullcontext(held_lock)
    downloads: list[DownloadResult] = []
    copied: list[ModCopyResult] = []
    installer: ExecutedCommand | None = None
    with lock_context:
        if plan.evidence_root.exists() or plan.runtime_root.exists():
            raise AtlasRecoveryExecutionError("Atlas recovery fixture paths must be absent before assembly")
        _assert_no_symlink_components(paths.cell_root, paths.repository_root, "qualification cell")
        _revalidate_candidate(plan, candidate_inspector)
        quick_raw = _read_regular(
            plan.quick_terminal_evidence.path, plan.quick_evidence_root, "quick terminal evidence",
        )
        if _sha256_bytes(quick_raw) != plan.quick_terminal_evidence.sha256:
            raise AtlasRecoveryExecutionError("quick terminal evidence changed before nightly execution")
        _validate_quick_terminal(quick_raw, plan, canonical_cells, range_identities)
        create_contained_directories(paths)
        fresh = FreshRuntimeObservation(True, True, True)
        plan.runtime_root.mkdir(parents=True, exist_ok=False)
        downloads.append(fetch_pinned_https(smoke.minecraft_server, paths, opener=opener))
        for item in smoke.downloads:
            downloads.append(fetch_pinned_https(item, paths, opener=opener))
        record = CommandRecord(PhaseName.DEDICATED_SMOKE, smoke.installer.argv, smoke.installer.cwd, (), smoke.launch.timeout_seconds)
        installer = command_executor(record, paths, ordinal=1)
        if installer.verdict is not Verdict.PASS:
            return _record(ExternalAtlasRecoveryResult(
                smoke.cell_id, smoke.loader, smoke.minecraft_version, Verdict.FAIL, installer.reason,
                installer, tuple(downloads), (), None, None,
            ), plan)
        _assert_no_symlink_components(plan.runtime_root, paths.cell_root, "installed Atlas recovery runtime")
        _verify_launcher(smoke)
        _installed_minecraft_server(smoke)
        for file in smoke.files:
            _write_planned_file(file.path, file.contents, plan.runtime_root)
        _append_neoforge_headless_properties(plan)
        for mod in smoke.mods:
            copied.append(_copy_pinned_mod(mod, plan.runtime_root))
        _verify_exact_mod_inventory(smoke)

        first = stage_runner(plan.stages[0], plan, paths)
        interrupted_report_raw = _read_regular(plan.stages[0].runtime_report_path, plan.world_root, "interrupted runtime report")
        settings_raw = _read_regular(plan.settings_path, plan.world_root, "persisted RingWorld settings")
        interrupted_atlas_raw = _read_regular(plan.atlas_path, plan.world_root, "interrupted Atlas")
        _write_new(plan.stages[0].captured_report_path, interrupted_report_raw, plan.evidence_root, "interrupted report capture")
        _write_new(plan.evidence_root / "settings.dat", settings_raw, plan.evidence_root, "settings capture")
        _write_new(plan.stages[0].captured_atlas_path, interrupted_atlas_raw, plan.evidence_root, "interrupted Atlas capture")
        interrupted_report = _parse_report(interrupted_report_raw, plan.stages[0], plan)
        settings = parse_persisted_ring_settings(settings_raw, plan.settings_path)
        interrupted_atlas = parse_ring_terrain_atlas(interrupted_atlas_raw, plan.atlas_path)
        interrupted_ledger = _ledger(plan.stages[0], first, paths)
        _write_new(plan.stages[0].marker_path, json.dumps([item.__dict__ for item in interrupted_ledger.events]).encode("utf-8"), plan.evidence_root, "interrupted marker capture")

        recovery_input_raw = _read_regular(plan.atlas_path, plan.world_root, "recovery input Atlas")
        if recovery_input_raw != interrupted_atlas_raw:
            raise AtlasRecoveryExecutionError("recovery start Atlas differs from the captured interruption checkpoint")
        _write_new(plan.recovery_input_atlas_path, recovery_input_raw, plan.evidence_root, "recovery input Atlas capture")
        recovery_input = parse_ring_terrain_atlas(recovery_input_raw, plan.atlas_path)

        second = stage_runner(plan.stages[1], plan, paths)
        recovered_report_raw = _read_regular(plan.stages[1].runtime_report_path, plan.world_root, "recovered runtime report")
        recovered_atlas_raw = _read_regular(plan.atlas_path, plan.world_root, "recovered Atlas")
        _write_new(plan.stages[1].captured_report_path, recovered_report_raw, plan.evidence_root, "complete report capture")
        _write_new(plan.stages[1].captured_atlas_path, recovered_atlas_raw, plan.evidence_root, "complete Atlas capture")
        recovered_report = _parse_report(recovered_report_raw, plan.stages[1], plan)
        recovered_atlas = parse_ring_terrain_atlas(recovered_atlas_raw, plan.atlas_path)
        recovery_ledger = _ledger(plan.stages[1], second, paths)
        _write_new(plan.stages[1].marker_path, json.dumps([item.__dict__ for item in recovery_ledger.events]).encode("utf-8"), plan.evidence_root, "recovery marker capture")
        identity = QualificationIdentity(
            smoke.cell_id, smoke.loader, smoke.minecraft_version,
            smoke.candidate.sha256, plan.quick_terminal_evidence.sha256,
        )
        try:
            qualification = validate_atlas_recovery_qualification(
                {"id": smoke.cell_id, "loader": smoke.loader, "minecraft": {"version": smoke.minecraft_version}},
                identity,
                AtlasRecoveryEvidence(
                    plan.runtime_root, plan.world_root, plan.evidence_root, fresh, settings,
                    interrupted_report, recovered_report, interrupted_atlas, recovery_input, recovered_atlas,
                    interrupted_ledger, recovery_ledger, first.exit_code, second.exit_code,
                ),
            )
        except InvocationError as error:
            return _record(ExternalAtlasRecoveryResult(
                smoke.cell_id, smoke.loader, smoke.minecraft_version, Verdict.FAIL, "ATLAS_RECOVERY_CONTRACT:" + str(error),
                installer, tuple(downloads), tuple(copied), None, None,
            ), plan)
        summary = qualification.as_dict()
        summary["executionSourceProvenance"] = execution_provenance
        summary["captures"] = {
            "settings": {"path": str((plan.evidence_root / "settings.dat").relative_to(paths.cell_root)), "sha256": _sha256_bytes(settings_raw)},
            "interruptedReport": {"path": str(plan.stages[0].captured_report_path.relative_to(paths.cell_root)), "sha256": _sha256_bytes(interrupted_report_raw)},
            "interruptedAtlas": {"path": str(plan.stages[0].captured_atlas_path.relative_to(paths.cell_root)), "sha256": _sha256_bytes(interrupted_atlas_raw)},
            "recoveryInputAtlas": {"path": str(plan.recovery_input_atlas_path.relative_to(paths.cell_root)), "sha256": _sha256_bytes(recovery_input_raw)},
            "completeReport": {"path": str(plan.stages[1].captured_report_path.relative_to(paths.cell_root)), "sha256": _sha256_bytes(recovered_report_raw)},
            "completeAtlas": {"path": str(plan.stages[1].captured_atlas_path.relative_to(paths.cell_root)), "sha256": _sha256_bytes(recovered_atlas_raw)},
        }
        summary["stages"] = {
            "interrupted": {
                "exitCode": first.exit_code,
                "markers": [item.__dict__ for item in interrupted_ledger.events],
                "log": {"path": str(Path(first.server_log).relative_to(paths.cell_root)),
                        "sha256": _sha256_bytes(_read_regular(Path(first.server_log), paths.logs_directory, "interrupted stage log"))},
            },
            "recovery": {
                "exitCode": second.exit_code,
                "markers": [item.__dict__ for item in recovery_ledger.events],
                "log": {"path": str(Path(second.server_log).relative_to(paths.cell_root)),
                        "sha256": _sha256_bytes(_read_regular(Path(second.server_log), paths.logs_directory, "recovery stage log"))},
            },
        }
        return _record(ExternalAtlasRecoveryResult(
            smoke.cell_id, smoke.loader, smoke.minecraft_version, Verdict.PASS, None,
            installer, tuple(downloads), tuple(copied), None, None, summary,
        ), plan)


execute_external_atlas_recovery = execute_external_runtime_atlas_recovery
