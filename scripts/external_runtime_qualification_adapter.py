#!/usr/bin/env python3
"""Fail-closed bridge from an external dedicated smoke to terminal evidence.

The external executor deliberately owns downloads, installation, and process
lifetimes.  This module owns the *narrower* conversion boundary used by a
qualification ``PhaseAdapter``: it can capture immutable local facts from a
completed smoke and bind them to the strict terminal schema, but it cannot
invent a clean source identity or a same-file group proof.  Missing either is
an ``INCOMPLETE`` phase, never a successful compatibility claim.

This module intentionally does not import ``run_minecraft_qualification``.
Its callable accepts that module's ``PhaseAdapterContext`` structurally so the
runner can install it without creating a cyclic dependency or granting this
module authority over scheduling/locking.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import stat
from typing import Any, Callable, Mapping, Protocol, Sequence

from external_runtime_executor import ExternalRuntimeSmokeResult, execute_external_runtime_smoke
from external_runtime_smoke import CandidateJar, ExternalRuntimeSmokePlan, external_runtime_smoke_plan
from minecraft_frozen_candidate import (
    EXPECTED_VERSIONS,
    FrozenCandidateError,
    FrozenCandidateInspection,
    SameFileCoverage,
    inspect_frozen_candidate,
    verify_same_file_coverage,
)
from minecraft_qualification_evidence import (
    TerminalEvidenceError,
    normalize_external_runtime_result,
    validate_terminal_evidence,
)
from minecraft_qualification_executor import EvidenceError, QualificationExecutionError, QualificationLock
from minecraft_qualification_model import EvidenceReference, PhaseName, PhaseResult, QualificationPaths, Verdict, is_within


STRICT_RUNTIME_EVIDENCE_UNAVAILABLE = "STRICT_RUNTIME_EVIDENCE_UNAVAILABLE"
FROZEN_CANDIDATE_UNAVAILABLE = "FROZEN_CANDIDATE_UNAVAILABLE"
SAME_FILE_GROUP_UNAVAILABLE = "SAME_FILE_GROUP_UNAVAILABLE"
EXTERNAL_RUNTIME_EXECUTOR_UNAVAILABLE = "EXTERNAL_RUNTIME_EXECUTOR_UNAVAILABLE"
HELD_LOCK_UNAVAILABLE = "HELD_LOCK_UNAVAILABLE"
STRICT_EVIDENCE_STEM = "strict-terminal-evidence"
SAFE_SMALL_WORLD_CONFIG = {
    "seed": "ringworld-qualification-safe-small-v1",
    "circumference_blocks": 2048,
    "width_blocks": 416,
    "wall_height_blocks": 160,
    "pregenerate_terrain_atlas": False,
}


class ExternalAdapterError(ValueError):
    """A completed smoke cannot be bound to a strict terminal record."""


class PhaseContext(Protocol):
    """Structural subset of the runner's public PhaseAdapterContext API."""

    cell: Mapping[str, Any]
    paths: QualificationPaths
    phase: PhaseName
    command: Any
    ordinal: int
    held_lock: QualificationLock | None


class SmokeExecutor(Protocol):
    def __call__(
        self,
        plan: ExternalRuntimeSmokePlan,
        paths: QualificationPaths,
        run_id: str,
        *,
        held_lock: QualificationLock,
    ) -> ExternalRuntimeSmokeResult: ...


@dataclass(frozen=True)
class RuntimeSupportInputs:
    """Independent facts that an executor must not manufacture for itself.

    ``provenance`` is collected before process work by the runner.  A
    ``FrozenCandidateInspection`` comes from the immutable oldest-ABI jar,
    while ``same_file`` must have been established across all three cells for
    the loader.  These are deliberately separate from mutable runtime output.
    """

    provenance: Mapping[str, Any]
    frozen_candidate: FrozenCandidateInspection | None
    same_file: SameFileCoverage | None


def reviewed_range_identities() -> dict[str, dict[str, str]]:
    """Return the immutable range declarations expected by the strict schema."""
    return {
        "fabric": {
            "oldest_abi_minecraft_version": "26.1",
            "minecraft_range": ">=26.1 <=26.1.2",
            "loader_range": "",
        },
        "neoforge": {
            "oldest_abi_minecraft_version": "26.1",
            "minecraft_range": "[26.1,26.1.2]",
            "loader_range": "[26.1.0.19-beta,26.1.2.87]",
        },
    }


