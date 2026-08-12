"""Strict, pure compatibility-range checks for frozen qualification candidates.

This module validates only the deliberately small range forms RingWorld has
reviewed for the 26.1.x line.  A successful result says that declared target
versions lie in declared metadata ranges; it is not build, smoke, gameplay,
or release evidence and must never be treated as a support claim.
"""

from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any, Mapping, Sequence


class CompatibilityRangeError(ValueError):
    """A declared range or manifest target is outside the strict contract."""


_MINECRAFT_VERSION = re.compile(r"(?P<major>0|[1-9][0-9]*)\.(?P<minor>0|[1-9][0-9]*)(?:\.(?P<patch>0|[1-9][0-9]*))?")
_FABRIC_RANGE = re.compile(
    r">=(?P<lower>0|[1-9][0-9]*\.(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*))?) "
    r"<=(?P<upper>0|[1-9][0-9]*\.(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*))?)"
)
_NEOFORGE_VERSION = re.compile(
    r"(?P<major>0|[1-9][0-9]*)\.(?P<minor>0|[1-9][0-9]*)\.(?P<patch>0|[1-9][0-9]*)\.(?P<build>0|[1-9][0-9]*)"
    r"(?:-(?P<stage>alpha|beta|rc)(?:\.(?P<stage_number>0|[1-9][0-9]*))?)?"
)
_NEOFORGE_RANGE = re.compile(r"\[(?P<lower>[^,\[\]()\s]+),(?P<upper>[^,\[\]()\s]+)\]")

APPROVED_FABRIC_MINECRAFT_RANGE = ">=26.1 <=26.1.2"
APPROVED_NEOFORGE_MINECRAFT_RANGE = "[26.1,26.1.2]"
APPROVED_NEOFORGE_LOADER_RANGE = "[26.1.0.19-beta,26.1.2.87]"


@dataclass(frozen=True, order=True)
class MinecraftVersion:
    """A normalised stable Minecraft release identifier."""

    major: int
    minor: int
    patch: int = 0


@dataclass(frozen=True, order=True)
class NeoForgeVersion:
    """A NeoForge release identifier with prerelease ordering preserved."""

    major: int
    minor: int
    patch: int
    build: int
    stage_rank: int
    stage_number: int


@dataclass(frozen=True)
class FabricMinecraftRange:
    lower: MinecraftVersion
    upper: MinecraftVersion

    def contains(self, version: MinecraftVersion) -> bool:
        return self.lower <= version <= self.upper


@dataclass(frozen=True)
class NeoForgeRange:
    lower: MinecraftVersion | NeoForgeVersion
    upper: MinecraftVersion | NeoForgeVersion

    def contains(self, version: MinecraftVersion | NeoForgeVersion) -> bool:
        if type(version) is not type(self.lower) or type(self.lower) is not type(self.upper):
            raise CompatibilityRangeError("version and range bounds must have the same kind")
        return self.lower <= version <= self.upper


@dataclass(frozen=True)
class ManifestRangeCoverage:
    """Pure declaration coverage; deliberately contains no support verdict."""

    fabric_minecraft_cells: tuple[str, ...]
    neoforge_minecraft_cells: tuple[str, ...]
    neoforge_loader_cells: tuple[str, ...]


def parse_minecraft_version(value: str) -> MinecraftVersion:
    if not isinstance(value, str):
        raise CompatibilityRangeError("Minecraft version must be a string")
    match = _MINECRAFT_VERSION.fullmatch(value)
    if match is None:
        raise CompatibilityRangeError("Minecraft version must be major.minor or major.minor.patch")
    return MinecraftVersion(int(match.group("major")), int(match.group("minor")), int(match.group("patch") or 0))


def parse_neoforge_version(value: str) -> NeoForgeVersion:
    if not isinstance(value, str):
        raise CompatibilityRangeError("NeoForge version must be a string")
    match = _NEOFORGE_VERSION.fullmatch(value)
    if match is None:
        raise CompatibilityRangeError("NeoForge version must be major.minor.patch.build with optional -alpha/-beta/-rc")
    stage = match.group("stage")
    stage_rank = {"alpha": 0, "beta": 1, "rc": 2, None: 3}[stage]
    return NeoForgeVersion(
        int(match.group("major")),
        int(match.group("minor")),
        int(match.group("patch")),
        int(match.group("build")),
        stage_rank,
        int(match.group("stage_number") or 0),
    )


def parse_fabric_minecraft_range(value: str) -> FabricMinecraftRange:
    """Accept only the approved closed Fabric 26.1.x predicate."""
    if not isinstance(value, str):
        raise CompatibilityRangeError("Fabric Minecraft range must be a string")
    match = _FABRIC_RANGE.fullmatch(value)
    if match is None:
        raise CompatibilityRangeError("Fabric Minecraft range must be exactly '>=lower <=upper'")
    if value != APPROVED_FABRIC_MINECRAFT_RANGE:
        raise CompatibilityRangeError("Fabric Minecraft range is not the approved 26.1.x frozen-candidate range")
    result = FabricMinecraftRange(
        parse_minecraft_version(match.group("lower")),
        parse_minecraft_version(match.group("upper")),
    )
    if result.lower > result.upper:
        raise CompatibilityRangeError("Fabric Minecraft range lower bound exceeds upper bound")
    return result


