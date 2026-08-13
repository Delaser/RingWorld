#!/usr/bin/env python3
"""Focused pure tests for the nightly worldgen qualification contract."""

from __future__ import annotations

from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(ROOT / "scripts"))

from minecraft_atlas_recovery_qualification import PersistedRingSettingsObservation  # noqa: E402
from minecraft_qualification_model import InvocationError  # noqa: E402
from minecraft_worldgen_qualification import (  # noqa: E402
    QualificationIdentity, TimedMarker, WorldgenLogFact, WorldgenQualificationEvidence,
    WorldgenStageEvidence, validate_worldgen_qualification,
)
from run_worldgen_structure_matrix import REQUIRED_MAJOR_FAMILIES  # noqa: E402

HASH = "a" * 64


class WorldgenQualificationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        matrix = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.cell = next(cell for cell in matrix["cells"] if cell["id"] == "26.1-fabric")

    def valid(self, root: Path):
        fixture = root / "run/nightly/02-worldgen-seam-structures"
        evidence = root / "evidence/nightly/02-worldgen-seam-structures"
        expected = (
            ("production-fresh", "production", "ringworld-regression-1", 16384, 256, False, 1),
            ("production-resume", "production", "ringworld-regression-1", 16384, 256, True, 1),
            ("seam-crossing", "seam-crossing", "ringworld-matrix-0", 2048, 416, False, 2),
            ("terminal-policy", "terminal-policy", "ringworld-matrix-3", 2048, 416, False, 3),
        )
        families = ["badlands", "beach", "cave", "desert"], ["badlands", "beach", "cave", "desert"], ["forest", "jungle", "mountain", "ocean", "plains", "river", "savanna"], ["snowy", "swamp", "taiga"]
        stages = []
        for index, (name, key, seed, circumference, width, resume, numeric) in enumerate(expected):
            runtime = fixture / key / "runtime"
            world = runtime / "world"
            settings = PersistedRingSettingsObservation(width, circumference, numeric, 160, 64, 4, 3, world / "dimensions/minecraft/overworld/data/ringworld/settings.dat", HASH)
            record = {
                "numeric_seed": numeric, "circumference": circumference, "width": width,
                "families": families[index], "biomes": families[index], "chunks": 10,
                "cave_air": 1, "ores": 1, "logs": 1, "starts": 1,
                "structures": ["minecraft:village"], "crossing_starts": 1 if index == 2 else 0,
                "crossing_structures": ["minecraft:village"] if index == 2 else [], "references": 1,
                "loot": 1, "monument_status": "SATISFIED" if index in {0, 1, 2} else "UNSATISFIED",
                "monument_reason": "ok", "monument_candidate": "0,0",
                "monument_spawn_override_entries": 1 if index == 2 else 0,
                "spawn_override_structures": 1 if index == 2 else 0,
                "spawn_override_ids": ["minecraft:ocean_monument"] if index == 2 else [],
            }
            log = WorldgenLogFact(name, resume, seed, numeric, circumference, width, 4, 3, record,
                                  root / "logs" / f"{name}.log", evidence / f"{name}.log", HASH, "b" * 64,
                                  tuple(TimedMarker(marker, index * 100 + offset) for offset, marker in enumerate(("worldgen-matrix-record", "stronghold-test-pass"), 1)), 0)
            stages.append(WorldgenStageEvidence(runtime, world, settings, log))
        return QualificationIdentity("26.1-fabric", "fabric", "26.1", HASH, "c" * 64), WorldgenQualificationEvidence(fixture, evidence, root / "logs", tuple(stages))

    def reject(self, mutate):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            identity, evidence = mutate(identity, evidence)
            with self.assertRaises(InvocationError):
                validate_worldgen_qualification(self.cell, identity, evidence)

    def test_accepts_exact_four_stage_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            result = validate_worldgen_qualification(self.cell, identity, evidence)
            self.assertEqual("worldgen-seam-structures", result.as_dict()["fixture"])
            self.assertEqual(REQUIRED_MAJOR_FAMILIES, set(result.as_dict()["families"]))

    def test_rejects_identity_settings_and_shared_world_contract(self):
        self.reject(lambda i, e: (replace(i, loader="neoforge"), e))
        self.reject(lambda i, e: (i, replace(e, stages=(replace(e.stages[0], settings=replace(e.stages[0].settings, terrain_noise_mapping=3)),) + e.stages[1:])))
        self.reject(lambda i, e: (i, replace(e, stages=(replace(e.stages[0], settings=replace(e.stages[0].settings, surface_reference_y=63)),) + e.stages[1:])))
        self.reject(lambda i, e: (i, replace(e, stages=e.stages[:1] + (replace(e.stages[1], world_root=e.stages[1].runtime_root / "other"),) + e.stages[2:])))
        self.reject(lambda i, e: (i, replace(e, stages=e.stages[:2] + (replace(e.stages[2], world_root=e.stages[0].world_root),) + e.stages[3:])))

    def test_accepts_distinct_capture_hashes_for_same_persisted_production_world(self):
        with tempfile.TemporaryDirectory() as directory:
            identity, evidence = self.valid(Path(directory))
            resumed = evidence.stages[1]
            refreshed = replace(resumed, settings=replace(resumed.settings, settings_sha256="d" * 64))
            result = validate_worldgen_qualification(self.cell, identity, replace(evidence, stages=(evidence.stages[0], refreshed) + evidence.stages[2:]))
            self.assertEqual("production-resume", result.as_dict()["stages"][1])

    def test_rejects_log_identity_markers_and_reload_drift(self):
        self.reject(lambda i, e: (i, replace(e, stages=(replace(e.stages[0], log=replace(e.stages[0].log, settings_format_version=2)),) + e.stages[1:])))
        self.reject(lambda i, e: (i, replace(e, stages=e.stages[:2] + (replace(e.stages[2], log=replace(e.stages[2].log, markers=tuple(reversed(e.stages[2].log.markers)))),) + e.stages[3:])))
        self.reject(lambda i, e: (i, replace(e, stages=(e.stages[0], replace(e.stages[1], log=replace(e.stages[1].log, record={**e.stages[1].log.record, "loot": 2}))) + e.stages[2:])))

    def test_rejects_missing_aggregate_coverage_or_terminal_policy(self):
        self.reject(lambda i, e: (i, replace(e, stages=tuple(replace(stage, log=replace(stage.log, record={**stage.log.record, "families": ["plains"]})) for stage in e.stages))))
        self.reject(lambda i, e: (i, replace(e, stages=tuple(replace(stage, log=replace(stage.log, record={**stage.log.record, "monument_status": "SATISFIED"})) for stage in e.stages))))


if __name__ == "__main__":
    unittest.main()