def strict_provenance_from_source(source: Any) -> dict[str, Any]:
    """Convert the runner's clean source fact into terminal-schema provenance."""
    required = ("commit", "manifest_sha256", "gradle_wrapper_sha256", "java_version", "origin")
    if any(not isinstance(getattr(source, field, None), str) or not getattr(source, field) for field in required):
        raise ExternalAdapterError("source provenance is incomplete")
    return {
        "commit": source.commit,
        "clean": True,
        "public_origin": source.origin,
        "manifest_sha256": source.manifest_sha256,
        "wrapper_sha256": source.gradle_wrapper_sha256,
        "java": {"major": 25, "version": source.java_version},
        "platform": {"system": platform.system() or "unknown", "machine": platform.machine() or "unknown"},
    }


def _strict_evidence_path(paths: QualificationPaths) -> Path:
    path = paths.evidence_directory / f"{STRICT_EVIDENCE_STEM}.json"
    if not is_within(path, paths.cell_root):
        raise ExternalAdapterError("strict terminal evidence path escapes its cell")
    return path


def _assert_no_symlink_path(path: Path, root: Path) -> None:
    if not is_within(path, root):
        raise ExternalAdapterError("strict terminal evidence path escapes its root")
    try:
        relative = path.resolve(strict=False).relative_to(root.resolve(strict=False))
    except ValueError as error:
        raise ExternalAdapterError("strict terminal evidence path escapes its root") from error
    current = root
    for part in relative.parts:
        current = current / part
        if current.exists() or current.is_symlink():
            if current.is_symlink():
                raise ExternalAdapterError("strict terminal evidence may not traverse a symlink")


def write_strict_terminal_evidence(
    terminal: Mapping[str, Any],
    paths: QualificationPaths,
    canonical_cells: Mapping[str, Mapping[str, Any]],
    range_identities: Mapping[str, Mapping[str, str]],
) -> EvidenceReference:
    """Validate then atomically create the raw strict terminal JSON record.

    The ordinary cell report is a scheduler summary.  This separately stores
    the unwrapped schema record that actually justifies the runtime phase.
    It is intentionally O_EXCL-only and cannot follow a pre-created symlink.
    """
    validate_terminal_evidence(terminal, canonical_cells, range_identities)
    destination = _strict_evidence_path(paths)
    if not paths.evidence_directory.is_dir() or paths.evidence_directory.is_symlink():
        raise ExternalAdapterError("strict terminal evidence directory is unavailable or unsafe")
    _assert_no_symlink_path(destination, paths.cell_root)
    payload = json.dumps(terminal, sort_keys=True, indent=2).encode("utf-8") + b"\n"
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(destination, flags, 0o600)
    except FileExistsError as error:
        raise EvidenceError("strict terminal evidence already exists") from error
    except OSError as error:
        raise ExternalAdapterError("cannot create strict terminal evidence") from error
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
    except Exception:
        # A partially created record is evidence of a failed run and must not
        # be overwritten by retry; leave it in place for inspection.
        raise
    relative = destination.resolve(strict=True).relative_to(paths.cell_root.resolve(strict=True)).as_posix()
    return EvidenceReference(
        "strict-terminal-evidence-json", relative,
        "SHA-256 " + hashlib.sha256(payload).hexdigest(),
    )