def parse_neoforge_minecraft_range(value: str) -> NeoForgeRange:
    """Accept only the approved closed NeoForge Minecraft range."""
    lower, upper = _parse_closed_neoforge_range(value)
    if value != APPROVED_NEOFORGE_MINECRAFT_RANGE:
        raise CompatibilityRangeError("NeoForge Minecraft range is not the approved 26.1.x frozen-candidate range")
    result = NeoForgeRange(parse_minecraft_version(lower), parse_minecraft_version(upper))
    if result.lower > result.upper:
        raise CompatibilityRangeError("NeoForge Minecraft range lower bound exceeds upper bound")
    return result


def parse_neoforge_loader_range(value: str) -> NeoForgeRange:
    """Accept only the approved closed NeoForge loader range."""
    lower, upper = _parse_closed_neoforge_range(value)
    if value != APPROVED_NEOFORGE_LOADER_RANGE:
        raise CompatibilityRangeError("NeoForge loader range is not the approved 26.1.x frozen-candidate range")
    result = NeoForgeRange(parse_neoforge_version(lower), parse_neoforge_version(upper))
    if result.lower > result.upper:
        raise CompatibilityRangeError("NeoForge loader range lower bound exceeds upper bound")
    return result


def _parse_closed_neoforge_range(value: str) -> tuple[str, str]:
    if not isinstance(value, str):
        raise CompatibilityRangeError("NeoForge range must be a string")
    match = _NEOFORGE_RANGE.fullmatch(value)
    if match is None:
        raise CompatibilityRangeError("NeoForge range must be exactly '[lower,upper]'")
    return match.group("lower"), match.group("upper")


def manifest_targets_covered(
    cells: Sequence[Mapping[str, Any]],
    *,
    fabric_minecraft_range: str,
    neoforge_minecraft_range: str,
    neoforge_loader_range: str,
) -> ManifestRangeCoverage:
    """Fail unless every selected manifest target is inside declared ranges.

    This accepts only manifest-like cell dictionaries.  It does not inspect a
    jar, mutate a manifest, or claim that the candidate has passed any gate.
    """
    fabric_range = parse_fabric_minecraft_range(fabric_minecraft_range)
    neo_minecraft_range = parse_neoforge_minecraft_range(neoforge_minecraft_range)
    neo_loader_range = parse_neoforge_loader_range(neoforge_loader_range)
    fabric_cells: list[str] = []
    neo_minecraft_cells: list[str] = []
    neo_loader_cells: list[str] = []

    for cell in cells:
        if not isinstance(cell, Mapping):
            raise CompatibilityRangeError("manifest cell must be an object")
        cell_id = cell.get("id")
        loader = cell.get("loader")
        minecraft = cell.get("minecraft")
        if not isinstance(cell_id, str) or not cell_id:
            raise CompatibilityRangeError("manifest cell must have an id")
        if loader not in {"fabric", "neoforge"}:
            raise CompatibilityRangeError(f"{cell_id}: loader must be fabric or neoforge")
        if not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str):
            raise CompatibilityRangeError(f"{cell_id}: Minecraft target is missing")
        minecraft_version = parse_minecraft_version(minecraft["version"])

        if loader == "fabric":
            if not fabric_range.contains(minecraft_version):
                raise CompatibilityRangeError(f"{cell_id}: Minecraft {minecraft['version']} is outside the Fabric range")
            fabric_cells.append(cell_id)
            continue

        if not neo_minecraft_range.contains(minecraft_version):
            raise CompatibilityRangeError(f"{cell_id}: Minecraft {minecraft['version']} is outside the NeoForge range")
        neo_minecraft_cells.append(cell_id)
        dependencies = cell.get("dependencies")
        if not isinstance(dependencies, Sequence) or isinstance(dependencies, (str, bytes)):
            raise CompatibilityRangeError(f"{cell_id}: dependencies are missing")
        versions = [
            dependency.get("version")
            for dependency in dependencies
            if isinstance(dependency, Mapping)
            and dependency.get("coordinate") == "net.neoforged:neoforge"
        ]
        if len(versions) != 1 or not isinstance(versions[0], str):
            raise CompatibilityRangeError(f"{cell_id}: must pin exactly one NeoForge dependency")
        if not neo_loader_range.contains(parse_neoforge_version(versions[0])):
            raise CompatibilityRangeError(f"{cell_id}: NeoForge {versions[0]} is outside the loader range")
        neo_loader_cells.append(cell_id)

    return ManifestRangeCoverage(tuple(fabric_cells), tuple(neo_minecraft_cells), tuple(neo_loader_cells))
