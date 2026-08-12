#!/usr/bin/env python3
"""Pure, fail-closed plan for Phase 4 nightly runtime qualification.

This is intentionally a contract only: it neither reads the named inputs nor
creates any profile, port binding, process, world copy, or report.  An eventual
executor must consume the returned plan verbatim, checksum every immutable
input again, and create the declared output paths using exclusive writes.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import re
from typing import Any, Mapping, Sequence

from external_runtime_smoke import CandidateJar
from minecraft_qualification_model import InvocationError, QualificationPaths, contained_path, is_within


SHA256 = re.compile(r"^[0-9a-f]{64}$")
SAFE_SMALL_PROFILE = "safe-small"
PRODUCTION_PROFILE = "production-atlas-render"
NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS = 1_800
NIGHTLY_PRODUCTION_TIMEOUT_SECONDS = 7_200


class NightlyFixture(str, Enum):
    CREATION_RELOAD = "creation-reload"
    WORLDGEN_SEAM_STRUCTURES = "worldgen-seam-structures"
    ATLAS_PREWARM_RECOVERY = "atlas-prewarm-recovery"
    ATLAS_UI_REVISION = "atlas-ui-revision"
    CLIENT_HANDSHAKE = "client-handshake"
    SEAM_GAMEPLAY_MULTIPLAYER = "seam-gameplay-multiplayer"
    RAID_SEAM = "raid-seam"
    MAP_COMPASS_RECONNECT = "map-compass-reconnect"
    LIFECYCLE_PORTALS = "lifecycle-portals"
    CURVED_OBJECTS = "curved-objects"
    PRODUCTION_ATLAS_RENDER = "production-atlas-render"


@dataclass(frozen=True)
class ImmutableNightlyInput:
    """A path/hash identity that the future executor must recheck before use."""

    role: str
    path: Path
    sha256: str


@dataclass(frozen=True)
class NightlySourceInputs:
    """Inputs accepted only after quick qualification has terminally passed."""

    frozen_candidate: CandidateJar
    quick_terminal_evidence: ImmutableNightlyInput
    production_world: ImmutableNightlyInput | None = None


@dataclass(frozen=True)
class NightlyFixturePlan:
    ordinal: int
    fixture: NightlyFixture
    profile: str
    port: int
    timeout_seconds: int
    existing_fixture: str
    input_roles: tuple[str, ...]
    runtime_root: Path
    world_root: Path
    evidence_json: Path
    evidence_markdown: Path
    required_markers: tuple[str, ...]
    required_outputs: tuple[Path, ...]


@dataclass(frozen=True)
class NightlyQualificationPlan:
    cell_id: str
    loader: str
    minecraft_version: str
    candidate: CandidateJar
    quick_terminal_evidence: ImmutableNightlyInput
    production_world: ImmutableNightlyInput | None
    fixture_plans: tuple[NightlyFixturePlan, ...]
    lock_path: Path
    future_validations: tuple[str, ...]


_FIXTURES: tuple[tuple[NightlyFixture, str, str, int, tuple[str, ...], tuple[str, ...]], ...] = (
    (NightlyFixture.CREATION_RELOAD, SAFE_SMALL_PROFILE, "world-creation/settings persistence", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("world-created", "settings-persisted", "settings-reloaded", "fixture-pass"), ("terminal.json", "terminal.md")),
    (NightlyFixture.WORLDGEN_SEAM_STRUCTURES, SAFE_SMALL_PROFILE, "stronghold/worldgen structure matrix", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("worldgen-ready", "seam-structure-verified", "fixture-pass"), ("terminal.json", "terminal.md", "worldgen-summary.json")),
    (NightlyFixture.ATLAS_PREWARM_RECOVERY, SAFE_SMALL_PROFILE, "headless Atlas prewarm/recovery", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("atlas-started", "atlas-recovered", "atlas-complete", "fixture-pass"), ("terminal.json", "terminal.md", "atlas-summary.json")),
    (NightlyFixture.ATLAS_UI_REVISION, SAFE_SMALL_PROFILE, "Atlas map/control fixture", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("client-ready", "atlas-ui-captured", "revision-verified", "fixture-pass"), ("terminal.json", "terminal.md", "captures/index.json")),
    (NightlyFixture.CLIENT_HANDSHAKE, SAFE_SMALL_PROFILE, "integrated client resource/shader handshake", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("client-ready", "settings-acknowledged", "resources-ready", "fixture-pass"), ("terminal.json", "terminal.md")),
    (NightlyFixture.SEAM_GAMEPLAY_MULTIPLAYER, SAFE_SMALL_PROFILE, "dedicated two-client seam gameplay matrix", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("both-clients-ready", "seam-gameplay-verified", "reconnect-verified", "fixture-pass"), ("terminal.json", "terminal.md", "multiplayer-summary.json")),
    (NightlyFixture.RAID_SEAM, SAFE_SMALL_PROFILE, "dedicated raid seam persistence fixture", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("raid-started", "raid-reloaded", "raid-victory", "fixture-pass"), ("terminal.json", "terminal.md", "raid-summary.json")),
    (NightlyFixture.MAP_COMPASS_RECONNECT, SAFE_SMALL_PROFILE, "map/compass seam and reconnect fixture", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("map-ready", "seam-markers-verified", "reconnect-verified", "fixture-pass"), ("terminal.json", "terminal.md", "map-summary.json")),
    (NightlyFixture.LIFECYCLE_PORTALS, SAFE_SMALL_PROFILE, "Overworld/Nether/End lifecycle fixture", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("overworld-ready", "portals-verified", "lifecycle-reopened", "fixture-pass"), ("terminal.json", "terminal.md")),
    (NightlyFixture.CURVED_OBJECTS, SAFE_SMALL_PROFILE, "curved block/entity-object capture fixture", NIGHTLY_SAFE_SMALL_TIMEOUT_SECONDS,
     ("client-ready", "curved-objects-captured", "fixture-pass"), ("terminal.json", "terminal.md", "captures/index.json")),
    (NightlyFixture.PRODUCTION_ATLAS_RENDER, PRODUCTION_PROFILE, "production Atlas/render projection fixture", NIGHTLY_PRODUCTION_TIMEOUT_SECONDS,
     ("production-world-copied", "atlas-complete", "projection-captured", "frame-pacing-recorded", "fixture-pass"),
     ("terminal.json", "terminal.md", "captures/index.json", "frame-pacing.json")),
)


def _require_sha256(value: str, label: str) -> str:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        raise InvocationError(f"{label} must be a lowercase SHA-256")
    return value


def _require_cell(cell: Mapping[str, Any]) -> tuple[str, str, str, int]:
    cell_id, loader = cell.get("id"), cell.get("loader")
    minecraft, profile = cell.get("minecraft"), cell.get("profile")
    if not isinstance(cell_id, str) or not cell_id or loader not in {"fabric", "neoforge"}:
        raise InvocationError("nightly qualification cell has an invalid id or loader")
    if not isinstance(minecraft, Mapping) or not isinstance(minecraft.get("version"), str) or not minecraft["version"]:
        raise InvocationError("nightly qualification cell has no Minecraft version")
    if not isinstance(profile, Mapping) or not isinstance(profile.get("server_port"), int) \
            or isinstance(profile["server_port"], bool):
        raise InvocationError("nightly qualification cell has no server port")
    port = profile["server_port"]
    if not 1 <= port <= 65_500:
        raise InvocationError("nightly qualification base port cannot allocate the fixture range")
    return cell_id, loader, minecraft["version"], port


def _require_input(value: ImmutableNightlyInput, label: str) -> ImmutableNightlyInput:
    if not isinstance(value, ImmutableNightlyInput) or not isinstance(value.role, str) or not value.role:
        raise InvocationError(f"{label} must identify an immutable input")
    _require_sha256(value.sha256, label + " hash")
    if not isinstance(value.path, Path) or value.path.is_absolute() is False:
        raise InvocationError(f"{label} path must be absolute for later independent verification")
    return value


def _require_source_inputs(inputs: NightlySourceInputs, loader: str) -> NightlySourceInputs:
    if not isinstance(inputs, NightlySourceInputs):
        raise InvocationError("nightly qualification needs explicit immutable source inputs")
    candidate = inputs.frozen_candidate
    if not isinstance(candidate, CandidateJar) or candidate.loader != loader or not candidate.path.is_absolute():
        raise InvocationError("nightly frozen candidate must be an absolute matching-loader input")
    _require_sha256(candidate.sha256, "nightly frozen candidate")
    quick = _require_input(inputs.quick_terminal_evidence, "quick terminal evidence")
    if quick.role != "quick-terminal-evidence":
        raise InvocationError("nightly qualification requires a quick-terminal-evidence input")
    if inputs.production_world is not None:
        production = _require_input(inputs.production_world, "production world")
        if production.role != "production-world":
            raise InvocationError("production world input has the wrong role")
    return inputs


def _fixture_paths(paths: QualificationPaths, ordinal: int, fixture: NightlyFixture) -> tuple[Path, Path, Path, Path]:
    name = f"{ordinal:02d}-{fixture.value}"
    root = contained_path(paths.run_directory, f"nightly/{name}", "nightly fixture root")
    world = contained_path(paths.world_directory, f"nightly/{name}", "nightly fixture world")
    evidence = contained_path(paths.evidence_directory, f"nightly/{name}", "nightly fixture evidence")
    runtime = contained_path(root, "runtime", "nightly fixture runtime")
    if not all(is_within(value, paths.cell_root) for value in (root, world, evidence, runtime)):
        raise InvocationError("nightly fixture path escapes qualification cell")
    return root, runtime, world, evidence


def nightly_qualification_plan(
    cell: Mapping[str, Any],
    paths: QualificationPaths,
    inputs: NightlySourceInputs,
) -> NightlyQualificationPlan:
    """Return the fixed ordered nightly matrix for one already-qualified cell.

    The future executor must check the frozen candidate plus *passing quick
    terminal record* before it copies either.  Production rendering is kept
    last and requires its separately immutable world input; no plan quietly
    substitutes a local save.
    """
    cell_id, loader, minecraft_version, base_port = _require_cell(cell)
    if paths.cell_id != cell_id:
        raise InvocationError("nightly paths and selected manifest cell differ")
    source = _require_source_inputs(inputs, loader)
    if not is_within(paths.cell_root, paths.repository_root / "dist" / "qualification"):
        raise InvocationError("nightly qualification paths must be below dist/qualification")
    planned: list[NightlyFixturePlan] = []
    ports: set[int] = set()
    for ordinal, (fixture, profile, existing, timeout, markers, outputs) in enumerate(_FIXTURES, start=1):
        if profile == PRODUCTION_PROFILE and source.production_world is None:
            raise InvocationError("nightly production Atlas/render fixture requires an immutable production world")
        root, runtime, world, evidence = _fixture_paths(paths, ordinal, fixture)
        port = base_port + ordinal
        if port > 65535 or port in ports:
            raise InvocationError("nightly fixture ports are not unique and valid")
        ports.add(port)
        output_paths = tuple(contained_path(evidence, output, "nightly fixture output") for output in outputs)
        if len(markers) != len(set(markers)) or markers[-1] != "fixture-pass":
            raise InvocationError("nightly fixture markers must be unique and terminate with fixture-pass")
        input_roles = ("frozen-candidate", "quick-terminal-evidence") + (("production-world",) if profile == PRODUCTION_PROFILE else ())
        planned.append(NightlyFixturePlan(
            ordinal, fixture, profile, port, timeout, existing, input_roles,
            runtime, world, output_paths[0], output_paths[1], markers, output_paths,
        ))
    return NightlyQualificationPlan(
        cell_id, loader, minecraft_version, source.frozen_candidate, source.quick_terminal_evidence,
        source.production_world, tuple(planned), paths.lock_path,
        (
            "Re-check every immutable input hash immediately before copying it.",
            "Run fixtures in declared order; do not reuse worlds, ports, reports, or runtime directories.",
            "Require every declared marker and output before recording a fixture PASS.",
            "A nightly executor must remain below this qualification cell and never inspect user/live worlds.",
        ),
    )


# Concise public alias for later execution wiring.
plan_minecraft_nightly_qualification = nightly_qualification_plan
