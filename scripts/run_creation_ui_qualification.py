#!/usr/bin/env python3
"""Run the external Prism creation-UI fixture for one selected manifest cell."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Callable

from external_graphical_creation_ui import GraphicalCreationUiResult, creation_ui_plan, execute_creation_ui
from minecraft_qualification_executor import QualificationExecutionError, QualificationLock, new_run_id
from minecraft_qualification_model import InvocationError, Verdict
from run_atlas_recovery_qualification import ROOT, _RUN_ID, _manifest_path, prepare_invocation


class CreationUiInvocationError(QualificationExecutionError):
    """The graphical client preflight failed before Prism execution."""


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--cell", required=True, help="one exact canonical manifest cell ID")
    result.add_argument("--quick-run-id", required=True, help="passed quick run supplying the frozen jar/evidence")
    result.add_argument("--prism-archive", required=True, help="absolute reviewed Prism 11.0.3 macOS archive")
    result.add_argument("--java", required=True, help="absolute Java 25 executable")
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json")
    return result


Executor = Callable[..., GraphicalCreationUiResult]


def run(
    arguments: argparse.Namespace,
    *,
    repository_root: Path = ROOT,
    run_id_factory: Callable[[], str] = new_run_id,
    executor: Executor = execute_creation_ui,
) -> GraphicalCreationUiResult:
    root = repository_root.resolve(strict=False)
    run_id = run_id_factory()
    if not isinstance(run_id, str) or _RUN_ID.fullmatch(run_id) is None:
        raise CreationUiInvocationError("creation UI executor produced an unsafe run ID")
    manifest_path = _manifest_path(root, arguments.manifest)
    prepared = prepare_invocation(
        repository_root=root,
        manifest_path=manifest_path,
        cell_id=arguments.cell,
        quick_run_id=arguments.quick_run_id,
        run_id=run_id,
    )
    prism_archive = Path(arguments.prism_archive)
    java = Path(arguments.java)
    if not prism_archive.is_absolute() or not java.is_absolute():
        raise CreationUiInvocationError("--prism-archive and --java must be absolute")
    plan = creation_ui_plan(
        prepared.cell,
        prepared.paths,
        prepared.candidate,
        prism_archive=prism_archive,
        java_executable=java,
        source_provenance=prepared.source_provenance,
    )
    with QualificationLock.acquire(prepared.paths.lock_path, run_id) as held_lock:
        return executor(plan, prepared.paths, held_lock=held_lock)


def main(argv: list[str] | None = None, *, repository_root: Path = ROOT) -> int:
    arguments = parser().parse_args(argv)
    try:
        result = run(arguments, repository_root=repository_root)
    except (CreationUiInvocationError, InvocationError, QualificationExecutionError, OSError, ValueError) as error:
        print("INVOCATION ERROR: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps({
        "fixture": "creation-settings-ui",
        "cell": result.cell_id,
        "loader": result.loader,
        "minecraft": result.minecraft_version,
        "verdict": result.verdict.value,
        "reason": result.reason,
        "captures": len(result.captures),
    }, sort_keys=True))
    return 0 if result.verdict is Verdict.PASS else 1


if __name__ == "__main__":
    raise SystemExit(main())
