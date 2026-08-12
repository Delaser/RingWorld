#!/usr/bin/env python3
"""Run the fail-closed, serial Minecraft quick-qualification matrix.

Source build/unit, diagnostic-artifact, and frozen same-file preparation have
execution adapters. The frozen candidate is built once per loader against the
oldest ABI and only exposed to a complete three-version loader triplet.
External-runtime smoke is installed only after a complete frozen loader
triplet and clean provenance exist; partial selections remain ``INCOMPLETE``
without runtime I/O.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass, replace
from enum import Enum
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Any, Callable, Mapping, Protocol, Sequence

from minecraft_qualification_executor import (
    PackageVerificationError,
    QualificationExecutionError,
    QualificationLock,
    create_contained_directories,
    execute_command,
    inspect_runtime_jar,
    new_run_id,
    write_terminal_report,
)
from minecraft_frozen_candidate import (
    EXPECTED_VERSIONS,
    FrozenCandidateError,
    FrozenCandidateInspection,
    inspect_frozen_candidate,
    verify_same_file_coverage,
)
from minecraft_qualification_ranges import (
    APPROVED_FABRIC_MINECRAFT_RANGE,
    APPROVED_NEOFORGE_LOADER_RANGE,
    APPROVED_NEOFORGE_MINECRAFT_RANGE,
)
from minecraft_qualification_model import (
    ArtifactEvidence,
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
    qualification_port,
    required_dependency_properties,
    render_json,
    render_markdown,
    report_dict,
    require_safe_identifier,
    is_within,
    select_cells,
)
from validate_minecraft_version_matrix import validate_manifest


ROOT = Path(__file__).resolve().parents[1]
NO_PHASE_ADAPTER = "NO_PHASE_ADAPTER"
CELL_ABORTED_AFTER_FAILURE = "CELL_ABORTED_AFTER_FAILURE"
SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET = "SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET"
FROZEN_CANDIDATE_PREPARATION_FAILED = "FROZEN_CANDIDATE_PREPARATION_FAILED"
FROZEN_PREFLIGHT_ABORTED = "FROZEN_CANDIDATE_PREFLIGHT_ABORTED"


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
    held_lock: QualificationLock | None = None


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


@dataclass(frozen=True)
class FrozenCandidatePlan:
    """One loader-wide candidate built from the oldest 26.1 ABI once.

    The candidate is not a cell build.  It has a synthetic, isolated Gradle
    cell solely so Gradle cannot write into a real matrix cell, then its one
    inspected runtime jar is frozen below the oldest cell's qualification run
    root.  Every loader cell refers to that one frozen pathname and hash.
    """

    loader: str
    source_cell_id: str
    paths: QualificationPaths
    command: CommandRecord
    source_runtime_directory: Path
    candidate_path: Path


@dataclass(frozen=True)
class FrozenCandidatePreparation:
    """Terminal outcome that the per-cell shared-contract adapter may cite."""

    loader: str
    verdict: Verdict
    reason: str | None
    plan: FrozenCandidatePlan | None = None
    inspection: FrozenCandidateInspection | None = None
    evidence: tuple[EvidenceReference, ...] = ()
    artifacts: tuple[ArtifactEvidence, ...] = ()


def _direct_runtime_jar(root: Path) -> Path:
    """Return one direct runtime jar while allowing Gradle's normal sources jar.

    The generic distribution helper intentionally rejects *any* sources jar;
    Gradle's `build` task always produces one next to the runtime jar.  This
    qualification-only selector keeps the stricter distribution helper intact
    and accepts that normal layout only when there is exactly one direct,
    non-symlink, non-sources runtime jar and no nested or escaping artifacts.
    """
    if not root.is_dir() or root.is_symlink():
        raise PackageVerificationError("isolated runtime-jar directory does not exist or is a symlink")
    direct = tuple(sorted(root.glob("*.jar")))
    nested = tuple(candidate for candidate in root.rglob("*.jar") if candidate.parent != root)
    if nested:
        raise PackageVerificationError("runtime jar output contains nested jar files")
    if any(not candidate.is_file() or candidate.is_symlink() or not is_within(candidate, root) for candidate in direct):
        raise PackageVerificationError("runtime jar output contains unsafe jar entries")
    runtime = tuple(candidate for candidate in direct if "-sources" not in candidate.name.lower())
    if len(runtime) != 1:
        raise PackageVerificationError("expected exactly one direct non-sources runtime jar")
    sources = tuple(candidate for candidate in direct if candidate is not runtime[0])
    expected_sources_name = f"{runtime[0].stem}-sources.jar"
    if len(sources) > 1 or (sources and sources[0].name != expected_sources_name):
        raise PackageVerificationError("runtime jar output contains an unexpected sources jar")
    return runtime[0]


def _frozen_properties(cell: Mapping[str, Any], paths: QualificationPaths) -> tuple[tuple[str, str], ...]:
    """Return the deliberately broader, reviewed metadata only for a frozen jar."""
    loader = cell.get("loader")
    minecraft = cell.get("minecraft")
    if loader not in {"fabric", "neoforge"} or not isinstance(minecraft, Mapping) or minecraft.get("version") != "26.1":
        raise QualificationExecutionError("a frozen candidate must use the oldest 26.1 matrix cell")
    common = (
        ("ringQualificationRoot", str(paths.run_root)),
        ("ringQualificationCell", paths.cell_id),
        ("ringQualificationPort", str(qualification_port(cell))),
        ("minecraft_version", "26.1"),
        ("mod_version", "0.0.0-qualification+mc26.1"),
        ("release_label", f"qualification-26.1-{loader}"),
        *required_dependency_properties(cell),
    )
    if loader == "fabric":
        return (*common, ("ringQualificationMinecraftRange", APPROVED_FABRIC_MINECRAFT_RANGE))
    return (*common,
            ("ringQualificationMinecraftRange", APPROVED_NEOFORGE_MINECRAFT_RANGE),
            ("ringQualificationNeoForgeRange", APPROVED_NEOFORGE_LOADER_RANGE))


def frozen_candidate_plan(cell: Mapping[str, Any], repository_root: Path, run_id: str) -> FrozenCandidatePlan:
    """Purely plan the contained oldest-ABI candidate build for one loader."""
    source_paths = QualificationPaths.from_cell(repository_root, cell, run_id)
    loader = cell.get("loader")
    if loader not in {"fabric", "neoforge"}:
        raise InvocationError("frozen candidate has an unsupported loader")
    synthetic_id = f"frozen-{loader}"
    synthetic_root = source_paths.run_root / synthetic_id
    if not is_within(synthetic_root, source_paths.run_root):
        raise InvocationError("frozen candidate output escapes its qualification run")
    paths = replace(
        source_paths,
        cell_id=synthetic_id,
        cell_root=synthetic_root,
        gradle_home=synthetic_root / "gradle-home",
        run_directory=synthetic_root / "run",
        cache_directory=synthetic_root / "cache",
        build_directory=synthetic_root / "build",
        evidence_directory=synthetic_root / "evidence",
        logs_directory=synthetic_root / "logs",
        world_directory=synthetic_root / "world",
        lock_path=source_paths.lock_path.with_name(f"{synthetic_id}.lock"),
    )
    if any(not is_within(value, source_paths.run_root) for value in (
        paths.cell_root, paths.gradle_home, paths.run_directory, paths.cache_directory,
        paths.build_directory, paths.evidence_directory, paths.logs_directory, paths.world_directory,
    )) or not is_within(paths.lock_path, repository_root / "dist" / "qualification" / ".locks"):
        raise InvocationError("frozen candidate path plan escapes its reviewed roots")
    profile = cell.get("profile")
    if not isinstance(profile, Mapping) or not isinstance(profile.get("timeout_seconds"), int):
        raise InvocationError("frozen candidate source cell has no timeout")
    properties = _frozen_properties(cell, paths)
    property_args = tuple(f"-P{name}={value}" for name, value in properties)
    tasks = (":test", ":build") if loader == "fabric" else (":neoforge:test", ":neoforge:build")
    command = CommandRecord(
        PhaseName.SHARED_CONTRACT,
        (
            str(repository_root / "gradlew"), "--console=plain", "--no-daemon", "--max-workers=1",
            *property_args, *tasks,
        ),
        repository_root,
        (("GRADLE_USER_HOME", str(paths.gradle_home)),),
        profile["timeout_seconds"],
    )
    candidate_path = source_paths.run_root / "frozen-candidates" / loader / "ringworld-qualification.jar"
    if not is_within(candidate_path, source_paths.run_root):
        raise InvocationError("frozen candidate storage escapes its qualification run")
    return FrozenCandidatePlan(
        loader, str(cell.get("id", "")), paths, command,
        paths.build_directory / loader / "libs", candidate_path,
    )


def _freeze_candidate(source: Path, destination: Path, loader: str) -> FrozenCandidateInspection:
    """Atomically retain and re-inspect one wide-range candidate below the run."""
    if destination.exists() or destination.is_symlink():
        raise QualificationExecutionError("frozen candidate destination already exists or is unsafe")
    destination.parent.mkdir(parents=True, exist_ok=False)
    try:
        # Copy rather than hard-link: Gradle's output remains mutable build
        # state, while the retained candidate is the exact input later copied
        # into every external runtime.
        with source.open("rb") as input_file, destination.open("xb") as output_file:
            shutil.copyfileobj(input_file, output_file, length=1024 * 1024)
            output_file.flush()
            os.fsync(output_file.fileno())
        inspection = inspect_frozen_candidate(destination, loader)
        source_hash = _sha256(source)
        if inspection.sha256 != source_hash:
            raise QualificationExecutionError("frozen candidate copy differs from its inspected source jar")
        return inspection
    except Exception:
        try:
            destination.unlink()
        except FileNotFoundError:
            pass
        try:
            destination.parent.rmdir()
        except OSError:
            pass
        raise


def _full_loader_triplet(cells: Sequence[Mapping[str, Any]], loader: str) -> tuple[Mapping[str, Any], ...] | None:
    selected = tuple(cell for cell in cells if cell.get("loader") == loader)
    expected_ids = {f"{version}-{loader}" for version in EXPECTED_VERSIONS}
    if {cell.get("id") for cell in selected} != expected_ids or len(selected) != len(EXPECTED_VERSIONS):
        return None
    oldest = next((cell for cell in selected if cell.get("id") == f"26.1-{loader}"), None)
    if oldest is None:
        return None
    return tuple(sorted(selected, key=lambda cell: str(cell["id"])))


def prepare_frozen_candidates(
    cells: Sequence[Mapping[str, Any]], repository_root: Path, run_id: str,
) -> Mapping[str, FrozenCandidatePreparation]:
    """Build and freeze at most one candidate per complete loader triplet.

    Missing cells never cause a partial same-file claim: their loader retains
    an explicit `INCOMPLETE` shared-contract result.  This preflight itself
    does not make any quick cell pass; dedicated runtime evidence remains a
    separately unavailable phase.
    """
    preparations: dict[str, FrozenCandidatePreparation] = {}
    for loader in ("fabric", "neoforge"):
        triplet = _full_loader_triplet(cells, loader)
        if triplet is None:
            preparations[loader] = FrozenCandidatePreparation(
                loader, Verdict.INCOMPLETE, SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET,
            )
            continue
        source = next(cell for cell in triplet if cell["id"] == f"26.1-{loader}")
        plan: FrozenCandidatePlan | None = None
        try:
            plan = frozen_candidate_plan(source, repository_root, run_id)
            with QualificationLock.acquire(plan.paths.lock_path, run_id):
                if plan.paths.cell_root.exists() or plan.candidate_path.exists():
                    raise QualificationExecutionError("frozen candidate output already exists and cannot be reused")
                create_contained_directories(plan.paths)
                executed = execute_command(plan.command, plan.paths, ordinal=1)
                if executed.verdict is not Verdict.PASS:
                    preparations[loader] = FrozenCandidatePreparation(
                        loader, Verdict.FAIL, f"{FROZEN_CANDIDATE_PREPARATION_FAILED}:{executed.reason or 'BUILD_FAILED'}", plan,
                        evidence=(
                            EvidenceReference("frozen-build-stdout", executed.stdout_log, "bounded, redacted oldest-ABI build stdout"),
                            EvidenceReference("frozen-build-stderr", executed.stderr_log, "bounded, redacted oldest-ABI build stderr"),
                        ),
                    )
                    continue
                source_jar = _direct_runtime_jar(plan.source_runtime_directory)
                # Inspect the exact retained jar, not merely Gradle's output.
                inspection = _freeze_candidate(source_jar, plan.candidate_path, loader)
                coverage = verify_same_file_coverage(
                    loader, {f"{version}-{loader}": inspection for version in EXPECTED_VERSIONS},
                )
                artifact = ArtifactEvidence(str(plan.candidate_path), "sha256", inspection.sha256, inspection.sha256, True)
                preparations[loader] = FrozenCandidatePreparation(
                    loader, Verdict.PASS, None, plan, inspection,
                    evidence=(
                        EvidenceReference("frozen-build-stdout", executed.stdout_log, "bounded, redacted oldest-ABI build stdout"),
                        EvidenceReference("frozen-build-stderr", executed.stderr_log, "bounded, redacted oldest-ABI build stderr"),
                        EvidenceReference("frozen-candidate", str(plan.candidate_path), "one directly MPL-inspected oldest-ABI candidate"),
                        EvidenceReference("frozen-candidate-sha256", inspection.sha256, "retained candidate hash"),
                        EvidenceReference("same-file-coverage", ",".join(coverage.cell_ids), "one path/hash covers the complete loader triplet"),
                    ),
                    artifacts=(artifact,),
                )
        except (OSError, FrozenCandidateError, PackageVerificationError, QualificationExecutionError, InvocationError) as error:
            preparations[loader] = FrozenCandidatePreparation(
                loader, Verdict.FAIL, f"{FROZEN_CANDIDATE_PREPARATION_FAILED}:{error.__class__.__name__}", plan,
            )
    return preparations


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


def artifact_verify_adapter(context: PhaseAdapterContext) -> PhaseResult:
    """Inspect exactly one isolated diagnostic runtime jar after a source build.

    This deliberately verifies the *per-cell* source-build diagnostic jar. It
    cannot establish the later same-file frozen candidate claim, which has a
    different wide-range metadata identity and needs cross-cell evidence.
    """
    if context.phase is not PhaseName.ARTIFACT_VERIFY or context.command is not None:
        raise QualificationExecutionError("artifact adapter received an invalid phase context")
    loader = context.cell.get("loader")
    minecraft = context.cell.get("minecraft")
    if not isinstance(loader, str) or not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str):
        raise QualificationExecutionError("artifact adapter received an invalid manifest cell")
    artifact_root = context.paths.build_directory / loader / "libs"
    try:
        jar = _direct_runtime_jar(artifact_root)
        if not is_within(jar, artifact_root):
            raise PackageVerificationError("runtime jar resolves outside the isolated cell build output")
        diagnostic_version = f"0.0.0-qualification+mc{minecraft['version']}"
        inspection = inspect_runtime_jar(
            jar,
            loader=loader,
            minecraft_version=minecraft["version"],
            diagnostic_version=diagnostic_version,
        )
        sha256 = _sha256(jar)
    except (OSError, PackageVerificationError, QualificationExecutionError) as error:
        return PhaseResult(context.phase, Verdict.FAIL, f"ARTIFACT_VERIFY_FAILED:{error.__class__.__name__}")
    artifact = ArtifactEvidence(str(jar), "sha256", sha256, sha256, True)
    evidence = (
        EvidenceReference("runtime-jar", str(jar), "exactly one loader runtime jar under the isolated build output"),
        EvidenceReference("runtime-jar-sha256", sha256, "computed SHA-256 of the inspected jar"),
        EvidenceReference("jar-metadata", inspection.metadata_entry, f"strict {loader} metadata identity"),
        EvidenceReference("jar-license", inspection.license_entry, "embedded MPL-2.0 RingWorld license"),
        EvidenceReference("jar-build-identity", diagnostic_version, "strict diagnostic artifact/release-label identity"),
    )
    return PhaseResult(context.phase, Verdict.PASS, evidence=evidence, artifacts=(artifact,))


def shared_contract_adapter(
    preparations: Mapping[str, FrozenCandidatePreparation],
) -> PhaseAdapter:
    """Expose one prepared loader-wide candidate to each complete-triplet cell.

    This adapter intentionally does not infer a broad support verdict from
    metadata alone.  It establishes only the byte-identical candidate input
    required by the later external runtime phase.
    """
    def adapter(context: PhaseAdapterContext) -> PhaseResult:
        if context.phase is not PhaseName.SHARED_CONTRACT or context.command is not None:
            raise QualificationExecutionError("shared-contract adapter received an invalid phase context")
        loader = context.cell.get("loader")
        preparation = preparations.get(loader) if isinstance(loader, str) else None
        if preparation is None:
            return PhaseResult(context.phase, Verdict.INCOMPLETE, SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET)
        if preparation.verdict is Verdict.PASS:
            if preparation.plan is None or preparation.inspection is None:
                raise QualificationExecutionError("passing frozen preparation has no candidate identity")
            cell_id = context.cell.get("id")
            expected_ids = {f"{version}-{loader}" for version in EXPECTED_VERSIONS}
            if cell_id not in expected_ids:
                return PhaseResult(context.phase, Verdict.INCOMPLETE, SHARED_CONTRACT_REQUIRES_FULL_LOADER_TRIPLET)
            evidence = preparation.evidence + (
                EvidenceReference("same-file-cell", str(cell_id), "references the one retained loader candidate"),
            )
            return PhaseResult(
                context.phase, Verdict.PASS, commands=(preparation.plan.command,),
                evidence=evidence, artifacts=preparation.artifacts,
            )
        return PhaseResult(context.phase, preparation.verdict, preparation.reason, evidence=preparation.evidence)
    return adapter


def default_phase_adapters(
    preparations: Mapping[str, FrozenCandidatePreparation] | None = None,
) -> Mapping[PhaseName, PhaseAdapter]:
    """Return only adapters whose proof contract is implemented today."""
    return {
        PhaseName.BUILD_AND_UNIT: build_and_unit_adapter,
        PhaseName.ARTIFACT_VERIFY: artifact_verify_adapter,
        PhaseName.SHARED_CONTRACT: shared_contract_adapter(preparations or {}),
    }


def _unavailable_phase(phase: PhaseName, command: CommandRecord | None, reason: str = NO_PHASE_ADAPTER) -> PhaseResult:
    return PhaseResult(phase, Verdict.INCOMPLETE, reason, (command,) if command else ())


def _failed_after_abort(phase: PhaseName, command: CommandRecord | None) -> PhaseResult:
    return PhaseResult(phase, Verdict.INCOMPLETE, CELL_ABORTED_AFTER_FAILURE, (command,) if command else ())


def _frozen_preflight_aborted(phase: PhaseName, command: CommandRecord | None) -> PhaseResult:
    """Record a diagnostic phase deliberately skipped by a failed shared preflight."""
    return PhaseResult(phase, Verdict.INCOMPLETE, FROZEN_PREFLIGHT_ABORTED, (command,) if command else ())


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
        artifacts=planned.artifacts + tuple(
            artifact for phase in phase_tuple for artifact in phase.artifacts
        ),
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


def _frozen_preflight_failures(
    cells: Sequence[Mapping[str, Any]],
    preparations: Mapping[str, FrozenCandidatePreparation],
) -> Mapping[str, FrozenCandidatePreparation]:
    """Return failed preparations only for selected complete loader triplets.

    A partial selection intentionally remains an ``INCOMPLETE`` shared-contract
    result and must still run its source-build diagnostics.  A failed *complete*
    triplet instead makes those diagnostics pointless: they cannot repair the
    retained common candidate needed by every one of its cells.
    """
    return {
        loader: preparation
        for loader, preparation in preparations.items()
        if getattr(preparation, "verdict", None) is Verdict.FAIL
        and _full_loader_triplet(cells, loader) is not None
    }


def _preflight_failure_cell(
    planned: CellReport,
    preparation: FrozenCandidatePreparation,
) -> CellReport:
    """Render the concrete shared-contract failure without source-build work."""
    phases: list[PhaseResult] = []
    for planned_phase in planned.phases:
        phase = planned_phase.phase
        command = _phase_command(planned, phase)
        if phase in {PhaseName.MANIFEST_VALIDATION, PhaseName.INPUT_PLAN}:
            phases.append(planned_phase)
        elif phase in {PhaseName.BUILD_AND_UNIT, PhaseName.ARTIFACT_VERIFY}:
            phases.append(_frozen_preflight_aborted(phase, command))
        elif phase is PhaseName.SHARED_CONTRACT:
            phases.append(PhaseResult(
                phase,
                Verdict.FAIL,
                preparation.reason or FROZEN_CANDIDATE_PREPARATION_FAILED,
                evidence=preparation.evidence,
                artifacts=preparation.artifacts,
            ))
        else:
            phases.append(_failed_after_abort(phase, command))
    return _cell_with_phases(planned, phases)


def _write_preplanned_cell_report(planned: CellReport, report: CellReport, run_id: str) -> CellReport:
    """Create and write one terminal preflight-aborted cell report under its lock."""
    paths = planned.paths
    try:
        with QualificationLock.acquire(paths.lock_path, run_id):
            if paths.cell_root.exists():
                raise QualificationExecutionError(
                    f"qualification cell output already exists and cannot be reused: {paths.cell_root}"
                )
            create_contained_directories(paths)
            _write_cell_report(report)
    except (QualificationExecutionError, InvocationError, OSError) as error:
        return _execution_error_cell(planned, report.phases, error)
    return report


def execute_quick_matrix(
    cells: Sequence[Mapping[str, Any]],
    repository_root: Path,
    *,
    run_id_factory: RunIdFactory = new_run_id,
    phase_adapters: Mapping[PhaseName, PhaseAdapter] | None = None,
    frozen_preparation_provider: Callable[[Sequence[Mapping[str, Any]], Path, str], Mapping[str, FrozenCandidatePreparation]] = prepare_frozen_candidates,
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
    provenance = validate_source_provenance(provenance_provider(
        repository_root,
        manifest_path if manifest_path is not None else repository_root / "config" / "minecraft-version-matrix.json",
    ))
    matrix_evidence = _matrix_evidence_directory(repository_root, run_id)
    # Production execution prepares the one immutable oldest-ABI candidate
    # before any per-cell source diagnostics. Tests that inject all adapters
    # remain hermetic; an omitted shared adapter stays explicitly incomplete.
    preparations = frozen_preparation_provider(cells, repository_root, run_id) if phase_adapters is None else {}
    preflight_failures = _frozen_preflight_failures(cells, preparations)
    if phase_adapters is None:
        # Local import preserves the adapter's one-way structural dependency:
        # it accepts runner values but never imports the scheduler.
        from external_runtime_qualification_adapter import external_runtime_adapter_from_qualification_inputs

        adapters = dict(default_phase_adapters(preparations))
        if not preflight_failures:
            adapters[PhaseName.DEDICATED_SMOKE] = external_runtime_adapter_from_qualification_inputs(
                cells, provenance, preparations,
            )
    else:
        adapters = dict(phase_adapters)
    reports: list[CellReport] = []
    fail_fast_triggered = False
    preflight_failure_recorded = False

    for cell in cells:
        planned = _with_provenance(plan_cell(cell, repository_root, run_id, dry_run=False), provenance)
        paths = planned.paths
        loader = cell.get("loader")
        if preflight_failures:
            if not preflight_failure_recorded and isinstance(loader, str) and loader in preflight_failures:
                report = _preflight_failure_cell(planned, preflight_failures[loader])
                preflight_failure_recorded = True
            else:
                report = _cell_with_phases(
                    planned,
                    tuple(
                        planned_phase if planned_phase.phase in {PhaseName.MANIFEST_VALIDATION, PhaseName.INPUT_PLAN}
                        else _failed_after_abort(planned_phase.phase, _phase_command(planned, planned_phase.phase))
                        for planned_phase in planned.phases
                    ),
                )
            reports.append(_write_preplanned_cell_report(planned, report, run_id))
            continue
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
            with QualificationLock.acquire(paths.lock_path, run_id) as held_lock:
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
                    result = adapter(PhaseAdapterContext(cell, paths, phase, command, len(phases) + 1, held_lock))
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
