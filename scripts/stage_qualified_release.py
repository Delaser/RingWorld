#!/usr/bin/env python3
"""Stage qualified 26.1.x RingWorld candidates without publishing them."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import tempfile
from typing import Any, Mapping, Sequence
import zipfile

from external_runtime_qualification_adapter import (
    canonical_cells_from_manifest,
    reviewed_range_identities,
)
from minecraft_qualification_evidence import TerminalEvidenceError, validate_terminal_evidence
from minecraft_qualification_model import QualificationPaths, Verdict, require_safe_identifier
from release_candidate_equivalence import (
    ReleaseEquivalenceError,
    verify_release_candidate_equivalence,
)
from run_minecraft_qualification import load_manifest
from stage_modrinth_release import (
    GITHUB_COMMIT_PREFIX,
    PUBLIC_REPOSITORY,
    SOURCE_URL_PLACEHOLDER,
    VerificationError,
    current_public_source,
)


FORMAT = 1
MARKER = ".ringworld-qualified-stage"
LOADERS = ("fabric", "neoforge")
SHA256 = frozenset("0123456789abcdef")


class QualifiedStageError(ValueError):
    """Qualified evidence, candidates, or staging inputs are invalid."""


def _digest(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _regular(path: Path, label: str) -> Path:
    try:
        info = path.lstat()
    except OSError as error:
        raise QualifiedStageError(f"{label} is unavailable") from error
    if not stat.S_ISREG(info.st_mode) or path.is_symlink():
        raise QualifiedStageError(f"{label} must be a non-symlink regular file")
    return path


def _load_config(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(_regular(path, "release config").read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise QualifiedStageError("release config is invalid") from error
    if not isinstance(value, dict):
        raise QualifiedStageError("release config must be an object")
    required = {"artifact_version", "release_label", "game_versions", "modrinth", "curseforge", "rollback"}
    if set(value) != required:
        raise QualifiedStageError("release config fields do not match the qualified schema")
    versions = value["game_versions"]
    if versions != ["26.1", "26.1.1", "26.1.2"]:
        raise QualifiedStageError("release config must name exactly the qualified 26.1.x versions")
    if value["artifact_version"] != "1.1.0+mc26.1" or value["release_label"] != "1.1":
        raise QualifiedStageError("release config has an unreviewed public identity")
    return value


def _frozen_candidate(repository: Path, manifest: Mapping[str, Any], loader: str, run_id: str) -> Path:
    matches: list[Path] = []
    for cell in manifest.get("cells", ()):
        if isinstance(cell, Mapping) and cell.get("loader") == loader:
            paths = QualificationPaths.from_cell(repository, cell, run_id)
            candidate = paths.run_root / f"frozen-candidates/{loader}/ringworld-qualification.jar"
            if candidate.exists():
                matches.append(candidate)
    if len(matches) != 1:
        raise QualifiedStageError(f"quick run must retain exactly one {loader} frozen candidate")
    return _regular(matches[0], f"{loader} frozen candidate")


def validate_quick_matrix(
    repository: Path, manifest: Mapping[str, Any], run_id: str,
) -> tuple[dict[str, str], tuple[dict[str, str], ...]]:
    require_safe_identifier(run_id, "quick run id")
    cells = manifest.get("cells")
    if not isinstance(cells, Sequence) or isinstance(cells, (str, bytes)) or len(cells) != 6:
        raise QualifiedStageError("reviewed manifest must contain the six 26.1.x cells")
    canonical = canonical_cells_from_manifest(cells)
    ranges = reviewed_range_identities()
    candidates = {loader: _frozen_candidate(repository, manifest, loader, run_id) for loader in LOADERS}
    candidate_hashes = {loader: _digest(path, "sha256") for loader, path in candidates.items()}
    records: list[dict[str, str]] = []
    for cell in cells:
        if not isinstance(cell, Mapping):
            raise QualifiedStageError("reviewed manifest contains a malformed cell")
        paths = QualificationPaths.from_cell(repository, cell, run_id)
        evidence = _regular(paths.evidence_directory / "strict-terminal-evidence.json", "strict quick evidence")
        try:
            raw = json.loads(evidence.read_text(encoding="utf-8"))
            terminal = validate_terminal_evidence(raw, canonical, ranges)
        except (OSError, UnicodeError, json.JSONDecodeError, TerminalEvidenceError) as error:
            raise QualifiedStageError(f"{cell.get('id')} strict quick evidence is invalid") from error
        loader = str(cell.get("loader"))
        if terminal.verdict != Verdict.PASS.value or terminal.cell_id != cell.get("id") \
                or terminal.candidate_sha256 != candidate_hashes.get(loader):
            raise QualifiedStageError(f"{cell.get('id')} does not bind the retained frozen candidate")
        records.append({
            "cell": str(cell["id"]),
            "path": evidence.relative_to(repository).as_posix(),
            "sha256": _digest(evidence, "sha256"),
        })
    return candidate_hashes, tuple(records)


def _inventory(jar: Path) -> list[dict[str, Any]]:
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise QualifiedStageError("release jar contains duplicate archive members")
        return [
            {"path": item.filename, "size": item.file_size,
             "sha256": hashlib.sha256(archive.read(item)).hexdigest()}
            for item in sorted(archive.infolist(), key=lambda entry: entry.filename)
            if not item.is_dir()
        ]


def _render_changelog(template: str, loader: str, source_url: str) -> str:
    if template.count(SOURCE_URL_PLACEHOLDER) != 1:
        raise QualifiedStageError("qualified changelog must contain one source placeholder")
    note = ("Install Fabric Loader 0.19.3 or newer and the matching Fabric API for your "
            "Minecraft patch. RingWorld is required on the server and every client."
            if loader == "fabric" else
            "Install the matching NeoForge build for your Minecraft patch. RingWorld is "
            "required on the server and every client.")
    return (template.replace("{{LOADER}}", "Fabric" if loader == "fabric" else "NeoForge")
            .replace("{{INSTALL_NOTE}}", note)
            .replace(SOURCE_URL_PLACEHOLDER, source_url))


def _metadata(config: Mapping[str, Any], loader: str, changelog: str) -> tuple[dict[str, Any], dict[str, Any]]:
    versions = list(config["game_versions"])
    modrinth = config["modrinth"]
    curseforge = config["curseforge"]
    dependencies = ([{"project_id": modrinth["fabric_api_project_id"], "dependency_type": "required"}]
                    if loader == "fabric" else [])
    modrinth_record = {
        "project_id": modrinth["project_id"],
        "name": f"RingWorld 1.1 for Minecraft 26.1–26.1.2 ({'Fabric' if loader == 'fabric' else 'NeoForge'})",
        "version_number": modrinth[f"{loader}_version_number"],
        "version_type": "release", "featured": True,
        "game_versions": versions, "loaders": [loader],
        "dependencies": dependencies, "changelog": changelog,
    }
    relations = ([{"project_id": curseforge["fabric_api_project_id"],
                   "slug": curseforge["fabric_api_project_slug"],
                   "relation_type": "requiredDependency"}]
                 if loader == "fabric" else [])
    curseforge_record = {
        "project_id": curseforge["project_id"],
        "display_name": f"RingWorld 1.1 for {'Fabric' if loader == 'fabric' else 'NeoForge'}",
        "release_type": "release", "game_versions": versions,
        "loader": loader, "relations": relations, "changelog": changelog,
        "execution": "manual_owner_authorization_required",
    }
    return modrinth_record, curseforge_record


def _replace_stage(target: Path) -> None:
    if not target.exists():
        return
    marker = target / MARKER
    if not marker.is_file() or marker.read_text(encoding="utf-8") != "generated\n":
        raise QualifiedStageError(f"refusing to replace unrecognized stage {target}")
    shutil.rmtree(target)


def stage_qualified_release(
    *, repository: Path, manifest_path: Path, quick_run_id: str,
    fabric_jar: Path, neoforge_jar: Path, config_path: Path,
    changelog_path: Path, output_root: Path,
) -> Path:
    repository = repository.resolve(strict=True)
    source = current_public_source(repository)
    manifest = load_manifest(_regular(manifest_path, "qualification manifest"))
    config = _load_config(config_path)
    frozen_hashes, evidence = validate_quick_matrix(repository, manifest, quick_run_id)
    jars = {"fabric": _regular(fabric_jar, "Fabric release jar"),
            "neoforge": _regular(neoforge_jar, "NeoForge release jar")}
    frozen = {loader: _frozen_candidate(repository, manifest, loader, quick_run_id) for loader in LOADERS}
    equivalence: dict[str, Any] = {}
    for loader in LOADERS:
        result = verify_release_candidate_equivalence(
            frozen[loader], jars[loader], loader=loader,
            expected_license=(repository / "LICENSE").read_bytes(),
            release_version=config["artifact_version"], release_label=config["release_label"],
        )
        if result.qualification.sha256 != frozen_hashes[loader]:
            raise QualifiedStageError(f"{loader} equivalence does not bind the quick candidate")
        equivalence[loader] = result
    if current_public_source(repository) != source:
        raise QualifiedStageError("public source changed while staging")
    template = _regular(changelog_path, "qualified changelog").read_text(encoding="utf-8")
    target = output_root / config["artifact_version"]
    target.parent.mkdir(parents=True, exist_ok=True)
    _replace_stage(target)
    temporary = Path(tempfile.mkdtemp(prefix=".qualified-stage-", dir=target.parent))
    try:
        for loader in LOADERS:
            folder = temporary / loader
            folder.mkdir()
            staged_jar = folder / jars[loader].name
            shutil.copy2(jars[loader], staged_jar)
            changelog = _render_changelog(template, loader, source["url"])
            modrinth, curseforge = _metadata(config, loader, changelog)
            hashes = {"sha256": _digest(staged_jar, "sha256"), "sha512": _digest(staged_jar, "sha512")}
            manifest_record = {
                "format": FORMAT, "generated": True, "loader": loader,
                "artifact_version": config["artifact_version"], "release_label": config["release_label"],
                "upload_file": staged_jar.name, "upload_file_only": True,
                "size": staged_jar.stat().st_size, "hashes": hashes,
                "source": source, "game_versions": config["game_versions"],
                "quick_run_id": quick_run_id, "quick_evidence": list(evidence),
                "frozen_candidate_sha256": frozen_hashes[loader],
                "equivalence_allowed_differences": list(equivalence[loader].allowed_differences),
                "rollback": config["rollback"],
                "publication_action": "manual_owner_authorization_required",
            }
            (folder / "STAGING-MANIFEST.json").write_text(json.dumps(manifest_record, indent=2) + "\n")
            (folder / "ARCHIVE-INVENTORY.json").write_text(json.dumps(_inventory(staged_jar), indent=2) + "\n")
            (folder / "MODRINTH-VERSION.json").write_text(json.dumps(modrinth, indent=2) + "\n")
            (folder / "CURSEFORGE-UPLOAD.json").write_text(json.dumps(curseforge, indent=2) + "\n")
            (folder / "CHANGELOG.md").write_text(changelog, encoding="utf-8")
            (folder / "SHA256SUMS.txt").write_text(f"{hashes['sha256']}  {staged_jar.name}\n")
        (temporary / MARKER).write_text("generated\n")
        temporary.replace(target)
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--quick-run-id", required=True)
    parser.add_argument("--fabric-jar", type=Path, required=True)
    parser.add_argument("--neoforge-jar", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, default=Path("config/minecraft-version-matrix.json"))
    parser.add_argument("--config", type=Path, default=Path("deploy/qualified/26.1.x-release.json"))
    parser.add_argument("--changelog", type=Path, default=Path("deploy/qualified/26.1.x-changelog.md"))
    parser.add_argument("--output-root", type=Path, default=Path("dist/qualified-release"))
    args = parser.parse_args()
    try:
        target = stage_qualified_release(
            repository=Path.cwd(), manifest_path=args.manifest, quick_run_id=args.quick_run_id,
            fabric_jar=args.fabric_jar, neoforge_jar=args.neoforge_jar,
            config_path=args.config, changelog_path=args.changelog, output_root=args.output_root,
        )
    except (OSError, ValueError, zipfile.BadZipFile, VerificationError, ReleaseEquivalenceError) as error:
        print(f"FAIL {error}")
        return 1
    print(f"PASS staged qualified release at {target}")
    print("No upload, token, host mutation, tag, or deployment was performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
