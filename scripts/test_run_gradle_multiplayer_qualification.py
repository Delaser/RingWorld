#!/usr/bin/env python3
"""Static and filesystem contracts for frozen multiplayer qualification."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from minecraft_qualification_model import QualificationPaths  # noqa: E402
from run_gradle_multiplayer_qualification import (  # noqa: E402
    CAPTURES, GradleMultiplayerError, _base_argv, _configure_rcon, _tasks,
    _verify_fixture, _verify_installed_candidates,
)


class GradleMultiplayerQualificationTest(unittest.TestCase):
    def _prepared(self, root: Path, loader: str = "fabric", minecraft: str = "26.1"):
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cell = next(item for item in manifest["cells"]
                    if item["loader"] == loader and item["minecraft"]["version"] == minecraft)
        paths = QualificationPaths.from_cell(root, cell, "20260825T120000Z-abcdef123456")
        candidate = root / "dist/qualification/input/ringworld-qualification.jar"
        candidate.parent.mkdir(parents=True)
        candidate.write_bytes(b"frozen")
        return SimpleNamespace(
            paths=paths, cell=cell,
            candidate=SimpleNamespace(path=candidate, sha256=hashlib.sha256(b"frozen").hexdigest()),
        )

    def test_tasks_are_loader_symmetric(self) -> None:
        fabric, neoforge = _tasks("fabric"), _tasks("neoforge")
        self.assertEqual(set(fabric), set(neoforge))
        self.assertIn(":runMultiplayerServer", fabric["server"])
        self.assertIn(":neoforge:runMultiplayerServer", neoforge["server"])
        with self.assertRaises(GradleMultiplayerError):
            _tasks("forge")

    def test_command_binds_cell_candidate_and_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            prepared = self._prepared(Path(directory))
            argv = _base_argv(prepared)
            self.assertIn("-Pminecraft_version=26.1", argv)
            self.assertIn("-Pfabric_api_version=0.145.1+26.1", argv)
            self.assertIn(f"-PringQualificationFrozenCandidateJar={prepared.candidate.path}", argv)
            self.assertIn(f"-PringQualificationFrozenCandidateSha256={prepared.candidate.sha256}", argv)

    def test_installed_candidate_requires_one_exact_jar_per_role(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            prepared = self._prepared(Path(directory))
            runtime = prepared.paths.run_directory / "run-multiplayer"
            for role in ("server", "client-a", "client-b"):
                jar = runtime / role / "mods/ringworld-qualification.jar"
                jar.parent.mkdir(parents=True)
                jar.write_bytes(b"frozen")
            records = _verify_installed_candidates(prepared)
            self.assertEqual(3, len(records))
            extra = runtime / "client-a/mods/checkout.jar"
            extra.write_bytes(b"source leak")
            with self.assertRaisesRegex(GradleMultiplayerError, "only"):
                _verify_installed_candidates(prepared)

    def test_disposable_rcon_configuration_preserves_other_properties(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            server = Path(directory)
            properties = server / "server.properties"
            properties.write_text("server-port=26101\nenable-rcon=false\n", encoding="utf-8")
            self.assertEqual(properties, _configure_rcon(server, 27101, "test-password"))
            text = properties.read_text(encoding="utf-8")
            self.assertIn("server-port=26101\n", text)
            self.assertIn("enable-rcon=true\n", text)
            self.assertIn("rcon.port=27101\n", text)
            self.assertIn("rcon.password=test-password\n", text)
            with self.assertRaisesRegex(GradleMultiplayerError, "port"):
                _configure_rcon(server, 70000, "test-password")

    def test_fixture_verifier_binds_patch_markers_and_pngs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            prepared = self._prepared(Path(directory))
            runtime = prepared.paths.run_directory / "run-multiplayer"
            server = runtime / "server/logs/latest.log"
            server.parent.mkdir(parents=True)
            server.write_text(
                "Starting minecraft server version 26.1\n"
                "[multiplayer] full scenario result=true\n"
                "[multiplayer-extended] ordinary Nether portal wait result=true\n"
                "[multiplayer-extended] multi-lap Nether portal routing result=true\n"
                "[multiplayer-extended] seam thunder/lightning result=true\n"
                "[multiplayer] bidirectional seam placement result=true\n"
                "[multiplayer-extended] alias block-entity recovery policy result=true\n",
                encoding="utf-8",
            )
            for role, letter in (("client-a", "A"), ("client-b", "B")):
                log = runtime / role / "logs/latest.log"
                log.parent.mkdir(parents=True)
                log.write_text(
                    f"Loading Minecraft 26.1 with Loader\n[multiplayer:{letter}] client world fully loaded\n"
                    f"[multiplayer:{letter}] local scenario result=true; stopping client\n",
                    encoding="utf-8",
                )
            for relative in CAPTURES:
                capture = runtime / relative
                capture.parent.mkdir(parents=True, exist_ok=True)
                capture.write_bytes(b"\x89PNG\r\n\x1a\n" + b"0" * 128)
            self.assertEqual(3, len(_verify_fixture(prepared)))
            server.write_text("Starting minecraft server version 26.1\n", encoding="utf-8")
            with self.assertRaisesRegex(GradleMultiplayerError, "markers"):
                _verify_fixture(prepared)


if __name__ == "__main__":
    unittest.main()
