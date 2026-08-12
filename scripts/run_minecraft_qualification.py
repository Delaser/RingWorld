#!/usr/bin/env python3
"""Plan the fail-closed Minecraft quick-qualification matrix.

Phase 3 currently supplies validation, immutable planning, and report models.
It deliberately has no Gradle execution adapter.  Every phase after planning
is reported INCOMPLETE, never PASS.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any

from minecraft_qualification_model import (
    InvocationError,
    plan_matrix,
    render_json,
    render_markdown,
    require_safe_identifier,
    select_cells,
)
from validate_minecraft_version_matrix import validate_manifest


ROOT = Path(__file__).resolve().parents[1]


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--tier", required=True, choices=("quick",), help="only quick is implemented")
    result.add_argument("--cell", action="append", default=[], help="explicit matrix cell ID; repeatable")
    result.add_argument("--all", action="store_true", help="select all manifest cells")
    result.add_argument("--all-supported", action="store_true", help="select passing and published cells")
    result.add_argument("--manifest", default="config/minecraft-version-matrix.json", help="manifest JSON path")
    result.add_argument("--jobs", type=int, default=1, help="planned parallelism; execution is not yet available")
    result.add_argument("--fail-fast", action="store_true", help="reserved for the future execution adapter")
    result.add_argument("--resume", action="store_true", help="reserved for immutable evidence-aware execution")
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


def main(argv: list[str] | None = None, *, repository_root: Path = ROOT) -> int:
    args = parser().parse_args(argv)
    if args.jobs < 1:
        print("INVOCATION ERROR: --jobs must be positive", file=sys.stderr)
        return 2
    try:
        manifest = load_manifest((repository_root / args.manifest).resolve(strict=False) if not Path(args.manifest).is_absolute() else Path(args.manifest))
        cells = select_cells(manifest, args.cell, all_cells=args.all, all_supported=args.all_supported)
        # A static dry-run ID keeps planning/rendering deterministic and writes nothing.
        run_id = "dry-run" if args.dry_run else require_safe_identifier("planned-qualification", "run id")
        report = plan_matrix(
            cells,
            repository_root,
            run_id,
            dry_run=args.dry_run,
        )
    except InvocationError as error:
        print(f"INVOCATION ERROR: {error}", file=sys.stderr)
        return 2
    print(render_markdown(report), end="")
    print(render_json(report), end="")
    return 0 if report.verdict.value == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
