"""Pure tests for strict frozen-candidate compatibility declarations."""

from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "minecraft_qualification_ranges", ROOT / "scripts" / "minecraft_qualification_ranges.py"
)
assert SPEC and SPEC.loader
RANGES = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RANGES
SPEC.loader.exec_module(RANGES)


class MinecraftQualificationRangesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads(
            (ROOT / "config" / "minecraft-version-matrix.json").read_text(encoding="utf-8")
        )

    def test_accepts_only_the_reviewed_closed_range_forms(self) -> None:
        fabric = RANGES.parse_fabric_minecraft_range(">=26.1 <=26.1.2")
        neo_minecraft = RANGES.parse_neoforge_minecraft_range("[26.1,26.1.2]")
        neo_loader = RANGES.parse_neoforge_loader_range("[26.1.0.19-beta,26.1.2.87]")
        self.assertTrue(fabric.contains(RANGES.parse_minecraft_version("26.1.1")))
        self.assertFalse(fabric.contains(RANGES.parse_minecraft_version("26.1.3")))
        self.assertTrue(neo_minecraft.contains(RANGES.parse_minecraft_version("26.1.2")))
        self.assertFalse(neo_minecraft.contains(RANGES.parse_minecraft_version("26.1.3")))
        self.assertTrue(neo_loader.contains(RANGES.parse_neoforge_version("26.1.1.15-beta")))

    def test_rejects_open_malformed_or_extra_fabric_clauses(self) -> None:
        for value in (
            ">=26.1",
            "[26.1,26.1.2]",
            ">=26.1 <26.1.2",
            ">=26.1 <=26.1.2 <=26.1.3",
            ">=26.1  <=26.1.2",
            ">=26.1 <=26.1.2 ",
            ">=26.1 <=26.1.3",
            ">=26.1.2 <=26.1",
        ):
            with self.subTest(value=value):
                with self.assertRaises(RANGES.CompatibilityRangeError):
                    RANGES.parse_fabric_minecraft_range(value)

    def test_rejects_open_exclusive_malformed_or_reversed_neoforge_ranges(self) -> None:
        for parser, values in (
            (
                RANGES.parse_neoforge_minecraft_range,
                ("[26.1,)", "(26.1,26.1.2]", "[26.1,26.1.2)", "[26.1.2,26.1]", "[26.1,26.1.2],", "[26.1,26.1.3]"),
            ),
            (
                RANGES.parse_neoforge_loader_range,
                ("[26.1.0.19-beta,)", "[26.1.2.87,26.1.0.19-beta]", "[26.1.0.19-beta,26.1.2.87)", "[26.1.0.19-beta,26.1.2.88]"),
            ),
        ):
            for value in values:
                with self.subTest(parser=parser.__name__, value=value):
                    with self.assertRaises(RANGES.CompatibilityRangeError):
                        parser(value)

    def test_neoforge_comparison_preserves_prerelease_ordering(self) -> None:
        beta = RANGES.parse_neoforge_version("26.1.0.19-beta")
        release = RANGES.parse_neoforge_version("26.1.0.19")
        newer = RANGES.parse_neoforge_version("26.1.2.87")
        self.assertLess(beta, release)
        self.assertLess(release, newer)
        loader_range = RANGES.parse_neoforge_loader_range("[26.1.0.19-beta,26.1.2.87]")
        self.assertFalse(loader_range.contains(RANGES.parse_neoforge_version("26.1.2.88")))

    def test_every_manifest_cell_target_is_covered_without_claiming_support(self) -> None:
        coverage = RANGES.manifest_targets_covered(
            self.manifest["cells"],
            fabric_minecraft_range=">=26.1 <=26.1.2",
            neoforge_minecraft_range="[26.1,26.1.2]",
            neoforge_loader_range="[26.1.0.19-beta,26.1.2.87]",
        )
        self.assertEqual(("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"), coverage.fabric_minecraft_cells)
        self.assertEqual(("26.1-neoforge", "26.1.1-neoforge", "26.1.2-neoforge"), coverage.neoforge_minecraft_cells)
        self.assertEqual(coverage.neoforge_minecraft_cells, coverage.neoforge_loader_cells)

    def test_manifest_coverage_rejects_missing_dependency_or_target_outside_range(self) -> None:
        missing_dependency = copy.deepcopy(self.manifest["cells"])
        missing_dependency[1]["dependencies"] = []
        outside_target = copy.deepcopy(self.manifest["cells"])
        outside_target[0]["minecraft"]["version"] = "26.1.3"
        for cells in (missing_dependency, outside_target):
            with self.subTest(cells=cells[0]["id"]):
                with self.assertRaises(RANGES.CompatibilityRangeError):
                    RANGES.manifest_targets_covered(
                        cells,
                        fabric_minecraft_range=">=26.1 <=26.1.2",
                        neoforge_minecraft_range="[26.1,26.1.2]",
                        neoforge_loader_range="[26.1.0.19-beta,26.1.2.87]",
                    )


if __name__ == "__main__":
    unittest.main()