def canonical_cells_from_manifest(cells: Sequence[Mapping[str, Any]]) -> dict[str, dict[str, Any]]:
    """Convert reviewed nested manifest cells into terminal-schema identities.

    The persisted safe-small first-world configuration is owned by the smoke
    plan rather than duplicated in the JSON manifest.  We nevertheless expose
    it in each terminal cell identity so a report cannot silently swap the
    runtime geometry after its manifest has been reviewed.
    """
    result: dict[str, dict[str, Any]] = {}
    for raw in cells:
        if not isinstance(raw, Mapping):
            raise ExternalAdapterError("manifest cells must be objects")
        cell_id, loader = raw.get("id"), raw.get("loader")
        minecraft, profile = raw.get("minecraft"), raw.get("profile")
        if not isinstance(cell_id, str) or not cell_id or loader not in {"fabric", "neoforge"}:
            raise ExternalAdapterError("manifest cell has invalid id or loader")
        if not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str) or not minecraft["version"]:
            raise ExternalAdapterError("manifest cell has no Minecraft version")
        if not isinstance(profile, Mapping) or not isinstance(profile.get("server_port"), int) \
                or isinstance(profile["server_port"], bool) or not 1 <= profile["server_port"] <= 65535:
            raise ExternalAdapterError("manifest cell has no valid profile server port")
        if cell_id in result:
            raise ExternalAdapterError("manifest duplicates a qualification cell id")
        result[cell_id] = {
            "id": cell_id,
            "minecraft_version": minecraft["version"],
            "loader": loader,
            "port": profile["server_port"],
            "world_config": dict(SAFE_SMALL_WORLD_CONFIG),
        }
    if not result:
        raise ExternalAdapterError("manifest selects no qualification cells")
    return result


def external_runtime_adapter_from_qualification_inputs(
    cells: Sequence[Mapping[str, Any]],
    source_provenance: Any,
    preparations: Mapping[str, Any],
    *,
    smoke_executor: SmokeExecutor = execute_external_runtime_smoke,
) -> "ExternalRuntimeQualificationAdapter":
    """Build the default dedicated-smoke adapter from runner-owned inputs.

    This is intentionally duck-typed at the ``FrozenCandidatePreparation``
    boundary to avoid importing the runner and creating a circular dependency.
    Only a complete frozen preparation produces a candidate/support entry;
    partial selections therefore have no possible route to runtime I/O.
    """
    canonical = canonical_cells_from_manifest(cells)
    provenance = strict_provenance_from_source(source_provenance)
    candidates: dict[str, CandidateJar] = {}
    support: dict[str, RuntimeSupportInputs] = {}
    candidate_roots: set[Path] = set()
    for loader in ("fabric", "neoforge"):
        preparation = preparations.get(loader)
        if preparation is None or getattr(preparation, "verdict", None) is not Verdict.PASS:
            continue
        plan = getattr(preparation, "plan", None)
        inspection = getattr(preparation, "inspection", None)
        if plan is None or not isinstance(inspection, FrozenCandidateInspection):
            raise ExternalAdapterError("passing frozen preparation lacks an inspected candidate")
        if inspection.loader != loader or Path(inspection.path) != plan.candidate_path:
            raise ExternalAdapterError("frozen preparation candidate identity disagrees with its plan")
        inspected_by_cell = {f"{version}-{loader}": inspection for version in EXPECTED_VERSIONS}
        try:
            same_file = verify_same_file_coverage(loader, inspected_by_cell)
        except Exception as error:
            raise ExternalAdapterError("frozen preparation has no complete same-file coverage") from error
        candidates[loader] = CandidateJar(plan.candidate_path, inspection.sha256, loader, inspection.minecraft_range)
        support[loader] = RuntimeSupportInputs(provenance, inspection, same_file)
        candidate_roots.add(plan.candidate_path.parent.parent)
    # A run can contain a Fabric-only triplet or both loader triplets.  Their
    # frozen directories share a run root; use the common frozen-candidates
    # parent when present, and retain the normal adapter INCOMPLETE behavior
    # when no loader was prepared.
    frozen_root: Path | None = None
    if candidate_roots:
        if len(candidate_roots) != 1:
            raise ExternalAdapterError("frozen candidates do not share one qualification root")
        frozen_root = next(iter(candidate_roots))
    return ExternalRuntimeQualificationAdapter(
        canonical, reviewed_range_identities(), candidates, support,
        frozen_candidate_root=frozen_root, smoke_executor=smoke_executor,
    )


