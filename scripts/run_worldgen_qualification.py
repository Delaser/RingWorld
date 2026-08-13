#!/usr/bin/env python3
"""Run one real external worldgen/seam-structure nightly qualification."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from typing import Callable

from external_runtime_qualification_adapter import canonical_cells_from_manifest, reviewed_range_identities
from external_runtime_worldgen_executor import ExternalWorldgenResult, execute_external_runtime_worldgen
from external_runtime_worldgen_plan import external_runtime_worldgen_plan
from minecraft_frozen_candidate import FrozenCandidateInspection, inspect_frozen_candidate
from minecraft_qualification_executor import QualificationExecutionError, new_run_id
from minecraft_qualification_model import InvocationError, Verdict
from run_atlas_recovery_qualification import (
    AtlasRecoveryInvocationError, _manifest_path, prepare_invocation,
)
from run_minecraft_qualification import ROOT, SourceProvenance, collect_source_provenance, load_manifest


_RUN_ID = re.compile(r"^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True, help="one exact canonical manifest cell ID")
    result.add_argument("--quick-run-id", required=True, help="completed quick-matrix run supplying evidence and jar")
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    return result


Executor = Callable[..., ExternalWorldgenResult]


def run(arguments: argparse.Namespace, *, repository_root: Path = ROOT,
        provenance_provider: Callable[[Path, Path], SourceProvenance] = collect_source_provenance,
        candidate_inspector: Callable[[Path, str], FrozenCandidateInspection] = inspect_frozen_candidate,
        run_id_factory: Callable[[], str] = new_run_id,
        executor: Executor = execute_external_runtime_worldgen) -> ExternalWorldgenResult:
    manifest_path = _manifest_path(repository_root.resolve(strict=False), arguments.manifest)
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise AtlasRecoveryInvocationError("worldgen executor produced an unsafe run ID")
    prepared = prepare_invocation(
        repository_root=repository_root, manifest_path=manifest_path,
        cell_id=arguments.cell, quick_run_id=arguments.quick_run_id, run_id=run_id,
        provenance_provider=provenance_provider, candidate_inspector=candidate_inspector,
    )
    manifest = load_manifest(manifest_path)
    canonical = canonical_cells_from_manifest(tuple(item for item in manifest["cells"] if isinstance(item, dict)))
    plan = external_runtime_worldgen_plan(
        prepared.cell, prepared.candidate, prepared.paths, prepared.quick_terminal_evidence,
        frozen_candidate_root=prepared.frozen_candidate_root,
        quick_evidence_root=prepared.quick_paths.cell_root,
    )
    return executor(
        plan, prepared.paths, run_id, canonical_cells=canonical,
        range_identities=reviewed_range_identities(),
        candidate_inspector=candidate_inspector,
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
        "fixture": "worldgen-seam-structures", "cell": result.cell_id,
        "loader": result.loader, "minecraft": result.minecraft_version,
        "verdict": result.verdict.value, "reason": result.reason,
        "terminal_evidence": result.evidence_json,
    }, sort_keys=True))
    return 0 if result.verdict is Verdict.PASS else 1


if __name__ == "__main__":
    raise SystemExit(main())
