#!/usr/bin/env python3
"""Pure, fail-closed evidence contract for the nightly worldgen matrix.

The external runner is responsible for reading settings and logs, retaining
their hashes, and creating the named evidence.  This module has deliberately
no filesystem, process, Gradle, or network operations.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
from typing import Any, Mapping

from minecraft_atlas_recovery_qualification import PersistedRingSettingsObservation
from minecraft_qualification_model import InvocationError
from run_worldgen_structure_matrix import REQUIRED_MAJOR_FAMILIES, validate_aggregate, validate_reload


SHA256 = re.compile(r"^[0-9a-f]{64}$")
FIXTURE_NAME = "02-worldgen-seam-structures"
SETTINGS_FORMAT_VERSION = 3
TERRAIN_NOISE_MAPPING = 4
WALL_HEIGHT_BLOCKS = 160
# ``crossingStarts`` in the parsed matrix record, not a separate log line,
# proves seam coverage.  These are the only two literal terminal facts emitted
# by ``RingWorldStrongholdTest`` for a complete matrix run.
REQUIRED_MARKERS = ("worldgen-matrix-record", "stronghold-test-pass")


@dataclass(frozen=True)
class QualificationIdentity:
    cell_id: str
    loader: str
    minecraft_version: str
    frozen_candidate_sha256: str
    quick_terminal_evidence_sha256: str


@dataclass(frozen=True)
class TimedMarker:
    name: str
    timestamp_millis: int


@dataclass(frozen=True)
class WorldgenLogFact:
    """One independently parsed ``[worldgen-matrix]`` result and its log."""

    stage: str
    resume: bool
    seed: str
    numeric_seed: int
    circumference_blocks: int
    width_blocks: int
    terrain_noise_mapping: int
    settings_format_version: int
    record: Mapping[str, object]
    runtime_log_path: Path
    captured_log_path: Path
    runtime_log_sha256: str
    captured_log_sha256: str
    markers: tuple[TimedMarker, ...]
    exit_code: int


@dataclass(frozen=True)
class WorldgenStageEvidence:
    runtime_root: Path
    world_root: Path
    settings: PersistedRingSettingsObservation
    log: WorldgenLogFact


@dataclass(frozen=True)
class WorldgenQualificationEvidence:
    fixture_root: Path
    evidence_root: Path
    logs_root: Path
    stages: tuple[WorldgenStageEvidence, WorldgenStageEvidence,
                  WorldgenStageEvidence, WorldgenStageEvidence]


@dataclass(frozen=True)
class WorldgenQualification:
    identity: QualificationIdentity
    evidence: WorldgenQualificationEvidence

    def as_dict(self) -> dict[str, Any]:
        return {
            "fixture": "worldgen-seam-structures",
            "cell": self.identity.cell_id,
            "loader": self.identity.loader,
            "minecraftVersion": self.identity.minecraft_version,
            "stages": [stage.log.stage for stage in self.evidence.stages],
            "families": sorted({family for stage in self.evidence.stages
                                for family in stage.log.record["families"]}),
        }


_EXPECTED = (
    ("production-fresh", False, "ringworld-regression-1", 16_384, 256),
    ("production-resume", True, "ringworld-regression-1", 16_384, 256),
    ("seam-crossing", False, "ringworld-matrix-0", 2_048, 416),
    ("terminal-policy", False, "ringworld-matrix-3", 2_048, 416),
)


def _path(value: object, label: str) -> Path:
    if not isinstance(value, Path) or not value.is_absolute() or ".." in value.parts:
        raise InvocationError(f"{label} must be an absolute non-traversing Path")
    return value


def _within(child: Path, parent: Path, label: str) -> None:
    try:
        child.relative_to(parent)
    except ValueError as error:
        raise InvocationError(f"{label} escapes its permitted root") from error


def _sha(value: object, label: str) -> str:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        raise InvocationError(f"{label} must be a lowercase SHA-256")
    return value


def _identity(cell: Mapping[str, Any], identity: QualificationIdentity) -> None:
    minecraft = cell.get("minecraft") if isinstance(cell, Mapping) else None
    version = minecraft.get("version") if isinstance(minecraft, Mapping) else cell.get("minecraft_version")
    expected = (cell.get("id"), cell.get("loader"), version)
    actual = (identity.cell_id, identity.loader, identity.minecraft_version)
    if actual != expected or identity.loader not in {"fabric", "neoforge"} or not all(isinstance(value, str) and value for value in actual):
        raise InvocationError("worldgen identity does not exactly match the canonical cell")
    _sha(identity.frozen_candidate_sha256, "frozen candidate hash")
    _sha(identity.quick_terminal_evidence_sha256, "quick terminal evidence hash")


def _settings(value: object, world: Path, expected: tuple[str, bool, str, int, int]) -> PersistedRingSettingsObservation:
    if not isinstance(value, PersistedRingSettingsObservation):
        raise InvocationError("worldgen stage needs independently decoded settings")
    _, _, _, circumference, width = expected
    if (value.circumference_blocks, value.width_blocks, value.wall_height_blocks,
            value.surface_reference_y, value.terrain_noise_mapping, value.format_version) != (
            circumference, width, WALL_HEIGHT_BLOCKS, 64, TERRAIN_NOISE_MAPPING, SETTINGS_FORMAT_VERSION):
        raise InvocationError("worldgen settings do not match the fixed stage geometry/mapping")
    if not isinstance(value.generator_seed, int) or isinstance(value.generator_seed, bool) or not -(1 << 63) <= value.generator_seed < (1 << 63):
        raise InvocationError("worldgen settings seed must be a signed 64-bit integer")
    path = _path(value.settings_path, "worldgen settings path")
    if path != world / "dimensions/minecraft/overworld/data/ringworld/settings.dat":
        raise InvocationError("worldgen settings are not dimension-owned")
    _sha(value.settings_sha256, "worldgen settings hash")
    return value


def _markers(markers: object, stage: str) -> None:
    if not isinstance(markers, tuple) or len(markers) != len(REQUIRED_MARKERS):
        raise InvocationError(f"{stage} must retain every ordered worldgen marker")
    previous = -1
    names: list[str] = []
    for marker in markers:
        if not isinstance(marker, TimedMarker) or not isinstance(marker.name, str) or not isinstance(marker.timestamp_millis, int) or isinstance(marker.timestamp_millis, bool) or marker.timestamp_millis <= previous:
            raise InvocationError(f"{stage} markers must be strictly ordered timestamps")
        names.append(marker.name)
        previous = marker.timestamp_millis
    if tuple(names) != REQUIRED_MARKERS:
        raise InvocationError(f"{stage} has wrong ordered worldgen markers")


def _log(value: object, stage: WorldgenStageEvidence, expected: tuple[str, bool, str, int, int],
         evidence_root: Path, logs_root: Path) -> Mapping[str, object]:
    if not isinstance(value, WorldgenLogFact):
        raise InvocationError("worldgen stage needs a parsed WorldgenLogFact")
    name, resume, seed, circumference, width = expected
    if (value.stage, value.resume, value.seed, value.circumference_blocks, value.width_blocks,
            value.terrain_noise_mapping, value.settings_format_version) != (
            name, resume, seed, circumference, width, TERRAIN_NOISE_MAPPING, SETTINGS_FORMAT_VERSION):
        raise InvocationError(f"{name} log does not match its immutable stage contract")
    if value.exit_code != 0 or not isinstance(value.numeric_seed, int) or isinstance(value.numeric_seed, bool):
        raise InvocationError(f"{name} did not finish cleanly")
    if value.numeric_seed != stage.settings.generator_seed:
        raise InvocationError(f"{name} log seed does not match persisted settings")
    required = {"numeric_seed", "circumference", "width", "families", "biomes", "chunks", "cave_air", "ores", "logs", "starts", "structures", "crossing_starts", "crossing_structures", "references", "loot", "monument_status", "monument_reason", "monument_candidate", "monument_spawn_override_entries", "spawn_override_structures", "spawn_override_ids"}
    if not isinstance(value.record, Mapping) or not required.issubset(value.record):
        raise InvocationError(f"{name} lacks complete parsed worldgen-matrix facts")
    if (value.record["numeric_seed"], value.record["circumference"], value.record["width"]) != (value.numeric_seed, circumference, width):
        raise InvocationError(f"{name} parsed record disagrees with stage identity")
    for path, root, label in ((value.runtime_log_path, logs_root, "runtime log"), (value.captured_log_path, evidence_root, "captured log")):
        _within(_path(path, name + " " + label), root, name + " " + label)
    _sha(value.runtime_log_sha256, name + " runtime log hash")
    _sha(value.captured_log_sha256, name + " captured log hash")
    _markers(value.markers, name)
    return dict(value.record)


def validate_worldgen_qualification(canonical_cell: Mapping[str, Any], identity: QualificationIdentity, evidence: WorldgenQualificationEvidence) -> WorldgenQualification:
    """Validate exactly four worldgen stages, with production save/reload reuse."""
    if not isinstance(identity, QualificationIdentity) or not isinstance(evidence, WorldgenQualificationEvidence):
        raise InvocationError("worldgen qualification needs structural identity and four stages")
    _identity(canonical_cell, identity)
    fixture = _path(evidence.fixture_root, "worldgen fixture root")
    evidence_root = _path(evidence.evidence_root, "worldgen evidence root")
    logs_root = _path(evidence.logs_root, "worldgen logs root")
    if len(evidence.stages) != 4:
        raise InvocationError("worldgen qualification requires exactly four stages")
    records: list[dict[str, object]] = []
    for stage, expected in zip(evidence.stages, _EXPECTED):
        if not isinstance(stage, WorldgenStageEvidence):
            raise InvocationError("worldgen stage has the wrong shape")
        runtime, world = _path(stage.runtime_root, expected[0] + " runtime"), _path(stage.world_root, expected[0] + " world")
        _within(runtime, fixture, expected[0] + " runtime")
        if world != runtime / "world":
            raise InvocationError(f"{expected[0]} must use runtime/world")
        settings = _settings(stage.settings, world, expected)
        records.append(_log(stage.log, stage, expected, evidence_root, logs_root))
    first, resumed, seam, terminal = evidence.stages
    if first.runtime_root != resumed.runtime_root or first.world_root != resumed.world_root:
        raise InvocationError("production fresh and resume must use one world")
    if any(other.world_root == first.world_root for other in (seam, terminal)) or seam.world_root == terminal.world_root:
        raise InvocationError("only production fresh/resume may share a world")
    stable_settings = lambda value: (
        value.width_blocks, value.circumference_blocks, value.generator_seed,
        value.wall_height_blocks, value.surface_reference_y,
        value.terrain_noise_mapping, value.format_version, value.settings_path,
    )
    if stable_settings(first.settings) != stable_settings(resumed.settings):
        raise InvocationError("production reload must retain stable persisted settings in the same world")
    try:
        validate_reload(records[0], records[1])
        validate_aggregate(records)
    except (KeyError, TypeError, ValueError) as error:
        raise InvocationError(f"worldgen aggregate/reload contract failed: {error}") from error
    families = {family for record in records for family in record["families"]}
    if not REQUIRED_MAJOR_FAMILIES.issubset(families):
        raise InvocationError("worldgen aggregate missed a required biome family")
    return WorldgenQualification(identity, evidence)


validate_worldgen_seam_structures = validate_worldgen_qualification
