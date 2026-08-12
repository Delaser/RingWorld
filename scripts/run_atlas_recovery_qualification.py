#!/usr/bin/env python3
"""Run one bounded, real external Atlas interruption/recovery qualification.

This is intentionally separate from the quick matrix: it consumes one
already-passed quick cell and that run's retained frozen candidate, then makes
a new, disposable nightly fixture run.  It does not rebuild the jar, reuse a
development runtime, publish anything, or accept a candidate supplied outside
the reviewed quick-run tree.
"""

from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re
import stat
import sys
from typing import Any, Callable, Mapping, Sequence

from external_runtime_atlas_recovery_executor import (
    ExternalAtlasRecoveryResult,
    execute_external_runtime_atlas_recovery,
)
from external_runtime_atlas_recovery_plan import (
    QuickTerminalEvidenceInput,
    external_runtime_atlas_recovery_plan,
)
from external_runtime_atlas_stage_runner import run_external_runtime_atlas_recovery_stage
from external_runtime_qualification_adapter import (
    STRICT_EVIDENCE_STEM,
    canonical_cells_from_manifest,
    reviewed_range_identities,
)
from external_runtime_smoke import CandidateJar
from minecraft_frozen_candidate import FrozenCandidateInspection, inspect_frozen_candidate
from minecraft_qualification_evidence import TerminalEvidenceError, validate_terminal_evidence
from minecraft_qualification_executor import QualificationExecutionError, new_run_id
from minecraft_qualification_model import InvocationError, QualificationPaths, Verdict, is_within, require_safe_identifier
from run_minecraft_qualification import (
    ROOT,
    SourceProvenance,
    collect_source_provenance,
    load_manifest,
    validate_source_provenance,
)


_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")
_SHA256 = re.compile(r"^[0-9a-f]{64}$")


class AtlasRecoveryInvocationError(QualificationExecutionError):
    """The nightly Atlas CLI invocation cannot safely reach external I/O."""


@dataclass(frozen=True)
class AtlasRecoveryInvocation:
    """All no-network inputs selected before the executor can run."""

    cell: Mapping[str, Any]
    quick_paths: QualificationPaths
    paths: QualificationPaths
    candidate: CandidateJar
    frozen_candidate_root: Path
    quick_terminal_evidence: QuickTerminalEvidenceInput
    source_provenance: Mapping[str, str]


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True, help="one exact canonical manifest cell ID")
    result.add_argument("--quick-run-id", required=True, help="the completed quick-matrix run ID supplying evidence and jar")
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json", help="reviewed matrix JSON path")
    return result


def _sha256_regular(path: Path, root: Path, label: str) -> str:
    """Hash one existing regular input without accepting a symlink escape."""
    if not path.is_absolute() or not is_within(path, root):
        raise AtlasRecoveryInvocationError(f"{label} escapes its reviewed qualification root")
    try:
        relative = path.resolve(strict=False).relative_to(root.resolve(strict=False))
    except ValueError as error:
        raise AtlasRecoveryInvocationError(f"{label} escapes its reviewed qualification root") from error
    current = root
    try:
        for part in relative.parts:
            current = current / part
            if current.is_symlink():
                raise AtlasRecoveryInvocationError(f"{label} may not traverse a symlink")
        mode = path.lstat().st_mode
    except OSError as error:
        raise AtlasRecoveryInvocationError(f"{label} is unavailable") from error
    if not stat.S_ISREG(mode):
        raise AtlasRecoveryInvocationError(f"{label} must be a regular file")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as error:
        raise AtlasRecoveryInvocationError(f"cannot read {label}") from error
    return digest.hexdigest()


def _manifest_path(repository_root: Path, value: str) -> Path:
    raw = Path(value)
    path = raw if raw.is_absolute() else repository_root / raw
    if not path.is_absolute() or not is_within(path, repository_root):
        raise AtlasRecoveryInvocationError("manifest must be a contained repository file")
    if path.is_symlink() or not path.is_file():
        raise AtlasRecoveryInvocationError("manifest must be an existing regular repository file")
    return path


