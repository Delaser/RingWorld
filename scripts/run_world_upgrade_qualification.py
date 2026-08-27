#!/usr/bin/env python3
"""Run one bounded copied-world RingWorld forward-upgrade qualification."""

from __future__ import annotations

import argparse
from functools import partial
import json
from pathlib import Path
import re
import sys
from typing import Callable, Mapping

from external_runtime_qualification_adapter import canonical_cells_from_manifest, reviewed_range_identities
from external_runtime_worldgen_executor import (
    ExternalForwardUpgradeResult, ForwardUpgradeSource, execute_external_runtime_forward_upgrade,
)
from external_runtime_worldgen_plan import external_runtime_worldgen_resume_stage
from minecraft_frozen_candidate import FrozenCandidateInspection, inspect_frozen_candidate
from minecraft_qualification_executor import QualificationExecutionError, new_run_id
from minecraft_qualification_model import InvocationError, QualificationPaths, Verdict
from minecraft_qualification_ranges import CompatibilityRangeError, parse_minecraft_version
from minecraft_support_contract import contract_from_manifest
from minecraft_world_upgrade_qualification import FIXTURE_NAME
from run_atlas_recovery_qualification import (
    AtlasRecoveryInvocationError, _exact_cell, _manifest_path, _quick_run_id,
    _sha256_regular, prepare_invocation,
)
from run_minecraft_qualification import ROOT, SourceProvenance, collect_source_provenance, load_manifest


_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--source-cell", required=True, help="older exact canonical manifest cell ID")
    result.add_argument("--source-worldgen-run-id", required=True, help="passed external worldgen fixture run ID")
    result.add_argument("--target-cell", required=True, help="later exact canonical manifest cell ID")
    result.add_argument("--target-quick-run-id", required=True, help="passed target quick-matrix run ID")
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    result.add_argument("--source-manifest", help="reviewed source candidate-group manifest (defaults to --manifest)")
    return result


Executor = Callable[..., ExternalForwardUpgradeResult]


def _version(cell: Mapping[str, object]) -> str:
    minecraft = cell.get("minecraft")
    if not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str):
        raise AtlasRecoveryInvocationError("canonical upgrade cell has no Minecraft version")
    return minecraft["version"]


def _source_candidate_hash(path: Path, root: Path) -> str:
    """Read the hash bound by an immutable source worldgen terminal."""
    _sha256_regular(path, root, "source worldgen terminal")
    try:
        record = json.loads(path.read_text(encoding="utf-8"))
        qualification = record.get("qualification") if isinstance(record, Mapping) else None
        candidate = qualification.get("frozenCandidateSha256") if isinstance(qualification, Mapping) else None
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise AtlasRecoveryInvocationError("source worldgen terminal is not readable JSON") from error
    if not isinstance(candidate, str) or re.fullmatch(r"[0-9a-f]{64}", candidate) is None:
        raise AtlasRecoveryInvocationError("source worldgen terminal has no valid frozen candidate hash")
    return candidate


