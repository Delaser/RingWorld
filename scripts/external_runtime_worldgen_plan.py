#!/usr/bin/env python3
"""Pure plan for the four-stage nightly worldgen/seam-structure fixture."""

from __future__ import annotations

from dataclasses import dataclass, replace
from pathlib import Path
import re
from typing import Any, Mapping

from external_runtime_atlas_recovery_plan import QuickTerminalEvidenceInput, validate_quick_evidence_input
from external_runtime_smoke import CandidateJar, ExternalRuntimeSmokePlan, PlannedFile, external_runtime_smoke_plan
from minecraft_qualification_model import InvocationError, QualificationPaths, contained_path, is_within, qualification_port
from minecraft_worldgen_qualification import FIXTURE_NAME, REQUIRED_MARKERS


FIXTURE_ORDINAL = 2
SHA256 = re.compile(r"^[0-9a-f]{64}$")


@dataclass(frozen=True)
class WorldgenStagePlan:
    name: str
    seed: str
    circumference_blocks: int
    width_blocks: int
    wall_height_blocks: int
    resume: bool
    runtime_root: Path
    world_root: Path
    captured_log_path: Path
    summary_path: Path
    required_markers: tuple[str, ...]
    server_properties: tuple[tuple[str, str], ...]
    ringworld_properties: tuple[tuple[str, str], ...]
    jvm_properties: tuple[str, ...]
    smoke: ExternalRuntimeSmokePlan

    @property
    def runtime_bootstrap(self) -> tuple[tuple[str, str], ...]:
        """Properties written by a later runtime assembler, never Gradle flags."""
        return (
            *self.server_properties,
            *self.ringworld_properties,
        )


@dataclass(frozen=True)
class ExternalRuntimeWorldgenPlan:
    cell_id: str
    loader: str
    minecraft_version: str
    candidate: CandidateJar
    quick_terminal_evidence: QuickTerminalEvidenceInput
    frozen_candidate_root: Path
    quick_evidence_root: Path
    fixture_root: Path
    evidence_root: Path
    stages: tuple[WorldgenStagePlan, WorldgenStagePlan, WorldgenStagePlan, WorldgenStagePlan]
    lock_path: Path
    future_validations: tuple[str, ...]


_STAGES = (
    ("production-fresh", "production", "ringworld-regression-1", 16_384, 256, False),
    ("production-resume", "production", "ringworld-regression-1", 16_384, 256, True),
    ("seam-crossing", "seam-crossing", "ringworld-matrix-0", 2_048, 416, False),
    ("terminal-policy", "terminal-policy", "ringworld-matrix-3", 2_048, 416, False),
)


def _replace_property(contents: str, name: str, value: str) -> str:
    prefix = name + "="
    lines = contents.splitlines()
    matches = [index for index, line in enumerate(lines) if line.startswith(prefix)]
    if len(matches) != 1:
        raise InvocationError(f"external runtime config has no unique {name}")
    lines[matches[0]] = prefix + value
    return "\n".join(lines) + "\n"


def _stage_smoke(cell: Mapping[str, Any], candidate: CandidateJar, paths: QualificationPaths,
                 frozen_candidate_root: Path, runtime: Path, *, seed: str,
                 circumference: int, width: int, port: int) -> ExternalRuntimeSmokePlan:
    smoke = external_runtime_smoke_plan(
        cell, candidate, paths, frozen_candidate_root=frozen_candidate_root,
        runtime_root=runtime,
    )
    rewritten: list[PlannedFile] = []
    for file in smoke.files:
        contents = file.contents
        if file.path == smoke.layout.server_properties_path:
            for name, value in (
                ("server-port", str(port)), ("level-name", "world"),
                ("level-seed", seed), ("motd", "RingWorld worldgen qualification"),
            ):
                contents = _replace_property(contents, name, value)
        elif file.path == smoke.layout.ringworld_properties_path:
            for name, value in (
                ("widthBlocks", str(width)),
                ("circumferenceBlocks", str(circumference)),
                ("wallHeightBlocks", "160"),
                ("pregenerateTerrainAtlas", "false"),
                ("requestOceanMonument", "true"),
            ):
                contents = _replace_property(contents, name, value)
        rewritten.append(PlannedFile(file.path, contents))
    return replace(smoke, files=tuple(rewritten))


