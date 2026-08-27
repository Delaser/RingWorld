#!/usr/bin/env python3
"""Fail-closed release-candidate equivalence for one RingWorld loader.

This is deliberately a byte-level *release staging* guard, not runtime
qualification.  It proves that a proposed public jar is the already-qualified
wide-range candidate with only its public version descriptors changed.  ZIP
member order and timestamps are intentionally ignored; every member name and
every non-metadata byte must match.
"""

from __future__ import annotations

import argparse
from collections import Counter
import copy
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Any, Mapping
import zipfile

from minecraft_frozen_candidate import (
    FrozenCandidateError,
    FrozenCandidateInspection,
    inspect_frozen_candidate,
)
from minecraft_support_contract import LEGACY_CONTRACT, SupportContract, contract_from_manifest
from run_minecraft_qualification import load_manifest
from verify_distribution_license import (
    VerificationError,
    read_jar_metadata,
    verify_jar_path,
)


LOADERS = frozenset(("fabric", "neoforge"))
BUILD_PROPERTIES = "ringworld-build.properties"
_ALLOWED_DIFFERENCES = {
    "fabric": frozenset(("fabric.mod.json", BUILD_PROPERTIES)),
    "neoforge": frozenset(("META-INF/neoforge.mods.toml", BUILD_PROPERTIES)),
}


class ReleaseEquivalenceError(ValueError):
    """The proposed public jar is not an equivalent release candidate."""


@dataclass(frozen=True)
class ReleaseCandidateEquivalence:
    """The successful byte-equivalence record for one loader."""

    loader: str
    qualification: FrozenCandidateInspection
    release_path: str
    release_sha256: str
    release_version: str
    release_label: str
    allowed_differences: tuple[str, ...]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _require_jar(path: Path, label: str) -> None:
    if not path.is_file() or path.suffix.lower() != ".jar":
        raise ReleaseEquivalenceError(f"{label} must be an existing .jar file")


def _archive_members(path: Path, label: str) -> dict[str, zipfile.ZipInfo]:
    """Return unique archive members while rejecting duplicate names early."""
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
    except (OSError, zipfile.BadZipFile) as error:
        raise ReleaseEquivalenceError(f"cannot open {label}: {error}") from error
    names = [info.filename for info in infos]
    duplicates = sorted(name for name, count in Counter(names).items() if count > 1)
    if duplicates:
        raise ReleaseEquivalenceError(f"{label} contains duplicate archive entry {duplicates[0]!r}")
    return {info.filename: info for info in infos}


def _read_properties(archive: zipfile.ZipFile, label: str) -> Mapping[str, str]:
    try:
        text = archive.read(BUILD_PROPERTIES).decode("utf-8")
    except KeyError as error:
        raise ReleaseEquivalenceError(f"{label} is missing {BUILD_PROPERTIES}") from error
    except UnicodeDecodeError as error:
        raise ReleaseEquivalenceError(f"{label} has non-UTF-8 {BUILD_PROPERTIES}") from error
    result: dict[str, str] = {}
    for line_number, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if not separator or not key or key in result:
            raise ReleaseEquivalenceError(
                f"{label} {BUILD_PROPERTIES}:{line_number}: malformed or duplicate property"
            )
        result[key] = value
    return result


def _neoforge_mod(metadata: Mapping[str, Any]) -> Mapping[str, Any]:
    mods = metadata.get("mods")
    if not isinstance(mods, list):
        raise ReleaseEquivalenceError("release NeoForge metadata has no mods table")
    matches = [mod for mod in mods if isinstance(mod, Mapping) and mod.get("modId") == "ringworld"]
    if len(matches) != 1:
        raise ReleaseEquivalenceError("release NeoForge metadata must declare exactly one RingWorld mod")
    return matches[0]


def _neoforge_dependency(metadata: Mapping[str, Any], mod_id: str) -> Mapping[str, Any]:
    dependencies = metadata.get("dependencies")
    entries = dependencies.get("ringworld") if isinstance(dependencies, Mapping) else None
    if not isinstance(entries, list):
        raise ReleaseEquivalenceError("release NeoForge metadata has no RingWorld dependency table")
    matches = [entry for entry in entries if isinstance(entry, Mapping) and entry.get("modId") == mod_id]
    if len(matches) != 1:
        raise ReleaseEquivalenceError(
            f"release NeoForge metadata must declare exactly one {mod_id} dependency"
        )
    return matches[0]


