"""Pure manifest-derived identities for one same-jar qualification group.

Each manifest is a reviewed candidate group. Adding a stable line requires
data, not new version constants; declaring a range is never runtime proof.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Sequence

from minecraft_qualification_ranges import parse_minecraft_version, parse_neoforge_version


@dataclass(frozen=True)
class SupportContract:
    group: str
    versions: tuple[str, ...]
    neoforge_versions: tuple[str, ...]

    @property
    def oldest(self) -> str:
        return self.versions[0]

    @property
    def artifact_version(self) -> str:
        return f"0.0.0-qualification+mc{self.oldest}"

    def release_label(self, loader: str) -> str:
        return f"qualification-{self.oldest}-{loader}"

    def cell_ids(self, loader: str) -> tuple[str, ...]:
        return tuple(f"{version}-{loader}" for version in self.versions)

    def minecraft_range(self, loader: str) -> str:
        if loader == "fabric":
            return f">={self.oldest} <={self.versions[-1]}"
        if loader == "neoforge":
            return f"[{self.oldest},{self.versions[-1]}]"
        raise ValueError("unsupported qualification loader")

    @property
    def neoforge_range(self) -> str:
        return f"[{self.neoforge_versions[0]},{self.neoforge_versions[-1]}]"

    def range_identities(self) -> dict[str, dict[str, str]]:
        return {loader: {
            "oldest_abi_minecraft_version": self.oldest,
            "minecraft_range": self.minecraft_range(loader),
            "loader_range": "" if loader == "fabric" else self.neoforge_range,
        } for loader in ("fabric", "neoforge")}


# Backwards-compatible default for callers consuming historical 26.1.x evidence.
# Operator CLIs always derive their contract from the selected manifest.
LEGACY_CONTRACT = SupportContract("26.1.x", ("26.1", "26.1.1", "26.1.2"),
                                  ("26.1.0.19-beta", "26.1.1.15-beta", "26.1.2.87"))


def contract_from_manifest(manifest: Mapping[str, Any]) -> SupportContract:
    group, cells = manifest.get("line"), manifest.get("cells")
    if not isinstance(group, str) or not group or not all(c.isalnum() or c in ".-_" for c in group):
        raise ValueError("qualification manifest needs a safe candidate-group line")
    if not isinstance(cells, Sequence) or isinstance(cells, (str, bytes)) or not cells:
        raise ValueError("qualification manifest has no cells")
    versions: dict[str, set[str]] = {"fabric": set(), "neoforge": set()}
    neo_versions: set[str] = set()
    for cell in cells:
        if not isinstance(cell, Mapping) or cell.get("loader") not in versions:
            raise ValueError("qualification cell has an unsupported loader")
        loader = cell["loader"]
        minecraft = cell.get("minecraft")
        version = minecraft.get("version") if isinstance(minecraft, Mapping) else None
        parse_minecraft_version(version)
        if version in versions[loader] or cell.get("id") != f"{version}-{loader}":
            raise ValueError("qualification group has duplicate or noncanonical cells")
        versions[loader].add(version)
        if loader == "neoforge":
            deps = cell.get("dependencies", ())
            matches = [d.get("version") for d in deps if isinstance(d, Mapping)
                       and d.get("coordinate") == "net.neoforged:neoforge"]
            if len(matches) != 1:
                raise ValueError("NeoForge cell needs exactly one pinned runtime dependency")
            neo = parse_neoforge_version(matches[0])
            game = parse_minecraft_version(version)
            if (neo.major, neo.minor, neo.patch) != (game.major, game.minor, game.patch):
                raise ValueError("NeoForge runtime version does not match its Minecraft cell")
            neo_versions.add(matches[0])
    if not versions["fabric"] or versions["fabric"] != versions["neoforge"]:
        raise ValueError("candidate group must contain matching Fabric and NeoForge versions")
    ordered = tuple(sorted(versions["fabric"], key=parse_minecraft_version))
    if len({parse_minecraft_version(v) for v in ordered}) != len(ordered):
        raise ValueError("candidate group contains equivalent version spellings")
    parsed = tuple(parse_minecraft_version(v) for v in ordered)
    if len({(v.major, v.minor) for v in parsed}) != 1:
        raise ValueError("use a separate candidate manifest for each stable Minecraft line")
    if [v.patch for v in parsed] != list(range(parsed[0].patch, parsed[-1].patch + 1)):
        raise ValueError("candidate range may not include untested gaps between patch versions")
    return SupportContract(group, ordered, tuple(sorted(neo_versions, key=parse_neoforge_version)))
