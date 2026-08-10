#!/usr/bin/env python3

import unittest

from run_worldgen_structure_matrix import ROOT, loader_runtime, parse_log, validate_aggregate, validate_reload


def record(**overrides):
    base = {
        "families": sorted({
            "badlands", "beach", "cave", "desert", "forest", "jungle", "mountain",
            "ocean", "plains", "river", "savanna", "snowy", "swamp", "taiga",
        }),
        "cave_air": 1,
        "ores": 1,
        "logs": 1,
        "starts": 1,
        "crossing_starts": 1,
        "references": 1,
        "loot": 1,
        "spawn_override_structures": 0,
        "spawn_override_ids": [],
        "monument_status": "SATISFIED",
        "monument_spawn_override_entries": 1,
    }
    base.update(overrides)
    return base


class WorldgenStructureMatrixTest(unittest.TestCase):
    def test_selects_isolated_loader_runtime(self):
        fabric = loader_runtime("fabric")
        neoforge = loader_runtime("neoforge")
        self.assertEqual(":runStrongholdTestServer", fabric.task)
        self.assertEqual(":neoforge:runStrongholdTestServer", neoforge.task)
        self.assertEqual(ROOT / "run-stronghold-test" / "logs" / "latest.log", fabric.run_log)
        self.assertEqual(ROOT / "neoforge" / "run-stronghold-test" / "logs" / "latest.log",
                         neoforge.run_log)
        self.assertNotEqual(fabric.report_dir, neoforge.report_dir)

    def test_parses_complete_runtime_record(self):
        log = """
[worldgen-matrix] seed=-12 layout=2048x416 biomeFamilies=[forest, ocean] biomeIds=[minecraft:forest, minecraft:ocean] chunks=208 caveAir=3 ores=4 logs=5 starts=2 structureIds=[minecraft:mineshaft] crossingStarts=1 crossingStructureIds=[minecraft:mineshaft] references=6 lootContainers=7 structuresWithSpawnOverrides=0 spawnOverrideStructureIds=[]
[worldgen-matrix] monumentStatus=SATISFIED monumentReason=NONE monumentCandidate=2,3 spawnOverrideEntries=1
[stronghold-test] PASS
"""
        parsed = parse_log(log)
        self.assertEqual(["forest", "ocean"], parsed["families"])
        self.assertEqual(1, parsed["crossing_starts"])
        self.assertEqual("SATISFIED", parsed["monument_status"])

    def test_aggregate_requires_both_policy_outcomes(self):
        with self.assertRaisesRegex(ValueError, "satisfied and unsatisfied"):
            validate_aggregate([record()])
        validate_aggregate([
            record(),
            record(monument_status="UNSATISFIED", monument_spawn_override_entries=0),
        ])

    def test_aggregate_reports_missing_family(self):
        with self.assertRaisesRegex(ValueError, "swamp"):
            validate_aggregate([
                record(families=["forest"], monument_status="UNSATISFIED"),
                record(),
            ][0:1])

    def test_reload_rejects_changed_world_identity(self):
        fresh = record(numeric_seed=1, circumference=16384, width=256,
                       biomes=["minecraft:plains"], chunks=128, structures=[],
                       crossing_structures=[], monument_reason="VALIDATED",
                       monument_candidate="1,2")
        with self.assertRaisesRegex(ValueError, "numeric_seed"):
            validate_reload(fresh, {**fresh, "numeric_seed": 2})
        validate_reload(fresh, dict(fresh))


if __name__ == "__main__":
    unittest.main()
