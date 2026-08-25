#!/usr/bin/env python3
"""Static contracts for frozen two-phase raid qualification."""

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from run_gradle_raid_qualification import (  # noqa: E402
    GradleRaidError, _tasks, _verify_phase,
)


class GradleRaidQualificationTest(unittest.TestCase):
    def test_terminal_writer_uses_directory_and_markdown_contract(self) -> None:
        source = (ROOT / "scripts" / "run_gradle_raid_qualification.py").read_text(
            encoding="utf-8")
        self.assertIn("prepared.paths.evidence_directory / EVIDENCE_SUBDIRECTORY,", source)
        self.assertIn('stem="terminal"', source)
        self.assertNotIn('EVIDENCE_SUBDIRECTORY / "terminal.json"', source)

    def test_task_inventory_is_loader_symmetric(self) -> None:
        fabric, neoforge = _tasks("fabric"), _tasks("neoforge")
        self.assertEqual(set(fabric), set(neoforge))
        self.assertEqual(":prepareRaidSeamTestWorld", fabric["prepare"])
        self.assertEqual(":neoforge:prepareNeoForgeRaidSeamTestWorld", neoforge["prepare"])
        with self.assertRaises(GradleRaidError):
            _tasks("forge")

    def test_phase_verifier_requires_patch_clients_and_terminal_markers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prepared = SimpleNamespace(cell={
                "loader": "fabric", "minecraft": {"version": "26.1"},
            })
            paths = {}
            for role in ("server", "client-a", "client-b"):
                path = root / f"{role}.log"
                paths[role] = path
            paths["server"].write_text(
                "Starting minecraft server version 26.1\n"
                "[raid-seam] arm-save-ready=true saved=true center=BlockPos{x=1, y=120, z=0} "
                "raiders=1 bossbarA=true bossbarB=true\n", encoding="utf-8")
            for role in ("client-a", "client-b"):
                paths[role].write_text(
                    "Loading Minecraft 26.1 with Fabric Loader\nclient world fully loaded\n",
                    encoding="utf-8")
            logs = tuple({"role": role, "path": str(path)} for role, path in paths.items())
            self.assertIn("arm-save-ready=true", _verify_phase(prepared, "arm", logs))
            paths["server"].write_text("Starting minecraft server version 26.1\n[raid-seam] FAIL\n",
                                       encoding="utf-8")
            with self.assertRaisesRegex(GradleRaidError, "failure"):
                _verify_phase(prepared, "arm", logs)


if __name__ == "__main__":
    unittest.main()
