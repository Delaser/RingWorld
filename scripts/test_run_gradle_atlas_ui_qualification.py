#!/usr/bin/env python3
"""Pure tests for the pinned Gradle Atlas UI runner."""

from __future__ import annotations

import json
from pathlib import Path
import shutil
import tempfile
import unittest

from minecraft_qualification_executor import ExecutedCommand
from minecraft_qualification_model import QualificationPaths, Verdict
from run_gradle_atlas_ui_qualification import CAPTURE_PREFIXES, PASS_MARKER, ROOT, _command, run
from run_minecraft_qualification import SourceProvenance, load_manifest


RUN_ID = "20260813T120000Z-abcdef123456"


class GradleAtlasUiQualificationTest(unittest.TestCase):
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
        self.assertIn("-Pminecraft_version=26.1", command.argv)
        self.assertEqual(command.argv[-1], ":runAtlasUiClient")
        cache_index = command.argv.index("--project-cache-dir") + 1
        self.assertEqual(Path(command.argv[cache_index]), paths.cache_directory / "gradle-project")
        run_root = paths.run_directory / "run-atlas-ui"
        (run_root / "logs").mkdir(parents=True)
        (run_root / "screenshots").mkdir()
        world = run_root / "saves" / "Atlas UI" / "level.dat"
        world.parent.mkdir(parents=True)
        world.write_bytes(b"world")
        (run_root / "logs/latest.log").write_text(PASS_MARKER + "\n", encoding="utf-8")
        for prefix in CAPTURE_PREFIXES:
            (run_root / "screenshots" / f"{prefix}test.png").write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        stdout, stderr = paths.logs_directory / "01.stdout.log", paths.logs_directory / "01.stderr.log"
        stdout.write_text("ok", encoding="utf-8")
        stderr.write_text("", encoding="utf-8")
        return ExecutedCommand(
            "BUILD_AND_UNIT", Verdict.PASS, command.argv, 0,
            "2026-08-13T12:00:00+00:00", 1.0, str(stdout), str(stderr), None,
        )

    def test_pass_records_atlas_contract(self) -> None:
        result = run(
            "26.1-fabric", repository_root=self.root, run_id_factory=lambda: RUN_ID,
            provenance_provider=lambda *_: self.provenance, command_executor=self.executor,
        )
        self.assertEqual(result["verdict"], "PASS")
        self.assertEqual(len(result["captures"]), 11)
        self.assertTrue(result["claims"]["revisioned_edit_verified"])
        terminal = (
            self.root / "dist/qualification/ringworld/26.1/fabric" / RUN_ID /
            "26.1-fabric/evidence/nightly/04-atlas-ui-revision/terminal.json"
        )
        self.assertEqual(json.loads(terminal.read_text(encoding="utf-8"))["cell"], "26.1-fabric")

    def test_missing_disposable_world_fails(self) -> None:
        def missing_world(command, paths, *, ordinal):
            result = self.executor(command, paths, ordinal=ordinal)
            next((paths.run_directory / "run-atlas-ui/saves").glob("**/level.dat")).unlink()
            return result
        result = run(
            "26.1-fabric", repository_root=self.root, run_id_factory=lambda: RUN_ID,
            provenance_provider=lambda *_: self.provenance, command_executor=missing_world,
        )
        self.assertEqual(result["verdict"], "FAIL")

    def test_neoforge_task_and_dependency_cache(self) -> None:
        manifest = load_manifest(self.root / "config/minecraft-version-matrix.json")
        cell = next(item for item in manifest["cells"] if item["id"] == "26.1-neoforge")
        paths = QualificationPaths.from_cell(self.root, cell, RUN_ID)
        cache = self.root.parent / "reviewed-read-only-cache"
        command = _command(cell, paths, cache)
        self.assertEqual(command.argv[-1], ":neoforge:runAtlasUiClient")
        self.assertIn(("GRADLE_RO_DEP_CACHE", str(cache)), command.environment)

    def test_both_loader_preparers_disable_first_run_onboarding(self) -> None:
        root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        neo_build = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        root_block = root_build[root_build.index('tasks.register("prepareAtlasUiRun")'):
                                root_build.index('tasks.named("runAtlasUiClient")')]
        neo_block = neo_build[neo_build.index("tasks.register('prepareNeoForgeAtlasUiRun')"):
                              neo_build.index("tasks.register('verifyNeoForgeAtlasUiClient')")]
        self.assertIn('onboardAccessibility:false', root_block)
        self.assertIn('onboardAccessibility:false', neo_block)
        self.assertIn('config.setProperty("widthBlocks", "128")', root_block)
        self.assertIn("config.setProperty('widthBlocks', '128')", neo_block)

    def test_both_loader_runs_supply_independent_build_identity_expectation(self) -> None:
        root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        neo_build = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        self.assertIn(
            '-Dringworld.atlasUiExpectedBuildLabel=${project.release_label} · ${project.mod_version}',
            root_build,
        )
        self.assertIn("'ringworld.atlasUiExpectedBuildLabel'", neo_build)
        self.assertIn('${rootProject.release_label} · ${rootProject.mod_version}', neo_build)

    def test_unattended_fixture_keeps_integrated_server_ticking(self) -> None:
        source = (ROOT / "src/client/java/dev/ringworld/client/AtlasPregenerationUiTestClient.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("client.options.pauseOnLostFocus = false;", source)


if __name__ == "__main__":
    unittest.main()
