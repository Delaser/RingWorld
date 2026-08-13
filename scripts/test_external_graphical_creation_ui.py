#!/usr/bin/env python3

from dataclasses import replace
import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from external_graphical_creation_ui import (
    CAPTURES,
    PRISM_MACOS_ARCHIVE_SHA256,
    GraphicalCreationUiError,
    _extract_prism,
    _instance_pack,
    creation_ui_plan,
    execute_creation_ui,
)
from external_runtime_smoke import CandidateJar
from minecraft_qualification_executor import QualificationLock
from minecraft_qualification_model import QualificationPaths


class ExternalGraphicalCreationUiTest(unittest.TestCase):
    def cell(self, loader="fabric"):
        dependencies = [
            {"name": "Fabric Loader", "version": "0.19.3", "url": "https://example.invalid/loader", "checksum": {"algorithm": "sha256", "value": "1" * 64}},
            {"name": "Fabric API", "version": "0.145.1+26.1", "url": "https://example.invalid/api", "checksum": {"algorithm": "sha256", "value": "2" * 64}},
        ] if loader == "fabric" else [
            {"name": "NeoForge", "version": "26.1.0.19-beta", "url": "https://example.invalid/neo", "checksum": {"algorithm": "sha256", "value": "3" * 64}},
        ]
        return {
            "id": f"26.1-{loader}", "loader": loader, "minecraft": {"version": "26.1"},
            "dependencies": dependencies,
            "profile": {
                "server_port": 26101,
                "timeout_seconds": 1800,
                "evidence_directory": f"dist/qualification/ringworld/26.1/{loader}",
            },
        }

    def plan(self, root: Path, loader="fabric"):
        cell = self.cell(loader)
        paths = QualificationPaths.from_cell(root, cell, "20260813T120000Z-123456789abc")
        return creation_ui_plan(
            cell, paths, CandidateJar(root / "ring.jar", "a" * 64, loader, ">=26.1 <=26.1.2"),
            prism_archive=root / "prism.zip", java_executable=root / "java",
            source_provenance={"commit": "b" * 40},
        )

    def test_fabric_plan_is_contained_and_exact(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plan = self.plan(root)
            self.assertEqual("26.1", plan.minecraft_version)
            self.assertEqual("0.19.3", plan.loader_version)
            self.assertEqual("2" * 64, plan.fabric_api.checksum)
            self.assertTrue(str(plan.runtime_root).startswith(str((root / "dist/qualification").resolve())))
            self.assertEqual(PRISM_MACOS_ARCHIVE_SHA256, plan.prism_archive_sha256)

    def test_neoforge_pack_uses_exact_component(self):
        with tempfile.TemporaryDirectory() as temporary:
            plan = self.plan(Path(temporary), "neoforge")
            self.assertIsNone(plan.fabric_api)
            self.assertEqual(
                [{"uid": "net.minecraft", "version": "26.1", "important": True},
                 {"uid": "net.neoforged", "version": "26.1.0.19-beta"}],
                _instance_pack(plan)["components"],
            )

    def test_plan_rejects_loader_mismatch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cell = self.cell("fabric")
            paths = QualificationPaths.from_cell(root, cell, "20260813T120000Z-123456789abc")
            with self.assertRaises(GraphicalCreationUiError):
                creation_ui_plan(
                    cell, paths, CandidateJar(root / "ring.jar", "a" * 64, "neoforge", "[26.1,26.1.2]"),
                    prism_archive=root / "prism.zip", java_executable=root / "java", source_provenance={},
                )

    def test_extract_rejects_traversal(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "bad.zip"
            with zipfile.ZipFile(archive, "w") as target:
                target.writestr("../escape", b"no")
            with self.assertRaises(GraphicalCreationUiError):
                _extract_prism(archive, root / "output")

    def test_capture_contract_is_complete(self):
        self.assertEqual(13, len(CAPTURES))
        self.assertEqual(len(CAPTURES), len(set(CAPTURES)))
        self.assertEqual("creation-ui-13-footer-applied-scale4.png", CAPTURES[-1])

    def test_executor_accepts_only_complete_self_reported_fixture(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cell = self.cell("neoforge")
            paths = QualificationPaths.from_cell(root, cell, "20260813T120000Z-123456789abc")
            candidate = root / "candidate.jar"
            candidate.write_bytes(b"ringworld")
            archive = root / "prism.zip"
            with zipfile.ZipFile(archive, "w") as target:
                target.writestr("Prism Launcher.app/Contents/MacOS/prismlauncher", b"fake")
            java = root / "java"
            java.write_bytes(b"java")
            candidate_hash = hashlib.sha256(candidate.read_bytes()).hexdigest()
            plan = creation_ui_plan(
                cell, paths, CandidateJar(candidate, candidate_hash, "neoforge", "[26.1,26.1.2]"),
                prism_archive=archive, java_executable=java, source_provenance={"commit": "b" * 40},
            )
            plan = replace(plan, prism_archive_sha256=hashlib.sha256(archive.read_bytes()).hexdigest())

            def stage_runner(active_plan, executable):
                self.assertTrue(executable.is_file())
                launcher = active_plan.evidence_root / "prism-launcher.log"
                launcher.write_text("fake prism\n", encoding="utf-8")
                game_log = active_plan.game_root / "logs/latest.log"
                game_log.parent.mkdir(parents=True)
                game_log.write_text("[creation-ui-test] PASS: complete\n", encoding="utf-8")
                screenshots = active_plan.game_root / "screenshots"
                screenshots.mkdir()
                png = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + (1920).to_bytes(4, "big") + (1080).to_bytes(4, "big")
                for name in CAPTURES:
                    (screenshots / name).write_bytes(png)
                return 0, launcher, game_log

            with QualificationLock.acquire(paths.lock_path, paths.run_id) as held:
                result = execute_creation_ui(plan, paths, held_lock=held, stage_runner=stage_runner)
            self.assertEqual("PASS", result.verdict.value)
            self.assertEqual(13, len(result.captures))
            self.assertTrue(plan.terminal_json.is_file())


if __name__ == "__main__":
    unittest.main()