def _verify_release_metadata(
    path: Path,
    loader: str,
    expected_license: bytes,
    release_version: str,
    release_label: str,
    contract: SupportContract,
) -> None:
    """Apply the existing license/loader verifier, then enforce public identity."""
    try:
        resolved_loader = verify_jar_path(path, expected_license, loader=loader)
    except (OSError, zipfile.BadZipFile, VerificationError) as error:
        raise ReleaseEquivalenceError(f"release jar failed distribution verification: {error}") from error
    if resolved_loader != loader:  # Defensive; verify_jar_path already enforces it.
        raise ReleaseEquivalenceError("release jar loader does not match requested loader")

    try:
        with zipfile.ZipFile(path) as archive:
            resolved_loader, metadata = read_jar_metadata(archive, str(path), loader=loader)
            if resolved_loader != loader:
                raise ReleaseEquivalenceError("release jar loader does not match requested loader")
            build = _read_properties(archive, "release jar")
    except (OSError, zipfile.BadZipFile, VerificationError) as error:
        raise ReleaseEquivalenceError(f"release jar metadata is invalid: {error}") from error

    if build.get("artifactVersion") != release_version:
        raise ReleaseEquivalenceError("release ringworld-build.properties artifactVersion is not approved")
    if build.get("releaseLabel") != release_label:
        raise ReleaseEquivalenceError("release ringworld-build.properties releaseLabel is not approved")

    if loader == "fabric":
        if metadata.get("id") != "ringworld" or metadata.get("version") != release_version:
            raise ReleaseEquivalenceError("release Fabric identity does not match approved version")
        dependencies = metadata.get("depends")
        if not isinstance(dependencies, Mapping) \
                or dependencies.get("minecraft") != contract.minecraft_range(loader):
            raise ReleaseEquivalenceError("release Fabric jar does not declare the manifest-approved range")
        return

    mod = _neoforge_mod(metadata)
    if mod.get("version") != release_version:
        raise ReleaseEquivalenceError("release NeoForge identity does not match approved version")
    minecraft = _neoforge_dependency(metadata, "minecraft")
    neoforge = _neoforge_dependency(metadata, "neoforge")
    if minecraft.get("versionRange") != contract.minecraft_range(loader):
        raise ReleaseEquivalenceError("release NeoForge jar does not declare the manifest-approved Minecraft range")
    if neoforge.get("versionRange") != contract.neoforge_range:
        raise ReleaseEquivalenceError("release NeoForge jar does not declare the approved loader range")


def _expected_release_metadata(
    qualification_metadata: Mapping[str, Any], loader: str, release_version: str, contract: SupportContract,
) -> Mapping[str, Any]:
    """Copy frozen metadata and apply the only reviewed public substitutions."""
    expected = copy.deepcopy(qualification_metadata)
    if loader == "fabric":
        dependencies = expected.get("depends")
        if not isinstance(dependencies, dict):
            raise ReleaseEquivalenceError("qualification Fabric metadata has no depends object")
        expected["version"] = release_version
        dependencies["minecraft"] = contract.minecraft_range(loader)
        return expected

    mod = _neoforge_mod(expected)
    minecraft = _neoforge_dependency(expected, "minecraft")
    neoforge = _neoforge_dependency(expected, "neoforge")
    # These are the only TOML fields a public release is allowed to alter.
    mod["version"] = release_version
    minecraft["versionRange"] = contract.minecraft_range(loader)
    neoforge["versionRange"] = contract.neoforge_range
    return expected


def _verify_semantic_metadata_transforms(
    qualification_path: Path,
    release_path: Path,
    loader: str,
    release_version: str,
    release_label: str,
    contract: SupportContract,
) -> None:
    """Reject hidden descriptor/property changes inside the byte allowlist."""
    try:
        with zipfile.ZipFile(qualification_path) as qualification, zipfile.ZipFile(release_path) as release:
            qualification_loader, qualification_metadata = read_jar_metadata(
                qualification, str(qualification_path), loader=loader
            )
            release_loader, release_metadata = read_jar_metadata(release, str(release_path), loader=loader)
            qualification_build = _read_properties(qualification, "qualification jar")
            release_build = _read_properties(release, "release jar")
    except (OSError, zipfile.BadZipFile, VerificationError) as error:
        raise ReleaseEquivalenceError(f"cannot read release-equivalence metadata: {error}") from error
    if qualification_loader != loader or release_loader != loader:
        raise ReleaseEquivalenceError("release-equivalence metadata loader mismatch")

    if release_metadata != _expected_release_metadata(qualification_metadata, loader, release_version, contract):
        raise ReleaseEquivalenceError(
            "release descriptor changes fields outside the approved public version transform"
        )
    expected_build = dict(qualification_build)
    expected_build["artifactVersion"] = release_version
    expected_build["releaseLabel"] = release_label
    if release_build != expected_build:
        raise ReleaseEquivalenceError(
            "release ringworld-build.properties changes fields outside artifactVersion/releaseLabel"
        )


