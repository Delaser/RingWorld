#!/usr/bin/env python3
"""Pure external-runtime plan for the nightly Atlas recovery fixture.

This is deliberately a planning seam.  It names one fresh fixture runtime,
the one world it may restart, and the immutable evidence destinations, but it
does not create them, download inputs, inspect jars, or launch Minecraft.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from pathlib import Path
import re
from typing import Any, Mapping

from external_runtime_smoke import CandidateJar, ExternalRuntimeSmokePlan, LaunchPlan, PlannedFile, external_runtime_smoke_plan
from minecraft_qualification_model import InvocationError, QualificationPaths, contained_path, is_within


FIXTURE_ORDINAL = 3
FIXTURE_NAME = "03-atlas-prewarm-recovery"
REPORT_FILENAME = "result.json"
SHA256 = re.compile(r"^[0-9a-f]{64}$")


@dataclass(frozen=True)
class QuickTerminalEvidenceInput:
    """The already-validated quick gate record consumed by this nightly run."""

    path: Path
    sha256: str


@dataclass(frozen=True)
class AtlasRecoveryStagePlan:
    """One launch of the same installed dedicated-server runtime."""

    name: str
    launch: LaunchPlan
    runtime_report_path: Path
    expected_status: str
    captured_report_path: Path
    captured_atlas_path: Path
    marker_path: Path


@dataclass(frozen=True)
class ExternalRuntimeAtlasRecoveryPlan:
    """The two-stage, one-world external Atlas recovery contract."""

    smoke: ExternalRuntimeSmokePlan
    fixture_root: Path
    runtime_root: Path
    world_root: Path
    evidence_root: Path
    settings_path: Path
    atlas_path: Path
    recovery_input_atlas_path: Path
    quick_terminal_evidence: QuickTerminalEvidenceInput
    quick_evidence_root: Path
    frozen_candidate_root: Path
    stages: tuple[AtlasRecoveryStagePlan, AtlasRecoveryStagePlan]
    lock_path: Path
    future_validations: tuple[str, ...]


def _fixture_paths(paths: QualificationPaths) -> tuple[Path, Path, Path, Path]:
    fixture_root = contained_path(paths.run_directory, f"nightly/{FIXTURE_NAME}", "Atlas recovery fixture root")
    runtime = contained_path(fixture_root, "runtime", "Atlas recovery fixture runtime")
    world = contained_path(runtime, "world", "Atlas recovery fixture world")
    evidence = contained_path(paths.evidence_directory, f"nightly/{FIXTURE_NAME}", "Atlas recovery fixture evidence")
    if not all(is_within(path, paths.cell_root) for path in (fixture_root, runtime, world, evidence)):
        raise InvocationError("Atlas recovery fixture paths escape their qualification cell")
    return fixture_root, runtime, world, evidence


def _headless_launch(smoke: ExternalRuntimeSmokePlan, report_filename: str) -> LaunchPlan:
    if report_filename != REPORT_FILENAME:
        raise InvocationError("Atlas recovery must use the fixed result.json report name")
    props = ("-Dringworld.headlessPrewarm=true", f"-Dringworld.headlessPrewarmReport={report_filename}")
    if smoke.loader == "fabric":
        argv = smoke.launch.argv
        try:
            jar_index = argv.index("-jar")
        except ValueError as error:
            raise InvocationError("Fabric external runtime has no -jar launch boundary") from error
        # Java system properties must be parsed before the ``-jar`` operand.
        return LaunchPlan(argv[:jar_index] + props + argv[jar_index:], smoke.launch.cwd, smoke.launch.timeout_seconds)
    if smoke.loader == "neoforge":
        # The installer-owned run script reads user_jvm_args.txt.  The executor
        # adds exactly these properties after it verifies that regular file.
        return smoke.launch
    raise InvocationError("Atlas recovery requires Fabric or NeoForge")


def _fixture_world_files(smoke: ExternalRuntimeSmokePlan) -> ExternalRuntimeSmokePlan:
    """Point this one fixture's vanilla level root at ``runtime/world``."""
    rewritten: list[PlannedFile] = []
    replaced = False
    for file in smoke.files:
        if file.path != smoke.layout.server_properties_path:
            rewritten.append(file)
            continue
        line = "level-name=qualification-safe-small"
        if file.contents.count(line) != 1:
            raise InvocationError("external smoke plan has no unique disposable level-name")
        rewritten.append(PlannedFile(file.path, file.contents.replace(line, "level-name=world")))
        replaced = True
    if not replaced:
        raise InvocationError("external smoke plan has no server.properties fixture file")
    return replace(smoke, files=tuple(rewritten))


