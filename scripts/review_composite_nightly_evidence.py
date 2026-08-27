#!/usr/bin/env python3
"""Read and fail-closed review of one explicitly selected composite nightly record."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
from typing import Any, Mapping, Sequence

from minecraft_support_contract import contract_from_manifest
from run_minecraft_qualification import load_manifest
from stage_qualified_release import validate_quick_matrix
from run_minecraft_nightly_matrix import FIXTURES


EXACT_FIXTURES = frozenset({"worldgen", "atlas-recovery", "multiplayer", "raid",
                            "production-lifecycle", "production-render"})
EXTERNAL_EXACT_FIXTURES = frozenset({"worldgen", "atlas-recovery"})
TERMINAL_FIXTURE = {
    "creation-ui": "creation-settings-ui", "worldgen": "worldgen-seam-structures",
    "atlas-recovery": "atlas-prewarm-recovery", "atlas-ui": "atlas-ui-revision",
    "multiplayer": "multiplayer", "raid": "raid-seam", "map-compass": "map-compass-reconnect",
    "production-lifecycle": "production-lifecycle", "curved-objects": "curved-objects",
    "production-render": "production-atlas-render",
}


class CompositeEvidenceError(ValueError):
    """An explicit composite selection cannot be trusted."""


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _load(path: Path) -> Mapping[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise CompositeEvidenceError(f"evidence is not a regular file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CompositeEvidenceError(f"evidence is not valid JSON: {path}") from error
    if not isinstance(value, Mapping):
        raise CompositeEvidenceError("evidence root must be an object")
    return value


def _commit(value: Any, label: str) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str) or len(value) != 40 or any(char not in "0123456789abcdef" for char in value):
        raise CompositeEvidenceError(f"{label} commit is malformed")
    return value


def _hash_bound(value: Any) -> set[tuple[str, str]]:
    found: set[tuple[str, str]] = set()
    if isinstance(value, Mapping):
        path, digest = value.get("path"), value.get("sha256")
        if isinstance(path, str) and isinstance(digest, str):
            found.add((path, digest))
        for child in value.values():
            found.update(_hash_bound(child))
    elif isinstance(value, Sequence) and not isinstance(value, (str, bytes)):
        for child in value:
            found.update(_hash_bound(child))
    return found


def _bound_files(value: Any) -> set[tuple[str, str]]:
    found = _hash_bound(value)
    if isinstance(value, Mapping):
        for name in ("stdout", "stderr", "log"):
            path, digest = value.get(name), value.get(name + "_sha256", value.get(name + "Sha256"))
            if isinstance(path, str) and isinstance(digest, str):
                found.add((path, digest))
        for child in value.values():
            found.update(_bound_files(child))
    elif isinstance(value, Sequence) and not isinstance(value, (str, bytes)):
        for child in value:
            found.update(_bound_files(child))
    return found


def _resolve_terminal_paths(terminal_path: Path, hashes: set[tuple[str, str]]) -> set[tuple[str, str]]:
    """Resolve documented external-terminal relative artifact paths to their cell root."""
    cell_root = None
    for parent in terminal_path.parents:
        if parent.name == "evidence":
            cell_root = parent.parent
            break
    if cell_root is None:
        return hashes
    return {(str((cell_root / source).resolve()) if not Path(source).is_absolute() else source, digest)
            for source, digest in hashes}


def _terminal(path: Path, expected_cell: str, fixture: str, *, candidate_hash: str | None,
              quick_hash: str | None, loader: str, minecraft: str) -> dict[str, Any]:
    terminal = _load(path)
    if terminal.get("verdict") != "PASS" or terminal.get("cell", terminal.get("cell_id")) != expected_cell:
        raise CompositeEvidenceError("replacement terminal verdict/cell identity is invalid")
    expected_fixture = TERMINAL_FIXTURE[fixture]
    actual_fixture = terminal.get("fixture")
    allowed_fixtures = {expected_fixture}
    if fixture in {"multiplayer", "raid", "production-lifecycle", "production-render"}:
        allowed_fixtures.add("frozen-" + expected_fixture)
    if fixture == "atlas-ui":
        allowed_fixtures.add("client-handshake")
    if actual_fixture is None and not (fixture in EXTERNAL_EXACT_FIXTURES and isinstance(terminal.get("qualification"), Mapping)):
        raise CompositeEvidenceError("terminal fixture identity is missing")
    if actual_fixture is not None and actual_fixture not in allowed_fixtures:
        raise CompositeEvidenceError("replacement terminal fixture identity is invalid")
    terminal_minecraft = terminal.get("minecraft", terminal.get("minecraftVersion"))
    if terminal.get("loader") not in (None, loader) or terminal_minecraft not in (None, minecraft):
        raise CompositeEvidenceError("terminal loader/Minecraft identity is invalid")
    frozen, quick = terminal.get("frozen_candidate"), terminal.get("quick_evidence")
    if fixture in EXACT_FIXTURES:
        if isinstance(frozen, Mapping) and isinstance(quick, Mapping):
            bound_candidate, bound_quick = frozen.get("sha256"), quick.get("sha256")
        else:
            qualification = terminal.get("qualification")
            bound_candidate = qualification.get("frozenCandidateSha256") if isinstance(qualification, Mapping) else None
            bound_quick = qualification.get("quickTerminalEvidenceSha256") if isinstance(qualification, Mapping) else None
        if bound_candidate != candidate_hash or bound_quick != quick_hash:
            raise CompositeEvidenceError("exact-fixture terminal does not bind selected quick candidate/evidence")
        claims = terminal.get("claims")
        if fixture not in EXTERNAL_EXACT_FIXTURES and (not isinstance(claims, Mapping) or claims.get("frozen_candidate_jar") is not True):
            raise CompositeEvidenceError("frozen Gradle terminal must claim its frozen candidate")
        evidence_class = "exact-frozen-candidate"
    else:
        claims = terminal.get("claims")
        if not isinstance(claims, Mapping) or claims.get("frozen_candidate_jar") is not False:
            raise CompositeEvidenceError("source-ABI terminal must explicitly disclaim frozen-candidate evidence")
        evidence_class = "source-abi"
    source = terminal.get("source")
    source_commit = _commit(source.get("commit") if isinstance(source, Mapping) else terminal.get("started_source_commit"),
                            "terminal source")
    return {"path": str(path), "sha256": _sha256(path), "evidence_class": evidence_class,
            "source_commit": source_commit,
            "hash_bound": _resolve_terminal_paths(path, _bound_files(terminal))}


def _result_by_identity(report: Mapping[str, Any]) -> dict[tuple[str, str], Mapping[str, Any]]:
    raw = report.get("results")
    if not isinstance(raw, Sequence) or isinstance(raw, (str, bytes)):
        raise CompositeEvidenceError("aggregate has no result sequence")
    result: dict[tuple[str, str], Mapping[str, Any]] = {}
    for item in raw:
        if not isinstance(item, Mapping) or not isinstance(item.get("cell"), str) or not isinstance(item.get("fixture"), str):
            raise CompositeEvidenceError("aggregate result identity is malformed")
        key = (item["cell"], item["fixture"])
        if key in result:
            raise CompositeEvidenceError("aggregate duplicates a cell/fixture result")
        result[key] = item
    return result


def _verify_retained(result: Mapping[str, Any], terminal_hashes: set[tuple[str, str]]) -> list[dict[str, Any]]:
    raw = result.get("retained_artifacts", ())
    if not isinstance(raw, Sequence) or isinstance(raw, (str, bytes)):
        raise CompositeEvidenceError("retained artifacts are malformed")
    verified = []
    for item in raw:
        if not isinstance(item, Mapping):
            raise CompositeEvidenceError("retained artifact is malformed")
        source, retained, digest = item.get("source_path"), item.get("retained_path"), item.get("sha256")
        if not all(isinstance(value, str) for value in (source, retained, digest)) or (source, digest) not in terminal_hashes:
            raise CompositeEvidenceError("retained artifact is not bound by its terminal")
        path = Path(retained)
        if path.is_symlink() or not path.is_file() or _sha256(path) != digest:
            raise CompositeEvidenceError("relocated retained artifact hash is invalid")
        verified.append({"source_path": source, "retained_path": retained, "sha256": digest})
    retained_sources = {(item["source_path"], item["sha256"]) for item in verified}
    for source, digest in terminal_hashes:
        if Path(source).suffix.lower() not in {".png", ".log"}:
            continue
        original = Path(source)
        if original.is_file() and not original.is_symlink() and _sha256(original) == digest:
            continue
        if (source, digest) not in retained_sources:
            raise CompositeEvidenceError(f"hash-bound PNG/log is neither present nor retained: {source}")
    return verified


def review(*, repository: Path, manifest_path: Path, quick_run_id: str, primary: Path,
           raid_repair: Path, downstream: Path, downstream_fixtures: Sequence[str]) -> dict[str, Any]:
    manifest = load_manifest(manifest_path)
    hashes, quick_records = validate_quick_matrix(repository, manifest, quick_run_id, contract_from_manifest(manifest))
    cells = manifest.get("cells")
    if not isinstance(cells, Sequence) or isinstance(cells, (str, bytes)):
        raise CompositeEvidenceError("manifest has no cells")
    identities = {str(cell.get("id")): cell for cell in cells if isinstance(cell, Mapping) and isinstance(cell.get("id"), str)}
    if len(identities) != len(cells):
        raise CompositeEvidenceError("manifest cell identities are malformed or duplicated")
    for cell in identities.values():
        minecraft = cell.get("minecraft")
        if not isinstance(cell.get("loader"), str) or not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str):
            raise CompositeEvidenceError("manifest cell metadata is malformed")
    expected_keys = {(cell_id, fixture) for cell_id in identities for fixture in FIXTURES}
    base = _load(primary)
    if base.get("verdict") != "FAIL":
        raise CompositeEvidenceError("primary aggregate must remain a failed aggregate")
    if base.get("quick_run_id") != quick_run_id:
        raise CompositeEvidenceError("primary aggregate does not bind selected quick run")
    primary_commit = _commit(base.get("started_source_commit"), "primary aggregate source")
    results = _result_by_identity(base)
    if set(results) != expected_keys:
        raise CompositeEvidenceError("primary aggregate does not contain the exact manifest cell/fixture keyset")
    gaps = {key for key, item in results.items() if item.get("verdict") != "PASS"}
    if len(downstream_fixtures) != len(set(downstream_fixtures)):
        raise CompositeEvidenceError("downstream fixture selection contains duplicates")
    raid_keys = [key for key in gaps if key[1] == "raid"]
    if len(raid_keys) != 1:
        raise CompositeEvidenceError("primary aggregate must have exactly one repaired raid gap")
    cell, _ = raid_keys[0]
    expected_downstream = {fixture for gap_cell, fixture in gaps if gap_cell == cell and fixture != "raid"}
    if set(downstream_fixtures) != expected_downstream:
        raise CompositeEvidenceError("downstream fixture selection must explicitly cover exactly the primary gaps")
    selected: dict[tuple[str, str], Mapping[str, Any]] = dict(results)
    repair_cell = identities[cell]
    repair_minecraft = repair_cell.get("minecraft") if isinstance(repair_cell, Mapping) else None
    repair_loader = repair_cell.get("loader") if isinstance(repair_cell, Mapping) else None
    if repair_loader not in hashes or not isinstance(repair_minecraft, Mapping) or not isinstance(repair_minecraft.get("version"), str):
        raise CompositeEvidenceError("repaired raid cell has invalid manifest identity")
    repair = _terminal(raid_repair, cell, "raid", candidate_hash=hashes[repair_loader],
                       quick_hash=next(record["sha256"] for record in quick_records if record["cell"] == cell),
                       loader=repair_loader, minecraft=repair_minecraft["version"])
    selected[(cell, "raid")] = {"verdict": "PASS", "terminal_evidence": [repair], "retained_artifacts": []}
    downstream_report = _load(downstream)
    if downstream_report.get("quick_run_id") != quick_run_id:
        raise CompositeEvidenceError("downstream aggregate does not bind selected quick run")
    downstream_commit = _commit(downstream_report.get("started_source_commit"), "downstream aggregate source")
    downstream_results = _result_by_identity(downstream_report)
    for fixture in downstream_fixtures:
        item = downstream_results.get((cell, fixture))
        if item is None or item.get("verdict") != "PASS":
            raise CompositeEvidenceError("explicit downstream replacement is not PASS")
        selected[(cell, fixture)] = item
    coverage = []
    for (item_cell, fixture), item in sorted(selected.items()):
        if item.get("verdict") != "PASS":
            raise CompositeEvidenceError("composite leaves a non-PASS result")
        refs = item.get("terminal_evidence")
        if not isinstance(refs, Sequence) or isinstance(refs, (str, bytes)) or not refs:
            raise CompositeEvidenceError("composite result has no terminal reference")
        details = []
        for ref in refs:
            if not isinstance(ref, Mapping) or not isinstance(ref.get("path"), str) or not isinstance(ref.get("sha256"), str):
                raise CompositeEvidenceError("terminal reference is malformed")
            path = Path(ref["path"])
            if path.is_symlink() or not path.is_file() or _sha256(path) != ref["sha256"]:
                raise CompositeEvidenceError("terminal reference hash is invalid")
            cell_metadata = identities.get(item_cell)
            if not isinstance(cell_metadata, Mapping):
                raise CompositeEvidenceError("terminal cell is not in selected manifest")
            minecraft_metadata = cell_metadata.get("minecraft")
            if not isinstance(minecraft_metadata, Mapping) or not isinstance(minecraft_metadata.get("version"), str):
                raise CompositeEvidenceError("manifest cell has no Minecraft version")
            loader = cell_metadata.get("loader")
            if loader not in hashes:
                raise CompositeEvidenceError("manifest cell has unsupported loader")
            details.append(_terminal(path, item_cell, fixture, candidate_hash=hashes[loader],
                                    quick_hash=next(record["sha256"] for record in quick_records if record["cell"] == item_cell),
                                    loader=loader, minecraft=minecraft_metadata["version"]))
        terminal_hashes = set().union(*(detail["hash_bound"] for detail in details))
        audit_terminals = [{key: detail[key] for key in ("path", "sha256", "evidence_class", "source_commit")}
                           for detail in details]
        coverage.append({"cell": item_cell, "fixture": fixture, "terminals": audit_terminals,
                         "retained_artifacts": _verify_retained(item, terminal_hashes)})
    return {"format": 1, "verdict": "COMPOSITE_COVERAGE_REVIEWED", "monolithic_pass": False,
            "primary_aggregate": str(primary), "primary_verdict": "FAIL", "quick_run_id": quick_run_id,
            "manifest_cells": [{"id": cell_id, "loader": cell["loader"], "minecraft": cell["minecraft"]["version"]}
                               for cell_id, cell in sorted(identities.items())],
            "source_commits": {"primary_aggregate": primary_commit, "downstream_aggregate": downstream_commit},
            "quick_records": list(quick_records), "explicit_overrides": {"raid_repair": str(raid_repair),
            "downstream_aggregate": str(downstream), "downstream_fixtures": list(downstream_fixtures)},
            "coverage": coverage,
            "limitations": ["Composite review is not a monolithic nightly PASS.",
                            "Source-ABI fixtures remain distinct from exact frozen-candidate fixtures.",
                            "This report is release-audit input, not runtime qualification or publication evidence."]}


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--quick-run-id", required=True)
    parser.add_argument("--primary-aggregate", type=Path, required=True)
    parser.add_argument("--raid-repair", type=Path, required=True)
    parser.add_argument("--downstream-aggregate", type=Path, required=True)
    parser.add_argument("--downstream-fixture", action="append", required=True)
    parser.add_argument("--manifest", type=Path, default=Path("config/minecraft-version-matrix.json"))
    args = parser.parse_args(argv)
    try:
        result = review(repository=Path.cwd(), manifest_path=args.manifest, quick_run_id=args.quick_run_id,
                        primary=args.primary_aggregate, raid_repair=args.raid_repair,
                        downstream=args.downstream_aggregate, downstream_fixtures=args.downstream_fixture)
    except (OSError, ValueError, StopIteration) as error:
        print(f"COMPOSITE REVIEW FAIL: {error}", file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
