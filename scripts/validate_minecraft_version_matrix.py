#!/usr/bin/env python3
"""Fail-closed validation for the Minecraft qualification version matrix."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import PurePosixPath
from typing import Any
from urllib.parse import urlparse


ALLOWED_LOADERS = {"fabric", "neoforge"}
ALLOWED_STATUSES = {"pending", "passing", "failing", "published"}
PROFILE_PATH_PREFIXES = {
    "run_directory": PurePosixPath("run/qualification"),
    "cache_directory": PurePosixPath("run/qualification-cache"),
    "evidence_directory": PurePosixPath("dist/qualification"),
}
CHECKSUM_LENGTHS = {"sha1": 40, "sha256": 64}
HEX_40 = re.compile(r"^[0-9a-f]{40}$")
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
VERSION = re.compile(r"^\d+(?:[.+-][0-9A-Za-z]+)*$")
FLOATING_VERSION = re.compile(r"(?:^|[._+-])(latest|release|snapshot|dev|nightly)(?:$|[._+-])", re.IGNORECASE)


def _is_mapping(value: Any) -> bool:
    return isinstance(value, dict)


def _error(errors: list[str], location: str, message: str) -> None:
    errors.append(f"{location}: {message}")


def _required_mapping(value: Any, location: str, errors: list[str]) -> dict[str, Any] | None:
    if not _is_mapping(value):
        _error(errors, location, "must be an object")
        return None
    return value


def _required_string(value: Any, location: str, errors: list[str]) -> str | None:
    if not isinstance(value, str) or not value:
        _error(errors, location, "must be a non-empty string")
        return None
    return value


def _validate_version(value: Any, location: str, errors: list[str]) -> None:
    version = _required_string(value, location, errors)
    if version is None:
        return
    if not VERSION.fullmatch(version) or FLOATING_VERSION.search(version):
        _error(errors, location, f"must be an exact non-floating version, got {version!r}")


def _validate_url(value: Any, location: str, errors: list[str]) -> None:
    url = _required_string(value, location, errors)
    if url is None:
        return
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc or parsed.fragment:
        _error(errors, location, "must be an immutable https URL without a fragment")


def _validate_checksum(value: Any, location: str, errors: list[str]) -> None:
    checksum = _required_mapping(value, location, errors)
    if checksum is None:
        return
    algorithm = checksum.get("algorithm")
    digest = checksum.get("value")
    if algorithm not in CHECKSUM_LENGTHS:
        _error(errors, f"{location}.algorithm", "must be sha1 or sha256")
        return
    if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]+", digest):
        _error(errors, f"{location}.value", "must be lower-case hexadecimal")
    elif len(digest) != CHECKSUM_LENGTHS[algorithm]:
        _error(errors, f"{location}.value", f"must be {CHECKSUM_LENGTHS[algorithm]} hexadecimal characters")


def _validate_downloads(value: Any, location: str, errors: list[str]) -> None:
    if not isinstance(value, list) or not value:
        _error(errors, location, "must be a non-empty list")
        return
    seen_names: set[str] = set()
    for index, download in enumerate(value):
        item_location = f"{location}[{index}]"
        item = _required_mapping(download, item_location, errors)
        if item is None:
            continue
        name = _required_string(item.get("name"), f"{item_location}.name", errors)
        if name and name in seen_names:
            _error(errors, f"{item_location}.name", f"duplicate download name {name!r}")
        if name:
            seen_names.add(name)
        _validate_url(item.get("url"), f"{item_location}.url", errors)
        _validate_checksum(item.get("checksum"), f"{item_location}.checksum", errors)
    if {"client", "server"} - seen_names:
        _error(errors, location, "must pin both client and server downloads")


def _validate_dependencies(value: Any, location: str, errors: list[str]) -> None:
    if not isinstance(value, list) or not value:
        _error(errors, location, "must be a non-empty list")
        return
    seen_coordinates: set[str] = set()
    for index, dependency in enumerate(value):
        item_location = f"{location}[{index}]"
        item = _required_mapping(dependency, item_location, errors)
        if item is None:
            continue
        _required_string(item.get("name"), f"{item_location}.name", errors)
        coordinate = _required_string(item.get("coordinate"), f"{item_location}.coordinate", errors)
        if coordinate and coordinate in seen_coordinates:
            _error(errors, f"{item_location}.coordinate", f"duplicate dependency {coordinate!r}")
        if coordinate:
            seen_coordinates.add(coordinate)
        _validate_version(item.get("version"), f"{item_location}.version", errors)
        _validate_url(item.get("url"), f"{item_location}.url", errors)
        _validate_checksum(item.get("checksum"), f"{item_location}.checksum", errors)


def _validate_path(
    value: Any,
    location: str,
    required_prefix: PurePosixPath,
    errors: list[str],
) -> str | None:
    path = _required_string(value, location, errors)
    if path is None:
        return None
    candidate = PurePosixPath(path)
    if candidate.is_absolute() or ".." in candidate.parts or path == ".":
        _error(errors, location, "must be a non-root-relative, non-traversing path")
        return None
    prefix_parts = required_prefix.parts
    if candidate.parts[:len(prefix_parts)] != prefix_parts or len(candidate.parts) == len(prefix_parts):
        _error(errors, location, f"must be a child of {required_prefix.as_posix()!r}")
        return None
    return path.rstrip("/")


def _validate_profile(value: Any, location: str, errors: list[str]) -> tuple[set[str], int | None]:
    profile = _required_mapping(value, location, errors)
    if profile is None:
        return set(), None
    paths: set[str] = set()
    for field, prefix in PROFILE_PATH_PREFIXES.items():
        path = _validate_path(profile.get(field), f"{location}.{field}", prefix, errors)
        if path:
            if path in paths:
                _error(errors, f"{location}.{field}", "profile paths must be distinct")
            paths.add(path)
    port = profile.get("server_port")
    if not isinstance(port, int) or isinstance(port, bool) or not 1024 <= port <= 65535:
        _error(errors, f"{location}.server_port", "must be an integer from 1024 through 65535")
        port = None
    _required_string(profile.get("fixture"), f"{location}.fixture", errors)
    for field in ("timeout_seconds", "allowed_parallelism"):
        candidate = profile.get(field)
        if not isinstance(candidate, int) or isinstance(candidate, bool) or candidate < 1:
            _error(errors, f"{location}.{field}", "must be a positive integer")
    return paths, port


def _validate_tags(cell: dict[str, Any], location: str, minecraft_version: str | None, errors: list[str]) -> None:
    tags = _required_mapping(cell.get("expected_game_version_tags"), f"{location}.expected_game_version_tags", errors)
    if tags is None:
        return
    for host in ("modrinth", "curseforge"):
        host_tags = tags.get(host)
        if not isinstance(host_tags, list) or host_tags != [minecraft_version]:
            _error(errors, f"{location}.expected_game_version_tags.{host}", "must contain exactly this cell's Minecraft version")


def _validate_artifact(cell: dict[str, Any], location: str, minecraft_version: str | None, loader: str | None, errors: list[str]) -> None:
    status = cell.get("status")
    artifact = cell.get("artifact")
    evidence = cell.get("evidence")
    if status in {"passing", "published"} and not _is_mapping(artifact):
        _error(errors, f"{location}.artifact", f"{status} cells require an immutable artifact")
    if status != "pending" and (not isinstance(evidence, list) or not evidence):
        _error(errors, f"{location}.evidence", f"{status} cells require immutable evidence")
    if artifact is not None:
        artifact_data = _required_mapping(artifact, f"{location}.artifact", errors)
        if artifact_data:
            shared_version = _required_string(artifact_data.get("shared_runtime_version"), f"{location}.artifact.shared_runtime_version", errors)
            host_version = _required_string(artifact_data.get("host_version"), f"{location}.artifact.host_version", errors)
            digest = artifact_data.get("sha256")
            if not isinstance(digest, str) or not HEX_64.fullmatch(digest):
                _error(errors, f"{location}.artifact.sha256", "must be a lower-case SHA-256")
            if minecraft_version and shared_version and f"mc{minecraft_version}" not in shared_version:
                _error(errors, f"{location}.artifact.shared_runtime_version", "must identify this Minecraft version")
            if minecraft_version and loader and host_version and (f"mc{minecraft_version}" not in host_version or loader not in host_version):
                _error(errors, f"{location}.artifact.host_version", "must identify this loader and Minecraft version")
    if evidence is not None:
        if not isinstance(evidence, list):
            _error(errors, f"{location}.evidence", "must be a list")
        else:
            for index, item in enumerate(evidence):
                evidence_location = f"{location}.evidence[{index}]"
                evidence_data = _required_mapping(item, evidence_location, errors)
                if evidence_data is None:
                    continue
                revision = evidence_data.get("source_revision")
                if not isinstance(revision, str) or not HEX_40.fullmatch(revision):
                    _error(errors, f"{evidence_location}.source_revision", "must be a full Git commit SHA-1")
                uri = evidence_data.get("uri")
                _validate_url(uri, f"{evidence_location}.uri", errors)
                if isinstance(uri, str) and isinstance(revision, str) and revision not in uri:
                    _error(errors, f"{evidence_location}.uri", "must name its immutable source revision")
                digest = evidence_data.get("artifact_sha256")
                if not isinstance(digest, str) or not HEX_64.fullmatch(digest):
                    _error(errors, f"{evidence_location}.artifact_sha256", "must be a lower-case SHA-256")
                elif _is_mapping(artifact) and digest != artifact.get("sha256"):
                    _error(errors, f"{evidence_location}.artifact_sha256", "must match the cell artifact SHA-256")


def _validate_same_artifact_claims(cells: list[Any], errors: list[str]) -> None:
    groups: dict[tuple[str, str], list[tuple[str, dict[str, Any], str, str]]] = {}
    for index, cell in enumerate(cells):
        if not _is_mapping(cell):
            continue
        claim = cell.get("same_artifact_claim")
        if claim is None:
            continue
        location = f"cells[{index}].same_artifact_claim"
        data = _required_mapping(claim, location, errors)
        if data is None:
            continue
        group = _required_string(data.get("group"), f"{location}.group", errors)
        digest = data.get("sha256")
        if not isinstance(digest, str) or not HEX_64.fullmatch(digest):
            _error(errors, f"{location}.sha256", "must be a lower-case SHA-256")
            continue
        artifact = cell.get("artifact")
        if not _is_mapping(artifact) or artifact.get("sha256") != digest:
            _error(errors, location, "must match this cell's immutable artifact SHA-256")
        loader = cell.get("loader")
        if group and isinstance(loader, str):
            groups.setdefault((group, loader), []).append(
                (location, data, str(cell.get("minecraft", {}).get("version", "")), digest)
            )
    for (group, loader), members in groups.items():
        declared_versions: list[str] | None = None
        for location, claim, minecraft_version, digest in members:
            versions = claim.get("minecraft_versions")
            if not isinstance(versions, list) or not all(isinstance(version, str) for version in versions):
                _error(errors, f"{location}.minecraft_versions", "must be a list of distinct Minecraft versions")
                continue
            if len(versions) != len(set(versions)) or minecraft_version not in versions:
                _error(errors, f"{location}.minecraft_versions", "must be distinct and include this cell's Minecraft version")
            if declared_versions is None:
                declared_versions = versions
            elif versions != declared_versions:
                _error(errors, location, f"same-artifact group {group!r} for {loader} must declare identical version lists")
            if digest != members[0][3]:
                _error(errors, f"{location}.sha256", f"same-artifact group {group!r} for {loader} must use one SHA-256")
        if declared_versions and len(members) != len(declared_versions):
            _error(errors, f"same_artifact_claim[{group}/{loader}]", "must have exactly one cell for every claimed Minecraft version")


def validate_manifest(manifest: Any) -> list[str]:
    """Return all fail-closed schema and policy violations in *manifest*."""
    errors: list[str] = []
    document = _required_mapping(manifest, "manifest", errors)
    if document is None:
        return errors
    if document.get("schema_version") != 1:
        _error(errors, "manifest.schema_version", "must be 1")
    _required_string(document.get("line"), "manifest.line", errors)
    cells = document.get("cells")
    if not isinstance(cells, list) or not cells:
        _error(errors, "manifest.cells", "must be a non-empty list")
        return errors

    cell_keys: set[tuple[str, str]] = set()
    ids: set[str] = set()
    paths: set[str] = set()
    ports: set[int] = set()
    for index, raw_cell in enumerate(cells):
        location = f"cells[{index}]"
        cell = _required_mapping(raw_cell, location, errors)
        if cell is None:
            continue
        cell_id = _required_string(cell.get("id"), f"{location}.id", errors)
        if cell_id and cell_id in ids:
            _error(errors, f"{location}.id", f"duplicate cell id {cell_id!r}")
        if cell_id:
            ids.add(cell_id)
        minecraft = _required_mapping(cell.get("minecraft"), f"{location}.minecraft", errors)
        minecraft_version: str | None = None
        if minecraft:
            minecraft_version = minecraft.get("version") if isinstance(minecraft.get("version"), str) else None
            _validate_version(minecraft.get("version"), f"{location}.minecraft.version", errors)
            if minecraft.get("java_major") != 25:
                _error(errors, f"{location}.minecraft.java_major", "must be Java 25")
            _validate_downloads(minecraft.get("downloads"), f"{location}.minecraft.downloads", errors)
        loader = cell.get("loader")
        if loader not in ALLOWED_LOADERS:
            _error(errors, f"{location}.loader", "must be fabric or neoforge")
            loader = None
        if minecraft_version and loader:
            key = (minecraft_version, loader)
            if key in cell_keys:
                _error(errors, location, f"duplicate qualification cell {minecraft_version}/{loader}")
            cell_keys.add(key)
            if cell_id and cell_id != f"{minecraft_version}-{loader}":
                _error(errors, f"{location}.id", "must equal '<minecraft-version>-<loader>'")
        status = cell.get("status")
        if status not in ALLOWED_STATUSES:
            _error(errors, f"{location}.status", f"must be one of {sorted(ALLOWED_STATUSES)}")
        _validate_dependencies(cell.get("dependencies"), f"{location}.dependencies", errors)
        pending_inputs = cell.get("pending_inputs")
        if status == "pending" and (not isinstance(pending_inputs, list) or not pending_inputs):
            _error(errors, f"{location}.pending_inputs", "pending cells must name their unresolved inputs")
        if pending_inputs is not None and not isinstance(pending_inputs, list):
            _error(errors, f"{location}.pending_inputs", "must be a list")
        elif isinstance(pending_inputs, list):
            for pending_index, pending in enumerate(pending_inputs):
                pending_location = f"{location}.pending_inputs[{pending_index}]"
                pending_data = _required_mapping(pending, pending_location, errors)
                if pending_data:
                    _required_string(pending_data.get("name"), f"{pending_location}.name", errors)
                    _required_string(pending_data.get("reason"), f"{pending_location}.reason", errors)
        profile_paths, port = _validate_profile(cell.get("profile"), f"{location}.profile", errors)
        for path in profile_paths:
            if path in paths:
                _error(errors, f"{location}.profile", f"profile path {path!r} is shared by more than one cell")
            paths.add(path)
        if port is not None:
            if port in ports:
                _error(errors, f"{location}.profile.server_port", f"port {port} is shared by more than one cell")
            ports.add(port)
        _validate_tags(cell, location, minecraft_version, errors)
        _validate_artifact(cell, location, minecraft_version, loader, errors)
    _validate_same_artifact_claims(cells, errors)
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", nargs="?", default="config/minecraft-version-matrix.json", help="manifest JSON path")
    args = parser.parse_args(argv)
    try:
        with open(args.manifest, encoding="utf-8") as source:
            manifest = json.load(source)
    except (OSError, json.JSONDecodeError) as error:
        print(f"FAIL {args.manifest}: {error}", file=sys.stderr)
        return 1
    errors = validate_manifest(manifest)
    if errors:
        print(f"FAIL {args.manifest}", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(f"PASS {args.manifest}: {len(manifest['cells'])} unique qualification cells")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
