"""Strict same-source selection of reusable nightly coverage; no runtime actions."""
from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping, Sequence


def select_resume(*, root: Path, manifest: Mapping[str, Any], manifest_sha256: str,
                  quick_run_id: str, source_commit: str, production_inventory: Mapping[str, Any],
                  reports: Sequence[Path], expected: Mapping[str, Mapping[str, str]],
                  fixtures: Sequence[str], retry_failed: bool = False) -> dict[str, Any]:
    # Lazy import: the existing reviewer imports the nightly fixture catalogue.
    from review_composite_nightly_evidence import (
        CompositeEvidenceError, _load, _sha256, _result_by_identity, _coverage_details,
    )
    identities = {cell['id']: cell for cell in manifest['cells']}
    allowed = {(cell, fixture) for cell in expected for fixture in fixtures}
    selected: dict[tuple[str, str], dict[str, Any]] = {}
    references = []
    for supplied in reports:
        path = supplied if supplied.is_absolute() else root / supplied
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 64 * 1024**2:
            raise CompositeEvidenceError('resume aggregate must be a bounded regular file')
        report = _load(path)
        world = report.get('production_world')
        if not isinstance(world, Mapping):
            raise CompositeEvidenceError('resume production world is malformed')
        if (report.get('started_source_commit') != source_commit
                or report.get('quick_run_id') != quick_run_id
                or report.get('manifest_sha256') != manifest_sha256
                or world.get('inventory') != production_inventory):
            raise CompositeEvidenceError('resume source, quick run, manifest or production world differs')
        if report.get('verdict') not in {'PASS', 'FAIL', 'INCOMPLETE', 'COMPOSITE_COVERAGE_REVIEWED'}:
            raise CompositeEvidenceError('resume aggregate verdict is invalid')
        rows = _result_by_identity(report)
        if not set(rows) <= allowed:
            raise CompositeEvidenceError('resume contains an unknown or unselected cell/fixture')
        reference = {'path': str(path), 'sha256': _sha256(path)}
        references.append(reference)
        for key, original in rows.items():
            if original.get('verdict') not in {'PASS', 'FAIL', 'INCOMPLETE'}:
                raise CompositeEvidenceError('resume fixture verdict is invalid')
            previous = selected.get(key)
            if previous and previous['verdict'] == 'PASS':
                if (original['verdict'] != 'PASS'
                        or original.get('terminal_evidence') != previous.get('terminal_evidence')
                        or original.get('retained_artifacts') != previous.get('retained_artifacts')):
                    raise CompositeEvidenceError('resume ambiguously replaces existing PASS evidence')
                continue  # Repeated immutable cumulative checkpoint, same terminals.
            if original['verdict'] == 'INCOMPLETE' and previous:
                continue  # A later partial checkpoint cannot erase a recorded failure.
            selected[key] = {**original, 'reused_from': reference}
    passed = {key: row for key, row in selected.items() if row['verdict'] == 'PASS'}
    # Reuse the exact frozen/source-ABI, terminal hash and retained-artifact review.
    # Validate each quick identity independently, including same-loader patch cells.
    for key, row in passed.items():
        cell = key[0]
        _coverage_details(
            identities=identities,
            hashes={identities[cell]['loader']: expected[cell]['frozen_candidate_sha256']},
            quick_records=[{'cell': cell, 'sha256': expected[cell]['quick_terminal_sha256']}],
            selected={key: row}, source_commits={key: source_commit}, require_terminal_source=True)
    blocked = {key: row for key, row in selected.items()
               if row['verdict'] == 'FAIL' and not retry_failed}
    return {'references': references, 'preserved': list(passed.values()),
            'blocked': list(blocked.values()),
            'pending': sorted(allowed - passed.keys() - blocked.keys()),
            'retry_failed_authorized': retry_failed}
