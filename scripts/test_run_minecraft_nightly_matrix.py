#!/usr/bin/env python3
"""Static contracts for the unattended nightly coordinator."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_minecraft_nightly_matrix import (  # noqa: E402
    FIXTURES, NightlyMatrixError, _child_argv, _selected_fixtures,
    _verify_terminals,
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
        for value in ("--quick-run-id quick-run",
                      "--gradle-dependency-cache /cache",
                      "--gradle-distribution-zip /gradle.zip",
                      "--gradle-loom-cache /loom"):
            self.assertIn(value, joined)
        source_index = command.index("--source-world")
        self.assertEqual(str(Path("/world")), command[source_index + 1])

    def test_worldgen_command_does_not_receive_gradle_or_world_options(self) -> None:
        command = _child_argv(ROOT, "26.1-neoforge", "worldgen",
                              self.arguments(), Path("/world"))
        joined = " ".join(command)
        self.assertIn("--quick-run-id quick-run", joined)
        self.assertNotIn("--source-world", joined)
        self.assertNotIn("--gradle-dependency-cache", joined)

    def test_terminal_binding_rejects_wrong_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = {"id": "26.1-fabric", "loader": "fabric",
                    "minecraft": {"version": "26.1"}}
            run_id = "20260826T000000Z-0123456789ab"
            path = (root / "dist/qualification/ringworld/26.1/fabric" / run_id
                    / "26.1-fabric/evidence/nightly/06-seam-gameplay-multiplayer/terminal.json")
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                "verdict": "PASS", "cell": "26.1-fabric",
                "source": {"commit": "abc"},
                "frozen_candidate": {"sha256": "candidate"},
                "quick_evidence": {"sha256": "quick"},
            }), encoding="utf-8")
            payload = {"run_id": run_id}
            records = _verify_terminals(root, cell, "multiplayer", payload,
                                        "abc", "candidate", "quick")
            self.assertEqual(1, len(records))
            with self.assertRaisesRegex(NightlyMatrixError, "candidate"):
                _verify_terminals(root, cell, "multiplayer", payload,
                                  "abc", "wrong", "quick")


if __name__ == "__main__":
    unittest.main()