def run(
    arguments: argparse.Namespace, *, repository_root: Path = ROOT,
    provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
    candidate_inspector: Callable[[Path, str], FrozenCandidateInspection] = inspect_frozen_candidate,
    run_id_factory: Callable[[], str] = new_run_id,
    executor: Executor = execute_external_runtime_forward_upgrade,
) -> ExternalForwardUpgradeResult:
    repository_root = repository_root.resolve(strict=False)
    manifest_path = _manifest_path(repository_root, arguments.manifest)
    source_manifest_path = _manifest_path(repository_root, getattr(arguments, "source_manifest", None) or arguments.manifest)
    manifest = load_manifest(manifest_path)
    source_manifest = load_manifest(source_manifest_path)
    # Validate both manifest groups independently before trusting their cells.
    source_contract = contract_from_manifest(source_manifest)
    target_contract = contract_from_manifest(manifest)
    source_cell = _exact_cell(source_manifest, arguments.source_cell)
    target_cell = _exact_cell(manifest, arguments.target_cell)
    source_version, target_version = _version(source_cell), _version(target_cell)
    try:
        forward = parse_minecraft_version(source_version) < parse_minecraft_version(target_version)
    except CompatibilityRangeError as error:
        raise AtlasRecoveryInvocationError("requested cells have invalid stable Minecraft versions") from error
    if source_cell.get("loader") != target_cell.get("loader") or not forward:
        raise AtlasRecoveryInvocationError("requested cells are not one supported forward upgrade path")
    source_paths = QualificationPaths.from_cell(
        repository_root, source_cell, _quick_run_id(arguments.source_worldgen_run_id),
    )
    source_terminal = source_paths.evidence_directory / "nightly/02-worldgen-seam-structures/terminal.json"
    source_world = source_paths.run_directory / "nightly/02-worldgen-seam-structures/production/runtime/world"
    source_resume_log = source_paths.evidence_directory / "nightly/02-worldgen-seam-structures/production-resume.log"
    # This preflight intentionally only reads trusted, ignored qualification
    # evidence.  The executor repeats all hashes before any runtime/download.
    terminal_hash = _sha256_regular(source_terminal, source_paths.cell_root, "source worldgen terminal")
    source_candidate_hash = _source_candidate_hash(source_terminal, source_paths.cell_root)
    resume_hash = _sha256_regular(source_resume_log, source_paths.cell_root, "source production-resume log")
    if not source_world.is_dir() or source_world.is_symlink():
        raise AtlasRecoveryInvocationError("source worldgen production world is unavailable")
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise AtlasRecoveryInvocationError("forward upgrade executor produced an unsafe run ID")
    target_inspector = (partial(inspect_frozen_candidate, contract=target_contract)
                        if candidate_inspector is inspect_frozen_candidate else candidate_inspector)
    target = prepare_invocation(
        repository_root=repository_root, manifest_path=manifest_path,
        cell_id=target_cell["id"], quick_run_id=arguments.target_quick_run_id, run_id=run_id,
        provenance_provider=provenance_provider, candidate_inspector=target_inspector,
    )
    source_canonical = canonical_cells_from_manifest(
        tuple(item for item in source_manifest["cells"] if isinstance(item, Mapping))
    )
    canonical = canonical_cells_from_manifest(tuple(item for item in manifest["cells"] if isinstance(item, Mapping)))
    fixture_root = target.paths.run_directory / f"nightly/{FIXTURE_NAME}"
    evidence_root = target.paths.evidence_directory / f"nightly/{FIXTURE_NAME}"
    stage = external_runtime_worldgen_resume_stage(
        target.cell, target.candidate, target.paths,
        frozen_candidate_root=target.frozen_candidate_root,
        fixture_root=fixture_root, evidence_root=evidence_root,
    )
    source = ForwardUpgradeSource(
        source_cell["id"], source_cell["loader"], source_version, source_paths.cell_root,
        source_terminal, terminal_hash, source_candidate_hash, source_world, source_resume_log, resume_hash,
    )
    return executor(
        source, target.cell, target.candidate, target.quick_terminal_evidence, stage, target.paths, run_id,
        frozen_candidate_root=target.frozen_candidate_root, quick_evidence_root=target.quick_paths.cell_root,
        fixture_root=fixture_root, evidence_root=evidence_root, canonical_cells=canonical,
        range_identities=reviewed_range_identities(target_contract),
        source_canonical_cells=source_canonical,
        source_range_identities=reviewed_range_identities(source_contract),
        candidate_inspector=target_inspector,
        execution_source_provenance=target.source_provenance,
    )


def main(argv: list[str] | None = None, *, repository_root: Path = ROOT) -> int:
    arguments = parser().parse_args(argv)
    try:
        result = run(arguments, repository_root=repository_root)
    except (AtlasRecoveryInvocationError, InvocationError, QualificationExecutionError, OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps({
        "fixture": FIXTURE_NAME, "source_cell": result.source_cell_id,
        "target_cell": result.target_cell_id, "loader": result.loader,
        "verdict": result.verdict.value, "reason": result.reason,
        "terminal_evidence": result.evidence_json,
    }, sort_keys=True))
    return 0 if result.verdict is Verdict.PASS else 1


if __name__ == "__main__":
    raise SystemExit(main())
