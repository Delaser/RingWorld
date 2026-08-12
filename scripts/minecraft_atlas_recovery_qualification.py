#!/usr/bin/env python3
"""Pure, fail-closed evidence contract for nightly Atlas recovery.

This module deliberately does no I/O, process, network, Gradle, or Minecraft
work.  A later external-server adapter must parse the raw schema-2 reports,
independently inspect the saved settings and Atlas file, checksum all immutable
inputs, and exclusive-create captured evidence before it calls this validator.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
from typing import Any, Mapping, Optional

from minecraft_qualification_model import InvocationError


SHA256 = re.compile(r"^[0-9a-f]{64}$")
DECIMAL_ID = re.compile(r"^(0|[1-9][0-9]*)$")
SAFE_MARKER = re.compile(r"^[a-z][a-z0-9-]{0,95}$")

ATLAS_REPORT_SCHEMA = 2
TERRAIN_NOISE_MAPPING = 4
CIRCUMFERENCE_BLOCKS = 2_048
WIDTH_BLOCKS = 416
WALL_HEIGHT_BLOCKS = 160
ATLAS_SAMPLE_STEP_BLOCKS = 8
EXPECTED_TOTAL_CHUNKS = (CIRCUMFERENCE_BLOCKS // 16) * (WIDTH_BLOCKS // 16)
EXPECTED_TOTAL_CELLS = (CIRCUMFERENCE_BLOCKS // ATLAS_SAMPLE_STEP_BLOCKS) * (WIDTH_BLOCKS // ATLAS_SAMPLE_STEP_BLOCKS)
INTERRUPTED_MARKERS = ("atlas-started", "atlas-interrupted")
RECOVERY_MARKERS = ("atlas-restarted", "atlas-recovered", "atlas-complete", "fixture-pass")


@dataclass(frozen=True)
class QualificationIdentity:
    cell_id: str
    loader: str
    minecraft_version: str
    frozen_candidate_sha256: str
    quick_terminal_evidence_sha256: str


@dataclass(frozen=True)
class PersistedRingSettingsObservation:
    """Independently observed saved settings; wall height is not in the report."""

    world_hash: str
    layout_fingerprint: str
    terrain_noise_mapping: int
    circumference_blocks: int
    width_blocks: int
    wall_height_blocks: int
    settings_path: Path
    settings_sha256: str


@dataclass(frozen=True)
class AtlasCacheObservation:
    """Independently inspected Atlas header/file identity for one stage."""

    world_hash: str
    layout_fingerprint: str
    terrain_noise_mapping: int
    circumference_blocks: int
    width_blocks: int
    atlas_path: Path
    atlas_sha256: str


@dataclass(frozen=True)
class AtlasReportFact:
    """Schema-2 report fields plus immutable source/capture provenance."""

    schema_version: int
    status: str
    identity_available: bool
    world_hash: str
    layout_fingerprint: str
    terrain_noise_mapping: int
    circumference_blocks: int
    width_blocks: int
    completed_chunks: int
    total_chunks: int
    completed_cells: int
    total_cells: int
    elapsed_millis: int
    atlas_path: Path
    runtime_report_path: Path
    captured_report_path: Path
    failure_reason: Optional[str]


@dataclass(frozen=True)
class TimedMarker:
    name: str
    timestamp_millis: int


@dataclass(frozen=True)
class MarkerLedger:
    stage: str
    path: Path
    events: tuple[TimedMarker, ...]


@dataclass(frozen=True)
class AtlasRecoveryEvidence:
    """Two clean server exits over exactly one newly-created runtime/world."""

    runtime_root: Path
    world_root: Path
    evidence_root: Path
    settings: PersistedRingSettingsObservation
    interrupted_report: AtlasReportFact
    recovered_report: AtlasReportFact
    interrupted_atlas: AtlasCacheObservation
    recovered_atlas: AtlasCacheObservation
    interrupted_ledger: MarkerLedger
    recovery_ledger: MarkerLedger
    interrupted_exit_code: int
    recovery_exit_code: int


@dataclass(frozen=True)
class AtlasRecoveryQualification:
    identity: QualificationIdentity
    evidence: AtlasRecoveryEvidence

    def as_dict(self) -> dict[str, Any]:
        return {
            "fixture": "atlas-prewarm-recovery",
            "cell": self.identity.cell_id,
            "loader": self.identity.loader,
            "minecraftVersion": self.identity.minecraft_version,
            "frozenCandidateSha256": self.identity.frozen_candidate_sha256,
            "quickTerminalEvidenceSha256": self.identity.quick_terminal_evidence_sha256,
            "settingsSha256": self.evidence.settings.settings_sha256,
            "interruptedReport": str(self.evidence.interrupted_report.captured_report_path),
            "recoveredReport": str(self.evidence.recovered_report.captured_report_path),
            "worldHash": self.evidence.settings.world_hash,
            "layoutFingerprint": self.evidence.settings.layout_fingerprint,
            "totalChunks": self.evidence.recovered_report.total_chunks,
            "totalCells": self.evidence.recovered_report.total_cells,
        }


def _sha256(value: object, label: str) -> str:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        raise InvocationError(f"{label} must be a lowercase SHA-256")
    return value


def _path(value: object, label: str) -> Path:
    if not isinstance(value, Path) or not value.is_absolute() or ".." in value.parts:
        raise InvocationError(f"{label} must be an absolute non-traversing Path")
    return value


def _contained(child: Path, parent: Path, label: str) -> None:
    try:
        child.relative_to(parent)
    except ValueError as error:
        raise InvocationError(f"{label} escapes its permitted root") from error


def _count(value: object, label: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise InvocationError(f"{label} must be a non-negative integer")
    return value


def _unsigned(value: object, label: str) -> str:
    if not isinstance(value, str) or DECIMAL_ID.fullmatch(value) is None:
        raise InvocationError(f"{label} must be an unsigned decimal identifier")
    return value


def _fixed_identity(world_hash: object, layout: object, mapping: object, circumference: object, width: object, label: str) -> tuple[str, str, int, int, int]:
    identity = (_unsigned(world_hash, label + " world hash"), _unsigned(layout, label + " layout fingerprint"), mapping, circumference, width)
    if identity[2:] != (TERRAIN_NOISE_MAPPING, CIRCUMFERENCE_BLOCKS, WIDTH_BLOCKS):
        raise InvocationError(f"{label} must use mapping 4 and the 2048x416 safe-small geometry")
    return identity  # type: ignore[return-value]


def _canonical_cell(cell: Mapping[str, Any], identity: QualificationIdentity) -> None:
    if not isinstance(cell, Mapping) or not isinstance(cell.get("minecraft"), Mapping):
        raise InvocationError("Atlas recovery needs a canonical manifest cell")
    expected = (cell.get("id"), cell.get("loader"), cell["minecraft"].get("version"))
    actual = (identity.cell_id, identity.loader, identity.minecraft_version)
    if actual != expected or identity.loader not in {"fabric", "neoforge"} or not all(isinstance(value, str) and value for value in actual):
        raise InvocationError("qualification identity does not exactly match the canonical cell")
    _sha256(identity.frozen_candidate_sha256, "frozen candidate hash")
    _sha256(identity.quick_terminal_evidence_sha256, "quick terminal evidence hash")


def _settings(value: object, world_root: Path) -> PersistedRingSettingsObservation:
    if not isinstance(value, PersistedRingSettingsObservation):
        raise InvocationError("Atlas recovery requires independently observed saved settings")
    _fixed_identity(value.world_hash, value.layout_fingerprint, value.terrain_noise_mapping, value.circumference_blocks, value.width_blocks, "saved settings")
    if value.wall_height_blocks != WALL_HEIGHT_BLOCKS:
        raise InvocationError("saved settings must use wall height 160")
    path = _path(value.settings_path, "saved settings path")
    _contained(path, world_root, "saved settings path")
    _sha256(value.settings_sha256, "saved settings hash")
    return value


def _report(value: object, status: str, runtime_root: Path, world_root: Path, evidence_root: Path, settings: PersistedRingSettingsObservation, label: str) -> AtlasReportFact:
    if not isinstance(value, AtlasReportFact):
        raise InvocationError(f"{label} must be an AtlasReportFact")
    if value.schema_version != ATLAS_REPORT_SCHEMA or value.status != status or not value.identity_available:
        raise InvocationError(f"{label} must be an identity-available schema-2 {status} report")
    if _fixed_identity(value.world_hash, value.layout_fingerprint, value.terrain_noise_mapping, value.circumference_blocks, value.width_blocks, label) != _fixed_identity(settings.world_hash, settings.layout_fingerprint, settings.terrain_noise_mapping, settings.circumference_blocks, settings.width_blocks, "saved settings"):
        raise InvocationError(f"{label} does not match persisted settings")
    chunks, total_chunks = _count(value.completed_chunks, label + " completed chunks"), _count(value.total_chunks, label + " total chunks")
    cells, total_cells = _count(value.completed_cells, label + " completed cells"), _count(value.total_cells, label + " total cells")
    _count(value.elapsed_millis, label + " elapsed milliseconds")
    if (total_chunks, total_cells) != (EXPECTED_TOTAL_CHUNKS, EXPECTED_TOTAL_CELLS) or chunks > total_chunks or cells > total_cells:
        raise InvocationError(f"{label} has invalid fixed-geometry totals")
    for path, root, path_label in ((value.atlas_path, world_root, "atlas path"), (value.runtime_report_path, world_root, "runtime report path"), (value.captured_report_path, evidence_root, "captured report path")):
        checked = _path(path, label + " " + path_label)
        _contained(checked, root, label + " " + path_label)
    if value.runtime_report_path != world_root / "ringworld-prewarm" / "result.json":
        raise InvocationError(f"{label} must retain the headless prewarm result.json location")
    if world_root != runtime_root / "world":
        raise InvocationError(f"{label} world root must be fixture runtime/world")
    has_failure = isinstance(value.failure_reason, str) and bool(value.failure_reason.strip())
    if value.failure_reason is not None and not isinstance(value.failure_reason, str):
        raise InvocationError(f"{label} has an invalid terminal failure reason")
    if has_failure is not (status == "INTERRUPTED"):
        raise InvocationError(f"{label} has an invalid terminal failure-reason state")
    return value


def _atlas(value: object, report: AtlasReportFact, world_root: Path, label: str) -> AtlasCacheObservation:
    if not isinstance(value, AtlasCacheObservation):
        raise InvocationError(f"{label} needs an independently inspected Atlas observation")
    observed = _fixed_identity(value.world_hash, value.layout_fingerprint, value.terrain_noise_mapping, value.circumference_blocks, value.width_blocks, label + " Atlas")
    reported = _fixed_identity(report.world_hash, report.layout_fingerprint, report.terrain_noise_mapping, report.circumference_blocks, report.width_blocks, label + " report")
    if observed != reported:
        raise InvocationError(f"{label} Atlas identity does not match its report")
    path = _path(value.atlas_path, label + " Atlas path")
    _contained(path, world_root, label + " Atlas path")
    if path != report.atlas_path:
        raise InvocationError(f"{label} Atlas path does not match its report")
    _sha256(value.atlas_sha256, label + " Atlas hash")
    return value


def _ledger(value: object, stage: str, markers: tuple[str, ...], evidence_root: Path) -> MarkerLedger:
    if not isinstance(value, MarkerLedger) or value.stage != stage:
        raise InvocationError(f"{stage} marker ledger has the wrong stage")
    path = _path(value.path, stage + " marker ledger path")
    _contained(path, evidence_root, stage + " marker ledger path")
    if not isinstance(value.events, tuple) or len(value.events) != len(markers):
        raise InvocationError(f"{stage} marker ledger must contain exact expected events")
    previous, names = -1, []
    for event in value.events:
        if not isinstance(event, TimedMarker) or not isinstance(event.name, str) or SAFE_MARKER.fullmatch(event.name) is None or not isinstance(event.timestamp_millis, int) or isinstance(event.timestamp_millis, bool) or event.timestamp_millis < 0 or event.timestamp_millis <= previous:
            raise InvocationError(f"{stage} marker ledger is not strictly ordered and timestamped")
        names.append(event.name)
        previous = event.timestamp_millis
    if tuple(names) != markers:
        raise InvocationError(f"{stage} marker ledger has the wrong ordered markers")
    return value


def validate_atlas_recovery_qualification(canonical_cell: Mapping[str, Any], identity: QualificationIdentity, evidence: AtlasRecoveryEvidence) -> AtlasRecoveryQualification:
    """Validate a partial clean stop then complete clean restart of one world."""
    if not isinstance(identity, QualificationIdentity) or not isinstance(evidence, AtlasRecoveryEvidence):
        raise InvocationError("Atlas recovery needs structural identity and two-stage evidence")
    _canonical_cell(canonical_cell, identity)
    runtime, world, evidence_root = _path(evidence.runtime_root, "runtime root"), _path(evidence.world_root, "world root"), _path(evidence.evidence_root, "evidence root")
    if world != runtime / "world":
        raise InvocationError("Atlas recovery must use one fixture runtime/world")
    settings = _settings(evidence.settings, world)
    interrupted = _report(evidence.interrupted_report, "INTERRUPTED", runtime, world, evidence_root, settings, "interrupted stage")
    recovered = _report(evidence.recovered_report, "COMPLETE", runtime, world, evidence_root, settings, "recovery stage")
    if interrupted.runtime_report_path != recovered.runtime_report_path or interrupted.captured_report_path == recovered.captured_report_path:
        raise InvocationError("Atlas restart must reuse one runtime report but retain distinct captured reports")
    if not (0 < interrupted.completed_cells < interrupted.total_cells):
        raise InvocationError("interrupted stage must retain a genuine partial Atlas checkpoint")
    if (recovered.completed_chunks, recovered.completed_cells) != (recovered.total_chunks, recovered.total_cells):
        raise InvocationError("recovery stage must complete every chunk and Atlas cell")
    first_atlas = _atlas(evidence.interrupted_atlas, interrupted, world, "interrupted stage")
    second_atlas = _atlas(evidence.recovered_atlas, recovered, world, "recovery stage")
    if first_atlas.atlas_path != second_atlas.atlas_path:
        raise InvocationError("Atlas recovery must observe one persistent Atlas file")
    if evidence.interrupted_exit_code != 0 or evidence.recovery_exit_code != 0:
        raise InvocationError("both Atlas server stages must have clean exits")
    first_ledger = _ledger(evidence.interrupted_ledger, "interrupted", INTERRUPTED_MARKERS, evidence_root)
    second_ledger = _ledger(evidence.recovery_ledger, "recovery", RECOVERY_MARKERS, evidence_root)
    if first_ledger.path == second_ledger.path or first_ledger.events[-1].timestamp_millis >= second_ledger.events[0].timestamp_millis:
        raise InvocationError("Atlas stage marker ledgers must be distinct and globally ordered")
    return AtlasRecoveryQualification(identity, evidence)


validate_atlas_prewarm_recovery = validate_atlas_recovery_qualification
