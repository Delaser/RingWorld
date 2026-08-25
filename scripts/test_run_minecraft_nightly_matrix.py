#!/usr/bin/env python3
"""Static contracts for the unattended nightly coordinator."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_minecraft_nightly_matrix import (  # noqa: E402
    FIXTURES, NightlyMatrixError, _child_argv, _selected_fixtures,
)


class MinecraftNightlyMatrixTest(unittest.TestCase):
    def arguments(self) -> argparse.Namespace:
        return argparse.Namespace(
            manifest="config/minecraft-version-matrix.json", quick_run_id="quick-run",
            gradle_dependency_cache="/cache", gradle_distribution_zip="/gradle.zip",
            gradle_loom_cache="/loom", production_world="/world",
        )

    def test_default_fixture_order_is_complete_and_duplicates_fail(self) -> None:
        self.assertEqual(FIXTURES, _selected_fixtures(None))
        with self.assertRaisesRegex(NightlyMatrixError, "duplicates"):
            _selected_fixtures(("raid", "raid"))

    def test_production_command_binds_every_input(self) -> None:
        command = _child_argv(ROOT, "26.1-fabric", "production-render",
                              self.arguments(), Path("/world"))
        joined = " ".join(command)
        for value in ("--quick-run-id quick-run", "--source-world /world",
                      "--gradle-dependency-cache /cache",
                      "--gradle-distribution-zip /gradle.zip",
                      "--gradle-loom-cache /loom"):
            self.assertIn(value, joined)

    def test_worldgen_command_does_not_receive_gradle_or_world_options(self) -> None:
        command = _child_argv(ROOT, "26.1-neoforge", "worldgen",
                              self.arguments(), Path("/world"))
        joined = " ".join(command)
        self.assertIn("--quick-run-id quick-run", joined)
        self.assertNotIn("--source-world", joined)
        self.assertNotIn("--gradle-dependency-cache", joined)


if __name__ == "__main__":
    unittest.main()