def _exact_cell(manifest: Mapping[str, Any], requested: str) -> Mapping[str, Any]:
    require_safe_identifier(requested, "cell id")
    cells = manifest.get("cells")
    if not isinstance(cells, Sequence) or isinstance(cells, (str, bytes)):
        raise AtlasRecoveryInvocationError("reviewed manifest has no cell sequence")
    matches = [cell for cell in cells if isinstance(cell, Mapping) and cell.get("id") == requested]
    if len(matches) != 1:
        raise AtlasRecoveryInvocationError("--cell must select exactly one canonical manifest cell")
    return matches[0]


def _quick_run_id(value: str) -> str:
    if not isinstance(value, str) or _RUN_ID.fullmatch(value) is None:
        raise AtlasRecoveryInvocationError("--quick-run-id must be one exact UTC qualification run ID")
    return value


def _quick_terminal_input(
    path: Path,
    quick_paths: QualificationPaths,
    canonical_cells: Mapping[str, Mapping[str, Any]],
    ranges: Mapping[str, Mapping[str, str]],
    cell: Mapping[str, Any],
    candidate: CandidateJar,
) -> QuickTerminalEvidenceInput:
    digest = _sha256_regular(path, quick_paths.cell_root, "quick strict terminal evidence")
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(parsed, Mapping):
            raise TerminalEvidenceError("quick terminal evidence is not an object")
        terminal = validate_terminal_evidence(parsed, canonical_cells, ranges)
    except (OSError, UnicodeError, json.JSONDecodeError, TerminalEvidenceError) as error:
        raise AtlasRecoveryInvocationError("quick strict terminal evidence is not a valid PASS record") from error
    minecraft = cell.get("minecraft")
    if terminal.verdict != Verdict.PASS.value or terminal.cell_id != cell.get("id") \
            or terminal.candidate_sha256 != candidate.sha256 \
            or not isinstance(minecraft, Mapping) or cell.get("loader") != candidate.loader:
        raise AtlasRecoveryInvocationError("quick terminal evidence does not bind the selected cell and frozen candidate")
    return QuickTerminalEvidenceInput(path, digest)


def _execution_provenance(source: SourceProvenance) -> dict[str, str]:
    """Emit the exact current checkout identity that the nightly executor records."""
    validated = validate_source_provenance(source)
    result = asdict(validated)
    expected = {
        "commit", "branch", "upstream", "origin", "manifest_sha256",
        "gradle_wrapper_sha256", "java_version",
    }
    if set(result) != expected or any(not isinstance(value, str) or not value for value in result.values()):
        raise AtlasRecoveryInvocationError("current execution source provenance is incomplete")
    return result


