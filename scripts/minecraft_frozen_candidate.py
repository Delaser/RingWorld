#!/usr/bin/env python3
"""Strict inspection and same-file evidence for 26.1.x frozen candidates.

This module checks declarations and byte identity only.  It does not infer
runtime compatibility: every covered cell still needs its external smoke and
the later gameplay/rendering gates.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping, Sequence
import zipfile

from minecraft_qualification_ranges import (
    APPROVED_FABRIC_MINECRAFT_RANGE,
    APPROVED_NEOFORGE_LOADER_RANGE,
    APPROVED_NEOFORGE_MINECRAFT_RANGE,
    parse_fabric_minecraft_range,
    parse_minecraft_version,
    parse_neoforge_loader_range,
    parse_neoforge_minecraft_range,
)
from verify_distribution_license import parse_neoforge_metadata


EXPECTED_VERSIONS = ("26.1", "26.1.1", "26.1.2")
MAX_ARCHIVE_ENTRIES = 20_000
MAX_METADATA_BYTES = 1024 * 1024
_MPL_LICENSE = "MPL-2.0"
_CANONICAL_MPL_SHA256 = "1f256ecad192880510e84ad60474eab7589218784b9a50bc7ceee34c2b91f1d5"


class FrozenCandidateError(ValueError):
    """The candidate cannot participate in same-file qualification."""


@dataclass(frozen=True)
class FrozenCandidateInspection:
    path: str
    loader: str
    sha256: str
    artifact_version: str
    release_label: str
    minecraft_range: str
    loader_range: str | None
    covered_minecraft_versions: tuple[str, ...]


@dataclass(frozen=True)
class SameFileCoverage:
    loader: str
    sha256: str
    cell_ids: tuple[str, ...]
    minecraft_versions: tuple[str, ...]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _single_text(archive: zipfile.ZipFile, name: str) -> str:
    matches = [info for info in archive.infolist() if info.filename == name]
    if len(matches) != 1:
        raise FrozenCandidateError(f"candidate must contain exactly one {name}")
    if matches[0].file_size > MAX_METADATA_BYTES:
        raise FrozenCandidateError(f"candidate metadata is too large: {name}")
    try:
        return archive.read(matches[0]).decode("utf-8")
    except UnicodeDecodeError as error:
        raise FrozenCandidateError(f"candidate metadata is not UTF-8: {name}") from error


def _properties(text: str) -> Mapping[str, str]:
    result: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise FrozenCandidateError("ringworld-build.properties has an invalid line")
        key, value = line.split("=", 1)
        if key in result:
            raise FrozenCandidateError("ringworld-build.properties duplicates a key")
        result[key] = value
    return result


def _neoforge_dependency(metadata: Mapping[str, Any], mod_id: str) -> Mapping[str, Any]:
    dependencies = metadata.get("dependencies")
    entries = dependencies.get("ringworld") if isinstance(dependencies, Mapping) else None
    if not isinstance(entries, Sequence) or isinstance(entries, (str, bytes)):
        raise FrozenCandidateError("NeoForge candidate has no RingWorld dependency table")
    matches = [entry for entry in entries if isinstance(entry, Mapping) and entry.get("modId") == mod_id]
    if len(matches) != 1:
        raise FrozenCandidateError(f"NeoForge candidate must declare one {mod_id} dependency")
    return matches[0]


def inspect_frozen_candidate(path: Path, loader: str) -> FrozenCandidateInspection:
    """Inspect one candidate built once against the oldest supported ABI."""
    if loader not in {"fabric", "neoforge"}:
        raise FrozenCandidateError("loader must be fabric or neoforge")
    if not path.is_file() or path.suffix.lower() != ".jar":
        raise FrozenCandidateError("frozen candidate is not a jar")
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) > MAX_ARCHIVE_ENTRIES:
                raise FrozenCandidateError("candidate contains too many archive entries")
            duplicates = [name for name, count in Counter(names).items() if count > 1]
            if duplicates:
                raise FrozenCandidateError("candidate has duplicate archive entries")
            fabric_present = "fabric.mod.json" in names
            neo_present = "META-INF/neoforge.mods.toml" in names
            if fabric_present == neo_present or (loader == "fabric") != fabric_present:
                raise FrozenCandidateError("candidate loader metadata is missing or ambiguous")
            build = _properties(_single_text(archive, "ringworld-build.properties"))
            artifact_version = build.get("artifactVersion")
            release_label = build.get("releaseLabel")
            expected_artifact = "0.0.0-qualification+mc26.1"
            expected_label = f"qualification-26.1-{loader}"
            if artifact_version != expected_artifact or release_label != expected_label:
                raise FrozenCandidateError("candidate was not built once from the approved 26.1 ABI identity")
            license_text = _single_text(archive, "LICENSE-RINGWORLD.txt")
            if hashlib.sha256(license_text.encode("utf-8")).hexdigest() != _CANONICAL_MPL_SHA256:
                raise FrozenCandidateError("candidate does not embed the canonical RingWorld MPL-2.0 license")

            loader_range: str | None = None
            if loader == "fabric":
                try:
                    metadata = json.loads(_single_text(archive, "fabric.mod.json"))
                except json.JSONDecodeError as error:
                    raise FrozenCandidateError("Fabric metadata is invalid JSON") from error
                if not isinstance(metadata, Mapping) or metadata.get("id") != "ringworld" \
                        or metadata.get("version") != artifact_version \
                        or metadata.get("license") != _MPL_LICENSE:
                    raise FrozenCandidateError("Fabric candidate identity does not match")
                dependencies = metadata.get("depends")
                minecraft_range = dependencies.get("minecraft") if isinstance(dependencies, Mapping) else None
                if minecraft_range != APPROVED_FABRIC_MINECRAFT_RANGE:
                    raise FrozenCandidateError("Fabric candidate does not declare the approved Minecraft range")
                parsed_minecraft_range = parse_fabric_minecraft_range(minecraft_range)
            else:
                try:
                    metadata = parse_neoforge_metadata(_single_text(archive, "META-INF/neoforge.mods.toml"))
                except ValueError as error:
                    raise FrozenCandidateError("NeoForge metadata is invalid TOML") from error
                mods = metadata.get("mods")
                if not isinstance(mods, Sequence) or len(mods) != 1 or not isinstance(mods[0], Mapping) \
                        or mods[0].get("modId") != "ringworld" or mods[0].get("version") != artifact_version \
                        or metadata.get("license") != _MPL_LICENSE:
                    raise FrozenCandidateError("NeoForge candidate identity does not match")
                minecraft_range = _neoforge_dependency(metadata, "minecraft").get("versionRange")
                loader_range = _neoforge_dependency(metadata, "neoforge").get("versionRange")
                if minecraft_range != APPROVED_NEOFORGE_MINECRAFT_RANGE \
                        or loader_range != APPROVED_NEOFORGE_LOADER_RANGE:
                    raise FrozenCandidateError("NeoForge candidate does not declare the approved closed ranges")
                parsed_minecraft_range = parse_neoforge_minecraft_range(minecraft_range)
                parse_neoforge_loader_range(loader_range)

            covered = tuple(
                version for version in EXPECTED_VERSIONS
                if parsed_minecraft_range.contains(parse_minecraft_version(version))
            )
            if covered != EXPECTED_VERSIONS:
                raise FrozenCandidateError("candidate range does not cover every 26.1.x matrix target")
    except (OSError, zipfile.BadZipFile) as error:
        raise FrozenCandidateError(f"cannot inspect frozen candidate: {error}") from error
    return FrozenCandidateInspection(
        str(path), loader, _sha256(path), artifact_version, release_label,
        minecraft_range, loader_range, covered,
    )


def verify_same_file_coverage(
    loader: str,
    inspections_by_cell: Mapping[str, FrozenCandidateInspection],
) -> SameFileCoverage:
    """Require all three loader cells to reference one byte-identical file."""
    expected_cells = tuple(f"{version}-{loader}" for version in EXPECTED_VERSIONS)
    if tuple(sorted(inspections_by_cell)) != tuple(sorted(expected_cells)):
        raise FrozenCandidateError("same-file evidence must contain exactly three expected loader cells")
    inspections = tuple(inspections_by_cell[cell] for cell in expected_cells)
    if any(item.loader != loader for item in inspections):
        raise FrozenCandidateError("same-file evidence mixes loaders")
    hashes = {item.sha256 for item in inspections}
    paths = {item.path for item in inspections}
    if len(hashes) != 1 or len(paths) != 1:
        raise FrozenCandidateError("same-file evidence must use one unchanged candidate path and SHA-256")
    if any(item.covered_minecraft_versions != EXPECTED_VERSIONS for item in inspections):
        raise FrozenCandidateError("same-file candidate declaration does not cover the full matrix")
    return SameFileCoverage(loader, inspections[0].sha256, expected_cells, EXPECTED_VERSIONS)