def external_runtime_atlas_recovery_plan(
    cell: Mapping[str, Any],
    candidate: CandidateJar,
    paths: QualificationPaths,
    quick_terminal_evidence: QuickTerminalEvidenceInput,
    *,
    frozen_candidate_root: Path | None = None,
    quick_evidence_root: Path | None = None,
) -> ExternalRuntimeAtlasRecoveryPlan:
    """Plan the exact nightly stage-3 headless interruption/recovery fixture."""
    fixture_root, runtime, world, evidence = _fixture_paths(paths)
    if frozen_candidate_root is None or not frozen_candidate_root.is_absolute() \
            or not is_within(candidate.path, frozen_candidate_root):
        raise InvocationError("Atlas recovery needs the reviewed frozen candidate root")
    quick_root = validate_quick_evidence_input(
        paths, quick_terminal_evidence, quick_evidence_root,
        "Atlas recovery",
    )
    smoke = _fixture_world_files(external_runtime_smoke_plan(
        cell,
        candidate,
        paths,
        frozen_candidate_root=frozen_candidate_root,
        runtime_root=runtime,
    ))
    if smoke.layout.root != runtime or smoke.layout.root.parent != fixture_root:
        raise InvocationError("Atlas recovery must own the exact nightly fixture runtime")
    settings = contained_path(world, "dimensions/minecraft/overworld/data/ringworld/settings.dat", "Atlas recovery settings")
    atlas = contained_path(world, "dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz", "Atlas recovery Atlas")
    report = contained_path(world, f"ringworld-prewarm/{REPORT_FILENAME}", "Atlas recovery runtime report")
    stage_one = AtlasRecoveryStagePlan(
        "interrupted",
        _headless_launch(smoke, REPORT_FILENAME),
        report,
        "INTERRUPTED",
        contained_path(evidence, "interrupted-result.json", "interrupted report capture"),
        contained_path(evidence, "interrupted-atlas.rwat.gz", "interrupted Atlas capture"),
        contained_path(evidence, "interrupted-markers.json", "interrupted marker capture"),
    )
    stage_two = AtlasRecoveryStagePlan(
        "recovery",
        _headless_launch(smoke, REPORT_FILENAME),
        report,
        "COMPLETE",
        contained_path(evidence, "complete-result.json", "complete report capture"),
        contained_path(evidence, "complete-atlas.rwat.gz", "complete Atlas capture"),
        contained_path(evidence, "recovery-markers.json", "recovery marker capture"),
    )
    recovery_input = contained_path(evidence, "recovery-input-atlas.rwat.gz", "recovery input Atlas capture")
    return ExternalRuntimeAtlasRecoveryPlan(
        smoke,
        fixture_root,
        runtime,
        world,
        evidence,
        settings,
        atlas,
        recovery_input,
        quick_terminal_evidence,
        quick_root,
        frozen_candidate_root,
        (stage_one, stage_two),
        paths.lock_path,
        (
            "Re-inspect the retained frozen candidate and quick terminal evidence before any runtime action.",
            "Wait for a durable parsed partial Atlas before sending the first graceful stop.",
            "Capture the exact partial report/settings/Atlas and prove the same Atlas bytes are present before restart.",
            "Only a parsed COMPLETE report plus independently decoded complete Atlas may claim fixture PASS.",
        ),
    )


def validate_quick_evidence_input(
    paths: QualificationPaths,
    quick_terminal_evidence: QuickTerminalEvidenceInput,
    quick_evidence_root: Path | None,
    label: str,
) -> Path:
    """Bind one selected patch cell to its exact prior strict quick record.

    The retained same-file jar lives below the oldest ABI cell, while each
    patch has its own quick cell/evidence directory.  The prior record may
    therefore be in another version profile, but it must still be the exact
    canonical cell path below ``dist/qualification`` and the fixed strict
    evidence filename.  Its contents are independently parsed by the executor.
    """
    quick_root = paths.cell_root if quick_evidence_root is None else quick_evidence_root
    qualification_root = paths.repository_root / "dist/qualification"
    expected_record = quick_root / "evidence/strict-terminal-evidence.json"
    if not isinstance(quick_terminal_evidence, QuickTerminalEvidenceInput) \
            or not quick_terminal_evidence.path.is_absolute() \
            or not quick_root.is_absolute() \
            or not is_within(quick_root, qualification_root) \
            or quick_root.name != paths.cell_id \
            or quick_terminal_evidence.path != expected_record \
            or SHA256.fullmatch(quick_terminal_evidence.sha256) is None:
        raise InvocationError(f"{label} needs a contained hash-identified quick terminal record")
    return quick_root


plan_external_runtime_atlas_recovery = external_runtime_atlas_recovery_plan
