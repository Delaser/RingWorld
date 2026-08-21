#!/usr/bin/env python3
"""Pure tests for the pinned Gradle graphical creation-UI runner."""

from __future__ import annotations

import json
from pathlib import Path
import shutil
import tempfile
import unittest

from minecraft_qualification_executor import ExecutedCommand
from minecraft_qualification_model import Verdict
from minecraft_qualification_model import QualificationPaths
from run_gradle_creation_ui_qualification import CAPTURE_PREFIXES, PASS_MARKER, ROOT, _command, run
from run_minecraft_qualification import SourceProvenance, load_manifest


RUN_ID = "20260813T120000Z-123456789abc"


class GradleCreationUiQualificationTest(unittest.TestCase):
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
        self.assertIn("-Pfabric_api_version=0.145.1+26.1", command.argv)
        project_cache_index = command.argv.index("--project-cache-dir") + 1
        self.assertEqual(Path(command.argv[project_cache_index]), paths.cache_directory / "gradle-project")
        self.assertEqual(command.argv[-1], ":runCreationUiClient")
        run_root = paths.run_directory / "run-creation-ui"
        (run_root / "logs").mkdir(parents=True)
        (run_root / "screenshots").mkdir()
        (run_root / "logs/latest.log").write_text(PASS_MARKER + "\n", encoding="utf-8")
        for prefix in CAPTURE_PREFIXES:
            (run_root / "screenshots" / f"{prefix}test.png").write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        stdout = paths.logs_directory / "01.stdout.log"
        stderr = paths.logs_directory / "01.stderr.log"
        stdout.write_text("ok", encoding="utf-8")
        stderr.write_text("", encoding="utf-8")
        return ExecutedCommand(
            "BUILD_AND_UNIT", Verdict.PASS, command.argv, 0,
            "2026-08-13T12:00:00+00:00", 1.0, str(stdout), str(stderr), None,
        )

    def test_pass_records_exact_graphical_contract(self) -> None:
        result = run(
            "26.1-fabric", repository_root=self.root, run_id_factory=lambda: RUN_ID,
            provenance_provider=lambda *_: self.provenance, command_executor=self.executor,
        )
        self.assertEqual(result["verdict"], "PASS")
        self.assertEqual(len(result["captures"]), 13)
        self.assertFalse(result["claims"]["production_launcher"])
        terminal = (
            self.root / "dist/qualification/ringworld/26.1/fabric" / RUN_ID /
            "26.1-fabric/evidence/nightly/01-creation-settings-ui/terminal.json"
        )
        self.assertEqual(json.loads(terminal.read_text(encoding="utf-8"))["cell"], "26.1-fabric")

    def test_missing_capture_fails(self) -> None:
        def incomplete(command, paths, *, ordinal):
            result = self.executor(command, paths, ordinal=ordinal)
            next((paths.run_directory / "run-creation-ui/screenshots").glob(CAPTURE_PREFIXES[0] + "*.png")).unlink()
            return result
        result = run(
            "26.1-fabric", repository_root=self.root, run_id_factory=lambda: RUN_ID,
            provenance_provider=lambda *_: self.provenance, command_executor=incomplete,
        )
        self.assertEqual(result["verdict"], "FAIL")

    def test_validated_dependency_cache_reaches_command_environment(self) -> None:
        manifest = load_manifest(self.root / "config/minecraft-version-matrix.json")
        cell = next(item for item in manifest["cells"] if item["id"] == "26.1-fabric")
        paths = QualificationPaths.from_cell(self.root, cell, RUN_ID)
        cache = self.root.parent / "reviewed-read-only-cache"
        command = _command(cell, paths, cache)
        self.assertIn(("GRADLE_RO_DEP_CACHE", str(cache)), command.environment)


if __name__ == "__main__":
    unittest.main()
