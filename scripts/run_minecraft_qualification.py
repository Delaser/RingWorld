#!/usr/bin/env python3
"""Run the fail-closed, serial Minecraft quick-qualification matrix.

Only the source build/unit phase has an execution adapter today. Artifact,
shared-contract, and external-runtime smoke phases are deliberately injected
interfaces, not optimistic stubs. A non-dry run therefore records immutable
``INCOMPLETE`` evidence until all required adapters exist.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Callable, Mapping, Protocol, Sequence

from minecraft_qualification_executor import (
    QualificationExecutionError,
    QualificationLock,
    create_contained_directories,
    execute_command,
    new_run_id,
    write_terminal_report,
)
from minecraft_qualification_model import (
    CellReport,
    CommandRecord,
    EvidenceReference,
    InvocationError,
    MatrixReport,
    PhaseName,
    PhaseResult,
    QualificationPaths,
    Verdict,
    aggregate_verdict,
    cell_report_dict,
    plan_cell,
    plan_matrix,
    render_json,
    render_markdown,
    report_dict,
    require_safe_identifier,
    select_cells,
)
from validate_minecraft_version_matrix import validate_manifest


ROOT = Path(__file__).resolve().parents[1]
NO_PHASE_ADAPTER = "NO_PHASE_ADAPTER"
CELL_ABORTED_AFTER_FAILURE = "CELL_ABORTED_AFTER_FAILURE"


class ExecutionMode(str, Enum):
    DRY_RUN = "DRY_RUN"
    EXECUTE = "EXECUTE"


class CellExecutionState(str, Enum):
    PLANNED = "PLANNED"
    LOCKED = "LOCKED"
    RUNNING = "RUNNING"
    TERMINAL = "TERMINAL"


_ALLOWED_CELL_TRANSITIONS: Mapping[CellExecutionState, frozenset[CellExecutionState]] = {
    CellExecutionState.PLANNED: frozenset({CellExecutionState.LOCKED, CellExecutionState.TERMINAL}),
    CellExecutionState.LOCKED: frozenset({CellExecutionState.RUNNING, CellExecutionState.TERMINAL}),
    CellExecutionState.RUNNING: frozenset({CellExecutionState.TERMINAL}),
    CellExecutionState.TERMINAL: frozenset(),
}


def transition_cell_state(current: CellExecutionState, target: CellExecutionState) -> CellExecutionState:
    """Reject skipped/repeated state transitions in the serial executor."""
    if target not in _ALLOWED_CELL_TRANSITIONS[current]:
        raise QualificationExecutionError(f"invalid cell execution transition {current.value}->{target.value}")
    return target


@dataclass(frozen=True)
class PhaseAdapterContext:
    """The complete, bounded contract passed to a future phase adapter."""

    cell: Mapping[str, Any]
    paths: QualificationPaths
    phase: PhaseName
    command: CommandRecord | None
    ordinal: int


class PhaseAdapter(Protocol):
    def __call__(self, context: PhaseAdapterContext) -> PhaseResult: ...


RunIdFactory = Callable[[], str]


@dataclass(frozen=True)
class SourceProvenance:
    """Clean immutable inputs required before a qualification command may run."""

    commit: str
    branch: str
    upstream: str
    origin: str
    manifest_sha256: str
    gradle_wrapper_sha256: str
    java_version: str


class ProvenanceProvider(Protocol):
    def __call__(self, repository_root: Path, manifest_path: Path) -> SourceProvenance: ...


_FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
_EXPECTED_ORIGIN = "https://github.com/Delaser/RingWorld.git"


def _checked_text(argv: Sequence[str], repository_root: Path) -> str:
    try:
        result = subprocess.run(
            list(argv), cwd=repository_root, check=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise QualificationExecutionError(f"source provenance command failed: {argv[0]}") from error
    return result.stdout.strip()


def _sha256(path: Path) -> str:
    try:
        return hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as error:
        raise QualificationExecutionError(f"cannot hash required qualification input {path}") from error


def collect_source_provenance(repository_root: Path, manifest_path: Path) -> SourceProvenance:
    """Collect a strict local source identity without mutating or fetching it."""
    # Porcelain omits ignored generated roots but includes all source-side
    # untracked files. They could change what Gradle compiles, so they block.
    status = _checked_text(("git", "status", "--porcelain", "--untracked-files=all"), repository_root)
    if status:
        raise QualificationExecutionError("source tree is dirty or has untracked files; qualification requires a clean checkout")
    commit = _checked_text(("git", "rev-parse", "HEAD"), repository_root)
    upstream = _checked_text(("git", "rev-parse", "@{upstream}"), repository_root)
    branch = _checked_text(("git", "branch", "--show-current"), repository_root)
    origin = _checked_text(("git", "remote", "get-url", "origin"), repository_root)
    if not _FULL_SHA.fullmatch(commit) or not _FULL_SHA.fullmatch(upstream):
        raise QualificationExecutionError("source provenance does not contain full Git commit SHA-1 values")
    if not branch or commit != upstream:
        raise QualificationExecutionError("HEAD is not exactly synchronized with its upstream")
    if origin != _EXPECTED_ORIGIN:
        raise QualificationExecutionError("origin does not match the reviewed RingWorld repository")
    try:
        java = subprocess.run(
            ("java", "-version"), cwd=repository_root, check=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise QualificationExecutionError("Java 25 is required for qualification provenance") from error
    java_version = (java.stdout + java.stderr).strip().splitlines()[0] if (java.stdout + java.stderr).strip() else ""
    if not re.search(r"(?:version\s+\"?25(?:[.\"]|$)|openjdk\s+25(?:[.\s]|$))", java_version, re.IGNORECASE):
        raise QualificationExecutionError("qualification provenance requires Java 25")
    return SourceProvenance(
        commit=commit,
        branch=branch,
        upstream=upstream,
        origin=origin,
        manifest_sha256=_sha256(manifest_path),
        gradle_wrapper_sha256=_sha256(repository_root / "gradlew"),
        java_version=java_version,
    )


def provenance_evidence(provenance: SourceProvenance) -> tuple[EvidenceReference, ...]:
    return (
        EvidenceReference("git-commit", provenance.commit, f"branch {provenance.branch} matches upstream"),
        EvidenceReference("git-upstream", provenance.upstream, "exact upstream commit"),
        EvidenceReference("git-origin", provenance.origin, "reviewed public repository"),
        EvidenceReference("manifest-sha256", provenance.manifest_sha256, "pinned qualification matrix"),
        EvidenceReference("gradle-wrapper-sha256", provenance.gradle_wrapper_sha256, "reviewed wrapper input"),
        EvidenceReference("java-25", provenance.java_version, "required runtime toolchain"),
    )


def validate_source_provenance(provenance: SourceProvenance) -> SourceProvenance:
    """Validate injected provenance before it can authorize any process work."""
    if not _FULL_SHA.fullmatch(provenance.commit) or provenance.commit != provenance.upstream:
        raise QualificationExecutionError("qualification provenance requires a full commit equal to upstream")
    if provenance.origin != _EXPECTED_ORIGIN or not provenance.branch:
        raise QualificationExecutionError("qualification provenance has an unreviewed origin or branch")
    if not re.fullmatch(r"[0-9a-f]{64}", provenance.manifest_sha256):
        raise QualificationExecutionError("qualification provenance has an invalid manifest SHA-256")
    if not re.fullmatch(r"[0-9a-f]{64}", provenance.gradle_wrapper_sha256):
        raise QualificationExecutionError("qualification provenance has an invalid Gradle wrapper SHA-256")
    if not re.search(r"(?:version\s+\"?25(?:[.\"]|$)|openjdk\s+25(?:[.\s]|$))", provenance.java_version, re.IGNORECASE):
        raise QualificationExecutionError("qualification provenance does not prove Java 25")
    return provenance


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--tier", required=True, choices=("quick",), help="only quick is implemented")
    result.add_argument("--cell", action="append", default=[], help="explicit matrix cell ID; repeatable")
    result.add_argument("--all", action="store_true", help="select all manifest cells")
    result.add_argument("--all-supported", action="store_true", help="select passing and published cells")
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json", help="manifest JSON path")
    result.add_argument("--jobs", type=int, default=1, help="must remain 1 until a safe parallel scheduler exists")
    result.add_argument("--fail-fast", action="store_true", help="accepted for compatibility; execution is always fail-fast")
    result.add_argument("--resume", action="store_true", help="not implemented; immutable evidence cannot be safely resumed yet")
    result.add_argument("--dry-run", action="store_true", help="validate and plan only; performs no writes or process work")
    return result


def load_manifest(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise InvocationError(f"cannot read manifest {path}: {error}") from error
    if not isinstance(value, dict):
        raise InvocationError("manifest root must be an object")
    errors = validate_manifest(value)
    if errors:
        raise InvocationError("invalid manifest:\n" + "\n".join(f"- {error}" for error in errors))
    return value


def _phase_command(report: CellReport, phase: PhaseName) -> CommandRecord | None:
    matches = [command for item in report.phases for command in item.commands if command.phase is phase]
    if len(matches) > 1:
        raise InvocationError(f"planned cell {report.cell_id} has duplicate {phase.value} commands")
    return matches[0] if matches else None


def build_and_unit_adapter(context: PhaseAdapterContext) -> PhaseResult:
    """Execute the reviewed Gradle build/unit command through the safe primitive."""
    if context.phase is not PhaseName.BUILD_AND_UNIT or context.command is None:
        raise QualificationExecutionError("build/unit adapter received an invalid phase context")
    executed = execute_command(context.command, context.paths, ordinal=context.ordinal)
    evidence = (
        EvidenceReference("command-stdout", executed.stdout_log, "bounded, redacted process stdout"),
        EvidenceReference("command-stderr", executed.stderr_log, "bounded, redacted process stderr"),
        EvidenceReference("command-exit", str(executed.return_code), executed.reason or "EXIT_0"),
    )
    return PhaseResult(context.phase, executed.verdict, executed.reason, (context.command,), evidence)


def default_phase_adapters() -> Mapping[PhaseName, PhaseAdapter]:
    """Return only adapters whose proof contract is implemented today."""
    return {PhaseName.BUILD_AND_UNIT: build_and_unit_adapter}


def _unavailable_phase(phase: PhaseName, command: CommandRecord | None, reason: str = NO_PHASE_ADAPTER) -> PhaseResult:
    return PhaseResult(phase, Verdict.INCOMPLETE, reason, (command,) if command else ())


def _failed_after_abort(phase: PhaseName, command: CommandRecord | None) -> PhaseResult:
    return PhaseResult(phase, Verdict.INCOMPLETE, CELL_ABORTED_AFTER_FAILURE, (command,) if command else ())


def _cell_with_phases(planned: CellReport, phases: Sequence[PhaseResult]) -> CellReport:
    phase_tuple = tuple(phases)
    return CellReport(
        cell_id=planned.cell_id,
        minecraft_version=planned.minecraft_version,
        loader=planned.loader,
        run_id=planned.run_id,
        verdict=aggregate_verdict(phase_tuple),
        phases=phase_tuple,
        paths=planned.paths,
        downloads=planned.downloads,
        artifacts=planned.artifacts,
    )


def _with_provenance(planned: CellReport, provenance: SourceProvenance) -> CellReport:
    phases = tuple(
        PhaseResult(
            phase.phase,
            phase.verdict,
            phase.reason,
            phase.commands,
            phase.evidence + provenance_evidence(provenance)
            if phase.phase is PhaseName.MANIFEST_VALIDATION else phase.evidence,
        )
        for phase in planned.phases
    )
    return _cell_with_phases(planned, phases)


def _matrix_evidence_directory(repository_root: Path, run_id: str) -> Path:
    require_safe_identifier(run_id, "run id")
    root = repository_root.resolve(strict=False) / "dist" / "qualification" / "matrix" / run_id
    if root.exists():
        raise QualificationExecutionError(f"matrix evidence directory already exists: {root}")
    return root


def _execution_error_cell(planned: CellReport, phases: Sequence[PhaseResult], error: BaseException) -> CellReport:
    """Turn any orchestration/evidence failure into an unambiguous non-PASS cell."""
    existing = {phase.phase: phase for phase in phases}
    completed = [
        existing.get(
            planned_phase.phase,
            PhaseResult(
                planned_phase.phase,
                Verdict.FAIL,
                f"EXECUTION_ERROR:{error.__class__.__name__}",
                planned_phase.commands,
            ),
        )
        for planned_phase in planned.phases
    ]
    if all(phase.verdict is Verdict.PASS for phase in completed):
        # This can only happen after a terminal-evidence failure. A report
        # without its immutable cell file must never remain a pass.
        last = completed[-1]
        completed[-1] = PhaseResult(
            last.phase,
            Verdict.FAIL,
            f"EVIDENCE_ERROR:{error.__class__.__name__}",
            last.commands,
            last.evidence,
        )
    return _cell_with_phases(planned, completed)


def _write_cell_report(report: CellReport) -> None:
    write_terminal_report(
        report.paths.evidence_directory,
        cell_report_dict(report),
        render_markdown(MatrixReport(1, "quick", False, report.run_id, report.verdict, (report,))),
        stem="cell-report",
    )


def execute_quick_matrix(
    cells: Sequence[Mapping[str, Any]],
    repository_root: Path,
    *,
    run_id_factory: RunIdFactory = new_run_id,
    phase_adapters: Mapping[PhaseName, PhaseAdapter] | None = None,
    provenance_provider: ProvenanceProvider = collect_source_provenance,
    manifest_path: Path | None = None,
) -> MatrixReport:
    """Execute serial cell work and leave immutable terminal evidence.

    The scheduler is intentionally serial. Any non-PASS phase makes the cell
    terminal non-PASS; a concrete failure halts later cells. Missing adapters
    are *incomplete*, never passes. The adapter mapping makes integration
    tests and future proof phases injectable without changing the core runner.
    """
    run_id = run_id_factory()
    if not isinstance(run_id, str):
        raise QualificationExecutionError("run-id factory returned a non-string value")
    adapters = dict(default_phase_adapters() if phase_adapters is None else phase_adapters)
    provenance = validate_source_provenance(provenance_provider(
        repository_root,
        manifest_path if manifest_path is not None else repository_root / "config" / "minecraft-version-matrix.json",
    ))
    matrix_evidence = _matrix_evidence_directory(repository_root, run_id)
    reports: list[CellReport] = []
    fail_fast_triggered = False

    for cell in cells:
        planned = _with_provenance(plan_cell(cell, repository_root, run_id, dry_run=False), provenance)
        paths = planned.paths
        if fail_fast_triggered:
            skipped = _cell_with_phases(
                planned,
                tuple(
                    planned_phase if planned_phase.phase in {PhaseName.MANIFEST_VALIDATION, PhaseName.INPUT_PLAN}
                    else _failed_after_abort(planned_phase.phase, _phase_command(planned, planned_phase.phase))
                    for planned_phase in planned.phases
                ),
            )
            try:
                with QualificationLock.acquire(paths.lock_path, run_id):
                    if paths.cell_root.exists():
                        raise QualificationExecutionError(
                            f"qualification cell output already exists and cannot be reused: {paths.cell_root}"
                        )
                    create_contained_directories(paths)
                    _write_cell_report(skipped)
            except (QualificationExecutionError, InvocationError, OSError) as error:
                skipped = _execution_error_cell(planned, skipped.phases, error)
            reports.append(skipped)
            continue

        phases: list[PhaseResult] = []
        failed = False
        state = CellExecutionState.PLANNED
        try:
            with QualificationLock.acquire(paths.lock_path, run_id):
                state = transition_cell_state(state, CellExecutionState.LOCKED)
                if paths.cell_root.exists():
                    raise QualificationExecutionError(
                        f"qualification cell output already exists and cannot be reused: {paths.cell_root}"
                    )
                create_contained_directories(paths)
                state = transition_cell_state(state, CellExecutionState.RUNNING)
                for planned_phase in planned.phases:
                    phase = planned_phase.phase
                    if phase in {PhaseName.MANIFEST_VALIDATION, PhaseName.INPUT_PLAN}:
                        phases.append(planned_phase)
                        continue
                    command = _phase_command(planned, phase)
                    if failed:
                        phases.append(_failed_after_abort(phase, command))
                        continue
                    adapter = adapters.get(phase)
                    if adapter is None:
                        phases.append(_unavailable_phase(phase, command))
                        continue
                    result = adapter(PhaseAdapterContext(cell, paths, phase, command, len(phases) + 1))
                    if result.phase is not phase:
                        raise QualificationExecutionError("phase adapter returned evidence for a different phase")
                    phases.append(result)
                    failed = result.verdict is Verdict.FAIL
                report = _cell_with_phases(planned, phases)
                _write_cell_report(report)
                state = transition_cell_state(state, CellExecutionState.TERMINAL)
        except (QualificationExecutionError, InvocationError, OSError) as error:
            report = _execution_error_cell(planned, phases, error)
        reports.append(report)
        fail_fast_triggered = report.verdict is Verdict.FAIL

    matrix = MatrixReport(1, "quick", False, run_id, aggregate_verdict(
        tuple(PhaseResult(PhaseName.MANIFEST_VALIDATION, cell.verdict, evidence=(EvidenceReference("cell-report", str(cell.paths.evidence_directory / "cell-report.json"), cell.verdict.value),)) for cell in reports)
    ), tuple(reports))
    write_terminal_report(matrix_evidence, report_dict(matrix), render_markdown(matrix), stem="matrix-report")
    return matrix


def main(argv: list[str] | None = None, *, repository_root: Path = ROOT) -> int:
    args = parser().parse_args(argv)
    if args.jobs != 1:
        print("INVOCATION ERROR: --jobs must be exactly 1 until a safe parallel scheduler exists", file=sys.stderr)
        return 2
    if args.resume:
        print("INVOCATION ERROR: --resume is not implemented; immutable evidence cannot be safely resumed", file=sys.stderr)
        return 2
    try:
        manifest_path = (repository_root / args.manifest).resolve(strict=False) if not Path(args.manifest).is_absolute() else Path(args.manifest)
        manifest = load_manifest(manifest_path)
        cells = select_cells(manifest, args.cell, all_cells=args.all, all_supported=args.all_supported)
        report = (
            plan_matrix(cells, repository_root, "dry-run", dry_run=True)
            if args.dry_run
            else execute_quick_matrix(cells, repository_root, manifest_path=manifest_path)
        )
    except (InvocationError, QualificationExecutionError, OSError) as error:
        print(f"INVOCATION ERROR: {error}", file=sys.stderr)
        return 2
    print(render_markdown(report), end="")
    print(render_json(report), end="")
    return 0 if report.verdict is Verdict.PASS else 1


if __name__ == "__main__":
    raise SystemExit(main())