def _sha256_regular(path: Path, paths: QualificationPaths, label: str) -> tuple[str, str]:
    """Hash one non-symlink evidence file and return its cell-relative path."""
    try:
        status = path.lstat()
    except OSError as error:
        raise ExternalAdapterError(f"{label} is unavailable") from error
    if path.is_symlink() or not stat.S_ISREG(status.st_mode) or not is_within(path, paths.cell_root):
        raise ExternalAdapterError(f"{label} is not a regular file below the qualification cell")
    try:
        relative = path.resolve(strict=True).relative_to(paths.cell_root.resolve(strict=True)).as_posix()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
    except (OSError, ValueError) as error:
        raise ExternalAdapterError(f"cannot hash {label}") from error
    return relative, digest


def _revalidate_frozen_candidate(
    candidate: CandidateJar,
    expected: FrozenCandidateInspection,
    frozen_root: Path | None,
) -> FrozenCandidateInspection:
    """Re-read the retained jar before any installer or network activity."""
    source = candidate.path
    try:
        status = source.lstat()
    except OSError as error:
        raise ExternalAdapterError("frozen candidate is unavailable before runtime execution") from error
    if source.is_symlink() or not stat.S_ISREG(status.st_mode) or frozen_root is None \
            or not is_within(source, frozen_root):
        raise ExternalAdapterError("frozen candidate is not a regular file below its reviewed root")
    try:
        observed = inspect_frozen_candidate(source, candidate.loader)
    except (OSError, FrozenCandidateError) as error:
        raise ExternalAdapterError("frozen candidate failed its pre-runtime inspection") from error
    if observed != expected or observed.sha256 != candidate.sha256 \
            or observed.minecraft_range != candidate.declared_target_range:
        raise ExternalAdapterError("frozen candidate changed after preparation")
    return observed


