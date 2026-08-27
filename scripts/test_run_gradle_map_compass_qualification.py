#!/usr/bin/env python3
"""Pure tests for the pinned Gradle map/compass runner."""

from __future__ import annotations

import json
from pathlib import Path
import shutil
import tempfile
import unittest

from minecraft_qualification_executor import ExecutedCommand
from minecraft_qualification_model import QualificationPaths, Verdict
from run_gradle_map_compass_qualification import (
    CAPTURE_PREFIXES, ORDERED_MARKERS, PASS_MARKER, ROOT, _command, run,
)
from run_minecraft_qualification import SourceProvenance, load_manifest


RUN_ID = "20260813T190000Z-abcdef123456"


class GradleMapCompassQualificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "config").mkdir()
        shutil.copy2(ROOT / "config/minecraft-version-matrix.json", self.root / "config/minecraft-version-matrix.json")
        (self.root / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")
        self.provenance = SourceProvenance(
            "a" * 40, "main", "a" * 40, "https://github.com/Delaser/RingWorld.git",
            "b" * 64, "c" * 64, 'openjdk version "25"',
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def executor(self, command, paths, *, ordinal):
        self.assertEqual(ordinal, 1)
        run_root = paths.run_directory / "run-map-compass-capture"
        (run_root / "logs").mkdir(parents=True)
        (run_root / "screenshots").mkdir()
        world = run_root / "saves/Map Compass/level.dat"
        world.parent.mkdir(parents=True)
        world.write_bytes(b"world")
        neo = command.argv[-1].startswith(":neoforge:")
        ack = ("RingWorld settings acknowledged by MapCompassTester on NeoForge: format 3"
               if neo else "RingWorld settings acknowledged by MapCompassTester: 2048x416, format 3")
        (run_root / "logs/latest.log").write_text(
            "\n".join((ack, ORDERED_MARKERS[0], ORDERED_MARKERS[1], ack,
                       ORDERED_MARKERS[2], PASS_MARKER + " passed")) + "\n", encoding="utf-8")
        for prefix in CAPTURE_PREFIXES:
            (run_root / "screenshots" / f"{prefix}.png").write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        stdout, stderr = paths.logs_directory / "01.stdout.log", paths.logs_directory / "01.stderr.log"
        stdout.write_text("ok", encoding="utf-8")
        stderr.write_text("", encoding="utf-8")
        return ExecutedCommand("BUILD_AND_UNIT", Verdict.PASS, command.argv, 0,
                               "2026-08-13T19:00:00+00:00", 1.0,
                               str(stdout), str(stderr), None)

    def test_pass_records_contract(self) -> None:
        result = run("26.1-fabric", repository_root=self.root,
                     run_id_factory=lambda: RUN_ID,
                     provenance_provider=lambda *_: self.provenance,
                     command_executor=self.executor)
        self.assertEqual(result["verdict"], "PASS")
        self.assertEqual(len(result["captures"]), 8)
        self.assertTrue(result["claims"]["normal_disconnect_reopen_and_state_clear"])
        terminal = (self.root / "dist/qualification/ringworld/26.1/fabric" / RUN_ID /
                    "26.1-fabric/evidence/nightly/08-map-compass-reconnect/terminal.json")
        self.assertEqual(json.loads(terminal.read_text())["fixture"], "map-compass-reconnect")

    def test_missing_second_ack_fails(self) -> None:
        def missing(command, paths, *, ordinal):
            result = self.executor(command, paths, ordinal=ordinal)
            log = paths.run_directory / "run-map-compass-capture/logs/latest.log"
            text = log.read_text()
            ack = "RingWorld settings acknowledged by MapCompassTester: 2048x416, format 3\n"
            log.write_text(text.replace(ack, "", 1))
            return result
        result = run("26.1-fabric", repository_root=self.root,
                     run_id_factory=lambda: RUN_ID,
                     provenance_provider=lambda *_: self.provenance,
                     command_executor=missing)
        self.assertEqual(result["verdict"], "FAIL")

    def test_neoforge_task_and_cache(self) -> None:
        manifest = load_manifest(self.root / "config/minecraft-version-matrix.json")
        cell = next(item for item in manifest["cells"] if item["id"] == "26.1-neoforge")
        paths = QualificationPaths.from_cell(self.root, cell, RUN_ID)
        cache = self.root.parent / "cache"
        command = _command(cell, paths, cache)
        self.assertEqual(command.argv[-1], ":neoforge:runMapCompassCaptureClient")
        self.assertIn(("GRADLE_RO_DEP_CACHE", str(cache)), command.environment)

    def test_fixture_startup_is_fresh_profile_safe(self) -> None:
        source = (ROOT / "src/client/java/dev/ringworld/client/RingMapCompassCaptureClient.java").read_text()
        root_build = (ROOT / "build.gradle").read_text()
        neo_build = (ROOT / "neoforge/build.gradle").read_text()
        self.assertIn("RingMinecraftClientAccess.screen(client) instanceof TitleScreen", source)
        self.assertIn("client.options.pauseOnLostFocus = false", source)
        self.assertIn("onboardAccessibility:false", root_build)
        self.assertIn("onboardAccessibility:false", neo_build)
        self.assertIn("earlyWindowControl = false", neo_build)


if __name__ == "__main__":
    unittest.main()