def prepare_invocation(
    *,
    repository_root: Path,
    manifest_path: Path,
    cell_id: str,
    quick_run_id: str,
    run_id: str,
    provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
    candidate_inspector: Callable[[Path, str], FrozenCandidateInspection] = inspect_frozen_candidate,
) -> AtlasRecoveryInvocation:
    """Select and validate all local inputs before any installer/download action.

    The current checkout gate intentionally happens after the retained quick
    inputs have been resolved, but before a new run tree exists or the real
    executor is called.  A dirty/unpushed checkout therefore cannot create a
    runtime, fetch an installer, or overwrite evidence.
    """
    repository_root = repository_root.resolve(strict=False)
    require_safe_identifier(run_id, "run id")
    manifest = load_manifest(manifest_path)
    cell = _exact_cell(manifest, cell_id)
    quick_paths = QualificationPaths.from_cell(repository_root, cell, _quick_run_id(quick_run_id))
    paths = QualificationPaths.from_cell(repository_root, cell, run_id)
    if paths.cell_root.exists():
        raise AtlasRecoveryInvocationError("new Atlas recovery qualification cell already exists")
    loader = cell.get("loader")
    if loader not in {"fabric", "neoforge"}:
        raise AtlasRecoveryInvocationError("selected cell has an unsupported loader")
    frozen_candidate_root = quick_paths.run_root / "frozen-candidates"
    candidate_path = frozen_candidate_root / loader / "ringworld-qualification.jar"
    _sha256_regular(candidate_path, quick_paths.run_root, "retained frozen candidate")
    try:
        inspection = candidate_inspector(candidate_path, loader)
    except (OSError, ValueError) as error:
        raise AtlasRecoveryInvocationError("retained frozen candidate failed inspection") from error
    if inspection.loader != loader or inspection.sha256 != _sha256_regular(
            candidate_path, quick_paths.run_root, "retained frozen candidate"):
        raise AtlasRecoveryInvocationError("retained frozen candidate inspection disagrees with its bytes")
    candidate = CandidateJar(candidate_path, inspection.sha256, loader, inspection.minecraft_range)
    canonical = canonical_cells_from_manifest(tuple(item for item in manifest["cells"] if isinstance(item, Mapping)))
    quick = _quick_terminal_input(
        quick_paths.evidence_directory / f"{STRICT_EVIDENCE_STEM}.json",
        quick_paths, canonical, reviewed_range_identities(), cell, candidate,
    )
    source = _execution_provenance(provenance_provider(repository_root, manifest_path))
    return AtlasRecoveryInvocation(cell, quick_paths, paths, candidate, frozen_candidate_root, quick, source)


Executor = Callable[..., ExternalAtlasRecoveryResult]


def run(
    arguments: argparse.Namespace,
    *,
    repository_root: Path = ROOT,
    provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
    candidate_inspector: Callable[[Path, str], FrozenCandidateInspection] = inspect_frozen_candidate,
    run_id_factory: Callable[[], str] = new_run_id,
    executor: Executor = execute_external_runtime_atlas_recovery,
) -> ExternalAtlasRecoveryResult:
    """Run the one real bounded fixture after all fail-closed preflight gates."""
    manifest_path = _manifest_path(repository_root.resolve(strict=False), arguments.manifest)
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise AtlasRecoveryInvocationError("Atlas recovery executor produced an unsafe run ID")
    prepared = prepare_invocation(
        repository_root=repository_root,
        manifest_path=manifest_path,
        cell_id=arguments.cell,
        quick_run_id=arguments.quick_run_id,
        run_id=run_id,
        provenance_provider=provenance_provider,
        candidate_inspector=candidate_inspector,
    )
    canonical = canonical_cells_from_manifest(tuple(item for item in load_manifest(manifest_path)["cells"] if isinstance(item, Mapping)))
    plan = external_runtime_atlas_recovery_plan(
        prepared.cell, prepared.candidate, prepared.paths, prepared.quick_terminal_evidence,
        frozen_candidate_root=prepared.frozen_candidate_root, quick_evidence_root=prepared.quick_paths.cell_root,
    )
    return executor(
        plan, prepared.paths, run_id,
        canonical_cells=canonical,
        range_identities=reviewed_range_identities(),
        stage_runner=run_external_runtime_atlas_recovery_stage,
        execution_source_provenance=prepared.source_provenance,
    )


def main(argv: list[str] | None = None, *, repository_root: Path = ROOT) -> int:
    arguments = parser().parse_args(argv)
    try:
        result = run(arguments, repository_root=repository_root)
    except (AtlasRecoveryInvocationError, InvocationError, QualificationExecutionError, OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps({
        "fixture": "atlas-prewarm-recovery",
        "cell": result.cell_id,
        "loader": result.loader,
        "minecraft": result.minecraft_version,
        "verdict": result.verdict.value,
        "reason": result.reason,
        "terminal_evidence": result.evidence_json,
    }, sort_keys=True))
    return 0 if result.verdict is Verdict.PASS else 1


if __name__ == "__main__":
    raise SystemExit(main())