def _compare_members(
    qualification_path: Path,
    release_path: Path,
    loader: str,
) -> None:
    qualification_members = _archive_members(qualification_path, "qualification jar")
    release_members = _archive_members(release_path, "release jar")
    qualification_names, release_names = set(qualification_members), set(release_members)
    added, removed = sorted(release_names - qualification_names), sorted(qualification_names - release_names)
    if added or removed:
        detail = f"added {added[0]!r}" if added else f"removed {removed[0]!r}"
        raise ReleaseEquivalenceError(f"release jar has a different archive member set: {detail}")

    allowed = _ALLOWED_DIFFERENCES[loader]
    try:
        with zipfile.ZipFile(qualification_path) as qualification, zipfile.ZipFile(release_path) as release:
            for name in sorted(qualification_names - allowed):
                if qualification.read(qualification_members[name]) != release.read(release_members[name]):
                    raise ReleaseEquivalenceError(
                        f"release jar differs outside approved public metadata: {name}"
                    )
    except (OSError, zipfile.BadZipFile) as error:
        raise ReleaseEquivalenceError(f"cannot compare jar contents: {error}") from error


def _replace_toml_table_field(text: str, table: str, mod_id: str | None, field: str, value: str) -> str:
    pattern = re.compile(rf"(?ms)(^\[\[{re.escape(table)}\]\][\s\S]*?)(?=^\[\[|\Z)")
    matches = list(pattern.finditer(text))
    changed = 0
    parts: list[str] = []
    cursor = 0
    for match in matches:
        block = match.group(1)
        if mod_id is not None and re.search(rf'(?m)^modId\s*=\s*"{re.escape(mod_id)}"\s*$', block) is None:
            continue
        replacement, count = re.subn(rf'(?m)^({re.escape(field)}[ \t]*=[ \t]*)"[^"]*"[ \t]*$',
                                     rf'\g<1>"{value}"', block)
        if count != 1:
            raise ReleaseEquivalenceError(f"qualification TOML has no unique {table}.{field} field")
        parts.extend((text[cursor:match.start(1)], replacement))
        cursor = match.end(1)
        changed += 1
    if changed != 1:
        raise ReleaseEquivalenceError(f"qualification TOML has no unique {table} table")
    parts.append(text[cursor:])
    return "".join(parts)


