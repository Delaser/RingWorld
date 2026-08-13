#!/usr/bin/env python3
"""Pure, fail-closed contract for one forward RingWorld save upgrade.

The executor owns filesystem/process work.  This module only checks evidence
shapes and the semantic equality of the copied source and target result.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
from typing import Any, Mapping

from minecraft_atlas_recovery_qualification import PersistedRingSettingsObservation
from minecraft_qualification_model import InvocationError
from run_worldgen_structure_matrix import validate_reload


SHA256 = re.compile(r"^[0-9a-f]{64}$")
FIXTURE_NAME = "05-world-upgrade"
SUPPORTED_FORWARD_PATHS = frozenset({
    ("26.1", "26.1.1"), ("26.1", "26.1.2"), ("26.1.1", "26.1.2"),
})


@dataclass(frozen=True)
class ForwardUpgradeIdentity:
    source_cell_id: str
    target_cell_id: str
    loader: str
    source_minecraft_version: str
    target_minecraft_version: str
    frozen_candidate_sha256: str
    source_worldgen_terminal_sha256: str
    target_quick_terminal_sha256: str


@dataclass(frozen=True)
class ForwardUpgradeEvidence:
    fixture_root: Path
    evidence_root: Path
    logs_root: Path
    source_world_root: Path
    target_world_root: Path
    source_settings: PersistedRingSettingsObservation
    target_settings: PersistedRingSettingsObservation
    source_record: Mapping[str, object]
    target_record: Mapping[str, object]


@dataclass(frozen=True)
class ForwardUpgradeQualification:
    identity: ForwardUpgradeIdentity
    evidence: ForwardUpgradeEvidence

    def as_dict(self) -> dict[str, Any]:
        return {
            "fixture": FIXTURE_NAME,
            "sourceCell": self.identity.source_cell_id,
            "targetCell": self.identity.target_cell_id,
            "loader": self.identity.loader,
            "sourceMinecraft": self.identity.source_minecraft_version,
            "targetMinecraft": self.identity.target_minecraft_version,
            "sourceSettingsSha256": self.evidence.source_settings.settings_sha256,
            "targetSettingsSha256": self.evidence.target_settings.settings_sha256,
        }


def _path(value: object, label: str) -> Path:
    if not isinstance(value, Path) or not value.is_absolute() or ".." in value.parts:
        raise InvocationError(f"{label} must be an absolute non-traversing Path")
    return value


def _within(child: Path, parent: Path, label: str) -> None:
    try:
        child.relative_to(parent)
    except ValueError as error:
        raise InvocationError(f"{label} escapes its permitted root") from error


def _sha(value: object, label: str) -> None:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        raise InvocationError(f"{label} must be a lowercase SHA-256")


def _cell(cell: Mapping[str, Any], label: str) -> tuple[str, str, str]:
    minecraft = cell.get("minecraft") if isinstance(cell, Mapping) else None
    version = minecraft.get("version") if isinstance(minecraft, Mapping) else cell.get("minecraft_version")
    result = (cell.get("id"), cell.get("loader"), version)
    if not all(isinstance(value, str) and value for value in result) or result[1] not in {"fabric", "neoforge"}:
        raise InvocationError(f"{label} must be one canonical loader cell")
    return result  # type: ignore[return-value]


def _settings(value: object, world: Path, label: str) -> tuple[object, ...]:
    if not isinstance(value, PersistedRingSettingsObservation):
        raise InvocationError(f"{label} needs independently parsed RingWorld settings")
    if value.settings_path != world / "dimensions/minecraft/overworld/data/ringworld/settings.dat":
        raise InvocationError(f"{label} settings are not dimension-owned")
    _sha(value.settings_sha256, label + " settings hash")
    return (
        value.width_blocks, value.circumference_blocks, value.generator_seed,
        value.wall_height_blocks, value.surface_reference_y,
        value.terrain_noise_mapping, value.format_version,
    )


def validate_forward_world_upgrade(
    source_cell: Mapping[str, Any], target_cell: Mapping[str, Any],
    identity: ForwardUpgradeIdentity, evidence: ForwardUpgradeEvidence,
) -> ForwardUpgradeQualification:
    """Validate one supported copied-world forward path, never a downgrade."""
    if not isinstance(identity, ForwardUpgradeIdentity) or not isinstance(evidence, ForwardUpgradeEvidence):
        raise InvocationError("forward upgrade requires structural identity and evidence")
    source_id, source_loader, source_version = _cell(source_cell, "source cell")
    target_id, target_loader, target_version = _cell(target_cell, "target cell")
    if source_loader != target_loader or (source_version, target_version) not in SUPPORTED_FORWARD_PATHS:
        raise InvocationError("upgrade direction is not a supported forward path")
    if (identity.source_cell_id, identity.target_cell_id, identity.loader,
            identity.source_minecraft_version, identity.target_minecraft_version) != (
            source_id, target_id, source_loader, source_version, target_version):
        raise InvocationError("upgrade identity does not match its canonical cells")
    for value, label in (
        (identity.frozen_candidate_sha256, "frozen candidate hash"),
        (identity.source_worldgen_terminal_sha256, "source worldgen terminal hash"),
        (identity.target_quick_terminal_sha256, "target quick terminal hash"),
    ):
        _sha(value, label)
    fixture, _evidence_root, _logs = (
        _path(evidence.fixture_root, "upgrade fixture root"),
        _path(evidence.evidence_root, "upgrade evidence root"),
        _path(evidence.logs_root, "upgrade logs root"),
    )
    target_world, source_world = (
        _path(evidence.target_world_root, "target copied world"),
        _path(evidence.source_world_root, "source world"),
    )
    _within(target_world, fixture, "target copied world")
    if target_world.name != "world":
        raise InvocationError("target upgrade world must retain the normal runtime/world ownership")
    source_facts = _settings(evidence.source_settings, source_world, "source")
    target_facts = _settings(evidence.target_settings, target_world, "target")
    if source_facts != target_facts:
        raise InvocationError("forward upgrade changed persisted RingWorld settings")
    try:
        validate_reload(dict(evidence.source_record), dict(evidence.target_record))
    except (KeyError, TypeError, ValueError) as error:
        raise InvocationError(f"forward upgrade worldgen/stronghold resume drift: {error}") from error
    return ForwardUpgradeQualification(identity, evidence)
