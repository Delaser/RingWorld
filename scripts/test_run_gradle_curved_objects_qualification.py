#!/usr/bin/env python3
"""Pure tests for the pinned Gradle curved-object runner."""

from __future__ import annotations

from pathlib import Path
import shutil
import tempfile
import unittest

from minecraft_qualification_executor import ExecutedCommand
from minecraft_qualification_model import QualificationPaths, Verdict
from run_gradle_curved_objects_qualification import CAPTURES, PASS_MARKER, ROOT, _command, run
from run_minecraft_qualification import SourceProvenance, load_manifest


RUN_ID = "20260813T200000Z-abcdef123456"


class GradleCurvedObjectsQualificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "config").mkdir()
        shutil.copy2(ROOT / "config/minecraft-version-matrix.json", self.root / "config/minecraft-version-matrix.json")
        (self.root / "gradlew").write_text("#!/bin/sh\n")
        self.provenance = SourceProvenance("a" * 40, "main", "a" * 40,
            "https://github.com/Delaser/RingWorld.git", "b" * 64, "c" * 64, 'openjdk version "25"')

    def tearDown(self) -> None:
        self.temp.cleanup()

    def executor(self, command, paths, *, ordinal):
        root = paths.run_directory / "run-curved-object-capture"
        (root / "logs").mkdir(parents=True)
        (root / "screenshots").mkdir()
        world = root / "saves/Curved/level.dat"
        world.parent.mkdir(parents=True)
        world.write_bytes(b"world")
        (root / "logs/latest.log").write_text("[curved-object-capture] fixture ready\n" + PASS_MARKER + "\n")
        for name in CAPTURES:
            (root / "screenshots" / name).write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        stdout, stderr = paths.logs_directory / "01.stdout.log", paths.logs_directory / "01.stderr.log"
        stdout.write_text("ok"); stderr.write_text("")
        return ExecutedCommand("BUILD_AND_UNIT", Verdict.PASS, command.argv, 0,
            "2026-08-13T20:00:00+00:00", 1.0, str(stdout), str(stderr), None)

    def test_pass(self) -> None:
        result = run("26.1-fabric", repository_root=self.root, run_id_factory=lambda: RUN_ID,
            provenance_provider=lambda *_: self.provenance, command_executor=self.executor)
        self.assertEqual(result["verdict"], "PASS")
        self.assertEqual(len(result["captures"]), 2)

    def test_missing_capture_fails(self) -> None:
        def missing(command, paths, *, ordinal):
            result = self.executor(command, paths, ordinal=ordinal)
            (paths.run_directory / "run-curved-object-capture/screenshots" / CAPTURES[0]).unlink()
            return result
        result = run("26.1-fabric", repository_root=self.root, run_id_factory=lambda: RUN_ID,
            provenance_provider=lambda *_: self.provenance, command_executor=missing)
        self.assertEqual(result["verdict"], "FAIL")

    def test_neoforge_task(self) -> None:
        manifest = load_manifest(self.root / "config/minecraft-version-matrix.json")
        cell = next(item for item in manifest["cells"] if item["id"] == "26.1-neoforge")
        paths = QualificationPaths.from_cell(self.root, cell, RUN_ID)
        self.assertEqual(_command(cell, paths).argv[-1], ":neoforge:runCurvedObjectCaptureClient")

    def test_fresh_profile_guards(self) -> None:
        source = (ROOT / "src/client/java/dev/ringworld/client/CurvedObjectCaptureClient.java").read_text()
        neo = (ROOT / "neoforge/build.gradle").read_text()
        self.assertIn("RingMinecraftClientAccess.screen(client) instanceof TitleScreen", source)
        self.assertIn("pauseOnLostFocus = false", source)
        self.assertIn("earlyWindowControl = false", neo)

    def test_capture_readiness_is_chart_aware_and_timeout_is_diagnostic(self) -> None:
        source = (ROOT / "src/client/java/dev/ringworld/client/CurvedObjectCaptureClient.java").read_text()
        self.assertIn("atCapturePosition(client, FAR_CAPTURE_X)", source)
        self.assertIn("atCapturePosition(client, NEAR_CAPTURE_X)", source)
        self.assertIn("geometry.shortestCircumferenceDelta(", source)
        self.assertIn("geometry.nearestImageX(canonicalX, client.player.getX())", source)
        self.assertIn("capture timeout stage={} captureTicks={} settleTicks={}", source)
        self.assertIn("fixturePresent={} renderReady={}", source)
        self.assertIn("private static final int MAX_CAPTURE_TICKS = 1_200;", source)
        self.assertIn("client.levelRenderer.hasRenderedAllSections()", source)
        self.assertNotIn("client.player.getX() < 2.0", source)
        self.assertNotIn("client.player.getX() > 31.0", source)


if __name__ == "__main__":
    unittest.main()