def external_runtime_worldgen_plan(cell: Mapping[str, Any], candidate: CandidateJar, paths: QualificationPaths,
                                   quick_terminal_evidence: QuickTerminalEvidenceInput, *,
                                   frozen_candidate_root: Path | None = None,
                                   quick_evidence_root: Path | None = None) -> ExternalRuntimeWorldgenPlan:
    """Name fixture-owned paths and fixed cases; do not inspect or create them."""
    minecraft = cell.get("minecraft") if isinstance(cell, Mapping) else None
    if not isinstance(cell.get("id"), str) or cell["id"] != paths.cell_id or cell.get("loader") not in {"fabric", "neoforge"} or not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str):
        raise InvocationError("worldgen plan must target one canonical qualification cell")
    if not isinstance(candidate, CandidateJar) or candidate.loader != cell["loader"] or not candidate.path.is_absolute() or SHA256.fullmatch(candidate.sha256) is None:
        raise InvocationError("worldgen plan needs a hash-identified matching frozen candidate")
    if frozen_candidate_root is None or not frozen_candidate_root.is_absolute() or not is_within(candidate.path, frozen_candidate_root):
        raise InvocationError("worldgen plan needs the reviewed frozen candidate root")
    quick_root = validate_quick_evidence_input(
        paths, quick_terminal_evidence, quick_evidence_root,
        "worldgen plan",
    )
    fixture = contained_path(paths.run_directory, f"nightly/{FIXTURE_NAME}", "worldgen fixture root")
    evidence = contained_path(paths.evidence_directory, f"nightly/{FIXTURE_NAME}", "worldgen evidence root")
    if not all(is_within(value, paths.cell_root) for value in (fixture, evidence)):
        raise InvocationError("worldgen plan paths escape the qualification cell")
    stages: list[WorldgenStagePlan] = []
    base_port = qualification_port(cell)
    runtime_smokes: dict[str, ExternalRuntimeSmokePlan] = {}
    runtime_ports = {"production": base_port + 1, "seam-crossing": base_port + 2, "terminal-policy": base_port + 3}
    for name, world_key, seed, circumference, width, resume in _STAGES:
        runtime = contained_path(fixture, f"{world_key}/runtime", "worldgen stage runtime")
        world = contained_path(runtime, "world", "worldgen stage world")
        smoke = runtime_smokes.get(world_key)
        if smoke is None:
            smoke = _stage_smoke(
                cell, candidate, paths, frozen_candidate_root, runtime,
                seed=seed, circumference=circumference, width=width,
                port=runtime_ports[world_key],
            )
            runtime_smokes[world_key] = smoke
        stages.append(WorldgenStagePlan(
            name, seed, circumference, width, 160, resume, runtime, world,
            contained_path(evidence, f"{name}.log", "worldgen captured log"),
            contained_path(evidence, f"{name}.json", "worldgen summary"), REQUIRED_MARKERS,
            (("level-name", "world"), ("level-seed", seed), ("online-mode", "false"),
             ("server-port", str(runtime_ports[world_key]))),
            (("circumferenceBlocks", str(circumference)), ("widthBlocks", str(width)),
             ("wallHeightBlocks", "160"), ("pregenerateTerrainAtlas", "false"),
             ("requestOceanMonument", "true")),
            ("-Dringworld.strongholdTest=true", "-Dringworld.worldgenMatrix=true",
             f"-Dringworld.strongholdTestResume={str(resume).lower()}"),
            smoke,
        ))
    if stages[0].world_root != stages[1].world_root or len({stage.world_root for stage in stages[2:]}) != 2:
        raise InvocationError("worldgen plan must share only the production fresh/resume world")
    return ExternalRuntimeWorldgenPlan(
        cell["id"], cell["loader"], minecraft["version"], candidate,
        quick_terminal_evidence, frozen_candidate_root, quick_root, fixture, evidence,
        tuple(stages), paths.lock_path,
        (
            "Re-inspect the retained frozen candidate and quick terminal evidence before runtime work.",
            "Run stages in declaration order; only production-resume reuses production-fresh/runtime/world.",
            "Parse settings and the complete worldgen-matrix record independently before recording PASS.",
            "Require an ordered parsed worldgen-matrix record and stronghold-test PASS marker with a clean exit.",
        ),
    )


plan_external_runtime_worldgen = external_runtime_worldgen_plan