def _as_utc(value: str, label: str) -> datetime:
    if not isinstance(value, str) or not value:
        raise ExternalAdapterError(f"{label} has no timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ExternalAdapterError(f"{label} timestamp is invalid") from error
    if parsed.tzinfo is None:
        raise ExternalAdapterError(f"{label} timestamp must include a timezone")
    return parsed.astimezone(timezone.utc)


def _timestamp(value: datetime) -> str:
    return value.isoformat(timespec="microseconds").replace("+00:00", "Z")


def _executed_command_record(
    result: ExternalRuntimeSmokeResult,
    paths: QualificationPaths,
) -> Mapping[str, Any]:
    executed = result.installer
    if executed is None:
        raise ExternalAdapterError("external runtime result has no installer command")
    stdout_path, stdout_hash = _sha256_regular(Path(executed.stdout_log), paths, "installer stdout log")
    stderr_path, stderr_hash = _sha256_regular(Path(executed.stderr_log), paths, "installer stderr log")
    started = _as_utc(executed.started_at_utc, "installer command")
    if not isinstance(executed.elapsed_seconds, (int, float)) or isinstance(executed.elapsed_seconds, bool) \
            or executed.elapsed_seconds < 0:
        raise ExternalAdapterError("installer command elapsed time is invalid")
    return {
        "phase": "runtime-installer",
        "argv": list(executed.argv),
        "exit_code": executed.return_code,
        "started_at_utc": _timestamp(started),
        "ended_at_utc": _timestamp(started + timedelta(seconds=executed.elapsed_seconds)),
        "elapsed_seconds": float(executed.elapsed_seconds),
        "stdout_path": stdout_path,
        "stdout_sha256": stdout_hash,
        "stderr_path": stderr_path,
        "stderr_sha256": stderr_hash,
    }


def _server_command_record(
    plan: ExternalRuntimeSmokePlan,
    result: ExternalRuntimeSmokeResult,
    paths: QualificationPaths,
) -> Mapping[str, Any]:
    if not result.server_log:
        raise ExternalAdapterError("external runtime result has no server log")
    log_path, log_hash = _sha256_regular(Path(result.server_log), paths, "server combined log")
    starts = [event.timestamp_utc for event in result.marker_ledger if event.name == "runtime-start"]
    ends = [event.timestamp_utc for event in result.marker_ledger if event.name == "runtime-exit"]
    if len(starts) != 1 or len(ends) != 1:
        raise ExternalAdapterError("runtime marker ledger has no unique start/exit pair")
    started, ended = _as_utc(starts[0], "runtime start"), _as_utc(ends[0], "runtime exit")
    if ended < started:
        raise ExternalAdapterError("runtime marker ledger is temporally reversed")
    return {
        "phase": "dedicated-server",
        "argv": list(plan.launch.argv),
        "exit_code": result.server_return_code,
        "started_at_utc": _timestamp(started),
        "ended_at_utc": _timestamp(ended),
        "elapsed_seconds": (ended - started).total_seconds(),
        "stdout_path": log_path,
        "stdout_sha256": log_hash,
        "stderr_path": log_path,
        "stderr_sha256": log_hash,
    }


def _runtime_inventory(
    plan: ExternalRuntimeSmokePlan,
    result: ExternalRuntimeSmokeResult,
    paths: QualificationPaths,
) -> tuple[list[Mapping[str, str]], str]:
    """Capture the installer-owned server, generated launcher, and exact mods."""
    identity = result.runtime_identity
    if identity is None:
        raise ExternalAdapterError("external runtime result has no installed runtime identity")
    if identity.loader != plan.loader:
        raise ExternalAdapterError("installed runtime loader disagrees with plan")
    installed_server = Path(identity.minecraft_server_path)
    server_path, server_hash = _sha256_regular(installed_server, paths, "installed Minecraft server")
    algorithm = plan.minecraft_server.algorithm
    if algorithm not in {"sha1", "sha256"}:
        raise ExternalAdapterError("reviewed Minecraft server pin uses an unsupported algorithm")
    try:
        pinned_hash = hashlib.new(algorithm, installed_server.read_bytes()).hexdigest()
    except (OSError, ValueError) as error:
        raise ExternalAdapterError("cannot rehash the installed Minecraft server") from error
    if pinned_hash != identity.minecraft_server_actual \
            or identity.minecraft_server_expected != plan.minecraft_server.checksum \
            or pinned_hash != plan.minecraft_server.checksum:
        raise ExternalAdapterError("installed Minecraft server hash changed after executor verification")
    launcher_path, launcher_hash = _sha256_regular(Path(identity.launcher_path), paths, "installed launcher")
    entries: list[Mapping[str, str]] = [
        {"path": server_path, "sha256": server_hash, "role": "minecraft-server"},
        {"path": launcher_path, "sha256": launcher_hash, "role": "loader-launcher"},
    ]
    ringworld_path: str | None = None
    expected_mods = {entry.name: entry for entry in plan.mods}
    copied_mods = {copy.name: copy for copy in result.mods}
    if set(copied_mods) != set(expected_mods):
        raise ExternalAdapterError("copied mod inventory disagrees with the reviewed plan")
    for index, name in enumerate(sorted(expected_mods)):
        planned = expected_mods[name]
        copied = copied_mods[name]
        path, actual = _sha256_regular(planned.destination, paths, f"installed {name} mod")
        if copied.actual_sha256 != actual or copied.expected_sha256 != planned.sha256:
            raise ExternalAdapterError(f"installed {name} mod hash disagrees with executor evidence")
        role = "ringworld" if name == "RingWorld" else f"mod-{index}-{name.lower().replace(' ', '-')}"
        entries.append({"path": path, "sha256": actual, "role": role})
        if name == "RingWorld":
            ringworld_path = path
    if ringworld_path is None:
        raise ExternalAdapterError("reviewed mod inventory has no RingWorld jar")
    return entries, ringworld_path


def _semantic_markers(result: ExternalRuntimeSmokeResult) -> list[Mapping[str, str]]:
    required = ("ringworld-bootstrap", "atlas-disabled", "server-ready", "server-stop", "world-save")
    by_name = {event.name: event.timestamp_utc for event in result.marker_ledger}
    if len(by_name) != len(result.marker_ledger):
        raise ExternalAdapterError("runtime marker ledger duplicates a marker")
    try:
        return [{"name": name, "timestamp_utc": by_name[name]} for name in required]
    except KeyError as error:
        raise ExternalAdapterError("runtime marker ledger lacks a required semantic marker") from error


def _installer_record(
    plan: ExternalRuntimeSmokePlan,
    result: ExternalRuntimeSmokeResult,
    paths: QualificationPaths,
) -> Mapping[str, str]:
    if len(plan.installer.argv) < 3:
        raise ExternalAdapterError("reviewed installer command has no jar argument")
    installer_path = Path(plan.installer.argv[2]).resolve(strict=False)
    matching_plan = [download for download in plan.downloads if download.destination.resolve(strict=False) == installer_path]
    if len(matching_plan) != 1:
        raise ExternalAdapterError("reviewed runtime plan has no unique installer download")
    planned_installer = matching_plan[0]
    matching = [download for download in result.downloads if Path(download.path).resolve(strict=False) == installer_path]
    if len(matching) != 1:
        raise ExternalAdapterError("external runtime result has no unique installer download")
    downloaded = matching[0]
    path, actual = _sha256_regular(Path(downloaded.path), paths, "runtime installer")
    if actual != downloaded.actual or downloaded.expected != downloaded.actual:
        raise ExternalAdapterError("runtime installer hash disagrees with download evidence")
    if planned_installer.checksum != downloaded.expected or planned_installer.name != downloaded.name:
        raise ExternalAdapterError("reviewed runtime installer plan disagrees with download evidence")
    return {
        "name": downloaded.name,
        "url": planned_installer.url,
        "path": path,
        "sha256": actual,
        "installed_sha256": actual,
    }
def _frozen_candidate_record(
    inputs: RuntimeSupportInputs,
    installed_path: str,
    installed_sha256: str,
) -> Mapping[str, str]:
    inspection = inputs.frozen_candidate
    if inspection is None:
        raise ExternalAdapterError(FROZEN_CANDIDATE_UNAVAILABLE)
    source = Path(inspection.path)
    if source.suffix != ".jar" or inspection.loader not in {"fabric", "neoforge"}:
        raise ExternalAdapterError("frozen candidate inspection is malformed")
    loader_range = inspection.loader_range if inspection.loader_range is not None else ""
    return {
        "source_path": source.name,
        "source_sha256": inspection.sha256,
        "installed_path": installed_path,
        "installed_sha256": installed_sha256,
        "oldest_abi_minecraft_version": "26.1",
        "minecraft_range": inspection.minecraft_range,
        "loader_range": loader_range,
    }


def _same_file_record(inputs: RuntimeSupportInputs, loader: str, candidate_sha256: str) -> Mapping[str, Any]:
    coverage = inputs.same_file
    if coverage is None:
        raise ExternalAdapterError(SAME_FILE_GROUP_UNAVAILABLE)
    if coverage.loader != loader or coverage.sha256 != candidate_sha256:
        raise ExternalAdapterError("same-file coverage disagrees with the frozen candidate")
    return {"group": f"26.1.x-{loader}", "sha256": coverage.sha256, "cell_ids": list(coverage.cell_ids)}


def capture_runtime_support(
    plan: ExternalRuntimeSmokePlan,
    result: ExternalRuntimeSmokeResult,
    paths: QualificationPaths,
    inputs: RuntimeSupportInputs,
) -> Mapping[str, Any]:
    """Capture all strict-support fields available after one completed smoke.

    This only reads immutable output within ``paths.cell_root``.  The caller
    still gives the result to :func:`normalize_external_runtime_result`, which
    verifies every relationship again and rejects a partial PASS.
    """
    if result.verdict is not Verdict.PASS:
        raise ExternalAdapterError("only a passing external runtime result can supply PASS evidence")
    inventory, ringworld_path = _runtime_inventory(plan, result, paths)
    copied_ringworld = [copy for copy in result.mods if copy.name == "RingWorld"]
    if len(copied_ringworld) != 1:
        raise ExternalAdapterError("external runtime result has no unique copied RingWorld jar")
    candidate = _frozen_candidate_record(inputs, ringworld_path, copied_ringworld[0].actual_sha256)
    installer = _installer_record(plan, result, paths)
    return {
        "provenance": inputs.provenance,
        "commands": [_executed_command_record(result, paths), _server_command_record(plan, result, paths)],
        "installer": installer,
        "runtime_inventory": inventory,
        "frozen_candidate": candidate,
        "markers": _semantic_markers(result),
        "same_file": _same_file_record(inputs, plan.loader, copied_ringworld[0].actual_sha256),
    }


class ExternalRuntimeQualificationAdapter:
    """A PhaseAdapter-compatible external-runtime bridge.

    The adapter is deliberately inert unless a runner lends its live cell lock
    through ``PhaseAdapterContext``.  The standalone executor accepts that
    capability and validates the exact path and run ID before it skips its own
    acquisition.  An absent or stale capability is ``INCOMPLETE``, never a
    second lock acquisition.
    """

    def __init__(
        self,
        canonical_cells: Mapping[str, Mapping[str, Any]],
        range_identities: Mapping[str, Mapping[str, str]],
        candidates: Mapping[str, CandidateJar],
        support_inputs: Mapping[str, RuntimeSupportInputs],
        *,
        frozen_candidate_root: Path | None = None,
        smoke_executor: SmokeExecutor | None = None,
    ) -> None:
        self._canonical_cells = canonical_cells
        self._range_identities = range_identities
        self._candidates = candidates
        self._support_inputs = support_inputs
        self._frozen_candidate_root = frozen_candidate_root
        self._smoke_executor = smoke_executor

    def __call__(self, context: PhaseContext) -> PhaseResult:
        if context.phase != PhaseName.DEDICATED_SMOKE or context.command is not None:
            raise ExternalAdapterError("external runtime adapter received an invalid phase context")
        loader = context.cell.get("loader")
        if loader not in {"fabric", "neoforge"}:
            raise ExternalAdapterError("external runtime adapter received an invalid loader")
        candidate = self._candidates.get(loader)
        if candidate is None:
            return PhaseResult(context.phase, Verdict.INCOMPLETE, FROZEN_CANDIDATE_UNAVAILABLE)
        inputs = self._support_inputs.get(loader)
        if inputs is None or inputs.frozen_candidate is None:
            return PhaseResult(context.phase, Verdict.INCOMPLETE, FROZEN_CANDIDATE_UNAVAILABLE)
        if inputs.same_file is None:
            return PhaseResult(context.phase, Verdict.INCOMPLETE, SAME_FILE_GROUP_UNAVAILABLE)
        if self._smoke_executor is None:
            return PhaseResult(context.phase, Verdict.INCOMPLETE, EXTERNAL_RUNTIME_EXECUTOR_UNAVAILABLE)
        held_lock = getattr(context, "held_lock", None)
        if not isinstance(held_lock, QualificationLock):
            return PhaseResult(context.phase, Verdict.INCOMPLETE, HELD_LOCK_UNAVAILABLE)
        try:
            _revalidate_frozen_candidate(candidate, inputs.frozen_candidate, self._frozen_candidate_root)
            plan = external_runtime_smoke_plan(
                context.cell, candidate, context.paths, frozen_candidate_root=self._frozen_candidate_root,
            )
            result = self._smoke_executor(plan, context.paths, context.paths.run_id, held_lock=held_lock)
            support = capture_runtime_support(plan, result, context.paths, inputs) if result.verdict is Verdict.PASS else None
            terminal = normalize_external_runtime_result(
                result, support, self._canonical_cells, self._range_identities,
            )
            validated = validate_terminal_evidence(terminal, self._canonical_cells, self._range_identities)
            persisted = write_strict_terminal_evidence(
                terminal, context.paths, self._canonical_cells, self._range_identities,
            )
        except (ExternalAdapterError, TerminalEvidenceError, QualificationExecutionError, OSError) as error:
            # A selected, fully-prepared runtime phase that cannot maintain its
            # lock/evidence contract is an execution failure, not an unknown
            # compatibility result.  Static missing prerequisites return above
            # before any runtime plan or I/O can occur.
            return PhaseResult(context.phase, Verdict.FAIL, f"STRICT_RUNTIME_EXECUTION_FAILED:{error.__class__.__name__}")
        evidence = [persisted]
        if validated.verdict == "PASS":
            evidence.append(EvidenceReference(
                "frozen-candidate-sha256", validated.candidate_sha256 or "", "same-file candidate identity",
            ))
        return PhaseResult(context.phase, Verdict(validated.verdict), terminal.get("reason"), evidence=tuple(evidence))


# Explicit name for runner configuration code; importing this module does not
# install it into the runner's default adapter map.
external_runtime_phase_adapter = ExternalRuntimeQualificationAdapter