def _materialized_metadata(data: bytes, loader: str, release_version: str, contract: SupportContract) -> bytes:
    if loader == "fabric":
        try:
            metadata = json.loads(data.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ReleaseEquivalenceError("qualification Fabric descriptor is invalid JSON") from error
        if not isinstance(metadata, Mapping):
            raise ReleaseEquivalenceError("qualification Fabric descriptor is not an object")
        expected = _expected_release_metadata(metadata, loader, release_version, contract)
        return json.dumps(expected, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ReleaseEquivalenceError("qualification NeoForge descriptor is not UTF-8") from error
    text = _replace_toml_table_field(text, "mods", "ringworld", "version", release_version)
    text = _replace_toml_table_field(text, "dependencies.ringworld", "minecraft", "versionRange",
                                     contract.minecraft_range(loader))
    return _replace_toml_table_field(text, "dependencies.ringworld", "neoforge", "versionRange",
                                     contract.neoforge_range).encode("utf-8")


def _materialized_build_properties(data: bytes, release_version: str, release_label: str) -> bytes:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ReleaseEquivalenceError("qualification ringworld-build.properties is not UTF-8") from error
    for key, value in (("artifactVersion", release_version), ("releaseLabel", release_label)):
        text, count = re.subn(rf"(?m)^({key}=).*?$", rf"\g<1>{value}", text)
        if count != 1:
            raise ReleaseEquivalenceError(f"qualification ringworld-build.properties has no unique {key}")
    return text.encode("utf-8")


def materialize_release_candidate(
    qualification_jar: Path, release_jar: Path, *, loader: str, expected_license: bytes,
    release_version: str, release_label: str, contract: SupportContract = LEGACY_CONTRACT,
) -> ReleaseCandidateEquivalence:
    """Create and then verify the sole allowed metadata-only public jar transform."""
    qualification_jar, release_jar = Path(qualification_jar), Path(release_jar)
    if release_jar.exists() or release_jar.is_symlink():
        raise ReleaseEquivalenceError("refusing to replace an existing materialized release jar")
    _require_jar(qualification_jar, "qualification jar")
    if loader not in LOADERS or not isinstance(contract, SupportContract):
        raise ReleaseEquivalenceError("materialization requires a reviewed loader and support contract")
    metadata_name = "fabric.mod.json" if loader == "fabric" else "META-INF/neoforge.mods.toml"
    try:
        with zipfile.ZipFile(qualification_jar) as source, zipfile.ZipFile(release_jar, "w") as output:
            for info in source.infolist():
                payload = source.read(info)
                if info.filename == metadata_name:
                    payload = _materialized_metadata(payload, loader, release_version, contract)
                elif info.filename == BUILD_PROPERTIES:
                    payload = _materialized_build_properties(payload, release_version, release_label)
                output.writestr(info, payload)
    except Exception:
        release_jar.unlink(missing_ok=True)
        raise
    return verify_release_candidate_equivalence(
        qualification_jar, release_jar, loader=loader, expected_license=expected_license,
        release_version=release_version, release_label=release_label, contract=contract,
    )


def verify_release_candidate_equivalence(
    qualification_jar: Path,
    release_jar: Path,
    *,
    loader: str,
    expected_license: bytes,
    release_version: str,
    release_label: str,
    contract: SupportContract = LEGACY_CONTRACT,
) -> ReleaseCandidateEquivalence:
    """Prove one release jar differs only in reviewed public version metadata.

    ``qualification_jar`` must be the strict frozen wide-range candidate.  The
    release metadata values are explicit inputs so staging cannot silently
    accept a stale public version or release label.
    """
    if loader not in LOADERS:
        raise ReleaseEquivalenceError("loader must be fabric or neoforge")
    if not expected_license:
        raise ReleaseEquivalenceError("expected MPL license bytes are required")
    if not isinstance(contract, SupportContract) or not release_version or not release_label:
        raise ReleaseEquivalenceError("release version and release label are required")
    qualification_jar, release_jar = Path(qualification_jar), Path(release_jar)
    _require_jar(qualification_jar, "qualification jar")
    _require_jar(release_jar, "release jar")

    # Verify each file independently before comparing them.  The frozen
    # inspection also proves the qualification jar carries the reviewed broad
    # declaration from the selected candidate group, not merely a similarly-shaped descriptor.
    _archive_members(qualification_jar, "qualification jar")
    _archive_members(release_jar, "release jar")
    try:
        qualification_loader = verify_jar_path(qualification_jar, expected_license, loader=loader)
        qualification = inspect_frozen_candidate(qualification_jar, loader, contract=contract)
    except (OSError, zipfile.BadZipFile, VerificationError, FrozenCandidateError) as error:
        raise ReleaseEquivalenceError(f"qualification jar failed verification: {error}") from error
    if qualification_loader != loader or qualification.loader != loader:
        raise ReleaseEquivalenceError("qualification jar loader does not match requested loader")
    _verify_release_metadata(release_jar, loader, expected_license, release_version, release_label, contract)
    _verify_semantic_metadata_transforms(
        qualification_jar, release_jar, loader, release_version, release_label, contract
    )
    _compare_members(qualification_jar, release_jar, loader)
    return ReleaseCandidateEquivalence(
        loader=loader,
        qualification=qualification,
        release_path=str(release_jar),
        release_sha256=_sha256(release_jar),
        release_version=release_version,
        release_label=release_label,
        allowed_differences=tuple(sorted(_ALLOWED_DIFFERENCES[loader])),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify a RingWorld release jar is metadata-only different from a frozen candidate."
    )
    parser.add_argument("qualification_jar", type=Path)
    parser.add_argument("release_jar", type=Path)
    parser.add_argument("--loader", choices=sorted(LOADERS), required=True)
    parser.add_argument("--release-version", required=True)
    parser.add_argument("--release-label", required=True)
    parser.add_argument("--license", type=Path, default=Path("LICENSE"))
    parser.add_argument("--manifest", type=Path, default=Path("config/minecraft-version-matrix.json"))
    args = parser.parse_args()
    try:
        contract = contract_from_manifest(load_manifest(args.manifest))
        result = verify_release_candidate_equivalence(
            args.qualification_jar,
            args.release_jar,
            loader=args.loader,
            expected_license=args.license.read_bytes(),
            release_version=args.release_version,
            release_label=args.release_label,
            contract=contract,
        )
    except (OSError, ValueError, ReleaseEquivalenceError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    print(
        "PASS"
        f" loader={result.loader}"
        f" qualification_sha256={result.qualification.sha256}"
        f" release_sha256={result.release_sha256}"
        f" release_version={result.release_version}"
        f" release_label={result.release_label}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
