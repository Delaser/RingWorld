#!/usr/bin/env python3
"""Static and filesystem contracts for frozen multiplayer qualification."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from minecraft_qualification_model import QualificationPaths  # noqa: E402
from run_gradle_multiplayer_qualification import (  # noqa: E402
    CAPTURES, GradleMultiplayerError, _base_argv, _configure_rcon, _tasks,
    _post_prepare_settle, _stage_loom_seed, _validated_loom_seed, _verify_fixture,
    _verify_installed_candidates, _wait_for_clients,
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

    def test_post_prepare_settle_is_bounded_and_chunked(self) -> None:
        intervals: list[int] = []
        _post_prepare_settle(12, sleeper=intervals.append)
        self.assertEqual([5, 5, 2], intervals)
        with self.assertRaisesRegex(GradleMultiplayerError, "bounded"):
            _post_prepare_settle(601, sleeper=intervals.append)

    def test_concurrent_game_processes_have_bounded_heaps_on_both_loaders(self) -> None:
        fabric = (ROOT / "build.gradle").read_text(encoding="utf-8")
        neoforge = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        self.assertEqual(7, fabric.count('vmArg "-Xmx2g"'))
        self.assertEqual(7, neoforge.count("jvmArgument '-Xmx2g'"))

    def test_client_wait_fails_as_soon_as_server_exits(self) -> None:
        server = SimpleNamespace(returncode=0, poll=lambda: 0)
        client = SimpleNamespace(returncode=None, poll=lambda: None)
        with self.assertRaisesRegex(GradleMultiplayerError, "server exited 0"):
            _wait_for_clients(server, {"client-a": client, "client-b": client}, 60,
                              sleeper=lambda _seconds: None)

    def test_client_wait_accepts_two_clean_client_exits(self) -> None:
        server = SimpleNamespace(returncode=None, poll=lambda: None)
        client = SimpleNamespace(returncode=0, poll=lambda: 0)
        _wait_for_clients(server, {"client-a": client, "client-b": client}, 60,
                          sleeper=lambda _seconds: None)

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

    def test_loom_seed_stages_only_reviewed_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source_root = root / "seed"
            sources = [source_root / "mojang_versions_manifest.json"]
            sources.extend(source_root / "26.1" / name for name in (
                "mojang_minecraft_info.json", "minecraft-client.jar", "minecraft-server.jar"))
            sources.append(source_root / "assets/indexes/26.1-30.json")
            sources.append(source_root / "assets/objects/ab/abcdef")
            for source in sources:
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_bytes(source.name.encode("utf-8"))
            home = root / "cell-home"
            _stage_loom_seed(sources, home, "26.1")
            self.assertEqual(
                b"minecraft-client.jar",
                (home / "caches/fabric-loom/26.1/minecraft-client.jar").read_bytes(),
            )
            self.assertEqual(
                b"abcdef", (home / "caches/fabric-loom/assets/objects/ab/abcdef").read_bytes())

    def test_loom_seed_is_bound_to_mojang_metadata_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory).resolve()
            version = cache / "26.1"
            version.mkdir()
            client, server = version / "minecraft-client.jar", version / "minecraft-server.jar"
            client.write_bytes(b"client")
            server.write_bytes(b"server")
            metadata_data = {"downloads": {
                "client": {"size": 6, "sha1": hashlib.sha1(b"client").hexdigest()},
                "server": {"size": 6, "sha1": hashlib.sha1(b"server").hexdigest()},
            }, "assetIndex": {"id": "30"}}
            asset_object = cache / "assets/objects/ab/abcdef12345678901234567890123456789012"
            asset_object.parent.mkdir(parents=True)
            asset_object.write_bytes(b"asset")
            asset_hash = hashlib.sha1(b"asset").hexdigest()
            correct_object = cache / "assets/objects" / asset_hash[:2] / asset_hash
            correct_object.parent.mkdir(parents=True, exist_ok=True)
            asset_object.rename(correct_object)
            asset_index = cache / "assets/indexes/26.1-30.json"
            asset_index.parent.mkdir(parents=True)
            asset_index.write_text(json.dumps({"objects": {
                "test": {"hash": asset_hash, "size": 5},
            }}), encoding="utf-8")
            metadata_data["assetIndex"].update({
                "size": asset_index.stat().st_size,
                "sha1": hashlib.sha1(asset_index.read_bytes()).hexdigest(),
            })
            metadata = version / "mojang_minecraft_info.json"
            metadata.write_text(json.dumps(metadata_data), encoding="utf-8")
            manifest = cache / "mojang_versions_manifest.json"
            manifest.write_text(json.dumps({"versions": [{
                "id": "26.1", "sha1": hashlib.sha1(metadata.read_bytes()).hexdigest(),
            }]}), encoding="utf-8")
            with patch("run_gradle_multiplayer_qualification.validate_gradle_dependency_cache",
                       return_value=cache):
                self.assertEqual(6, len(_validated_loom_seed(cache, ROOT, "26.1")))
                client.write_bytes(b"tamper")
                with self.assertRaisesRegex(GradleMultiplayerError, "identity"):
                    _validated_loom_seed(cache, ROOT, "26.1")

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

    def test_neoforge_fixture_uses_exact_mod_list_patch_marker(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            prepared = self._prepared(Path(directory), loader="neoforge")
            runtime = prepared.paths.run_directory / "run-multiplayer"
            server = runtime / "server/logs/latest.log"
            server.parent.mkdir(parents=True)
            server.write_text(
                "Starting minecraft server version 26.1\n" + "\n".join((
                    "[multiplayer] full scenario result=true",
                    "[multiplayer-extended] ordinary Nether portal wait result=true",
                    "[multiplayer-extended] multi-lap Nether portal routing result=true",
                    "[multiplayer-extended] seam thunder/lightning result=true",
                    "[multiplayer] bidirectional seam placement result=true",
                    "[multiplayer-extended] alias block-entity recovery policy result=true",
                )), encoding="utf-8")
            for role, letter in (("client-a", "A"), ("client-b", "B")):
                log = runtime / role / "logs/latest.log"
                log.parent.mkdir(parents=True)
                log.write_text(
                    f"Minecraft 26.1 (minecraft)\n[multiplayer:{letter}] client world fully loaded\n"
                    f"[multiplayer:{letter}] local scenario result=true; stopping client\n",
                    encoding="utf-8")
            for relative in CAPTURES:
                capture = runtime / relative
                capture.parent.mkdir(parents=True, exist_ok=True)
                capture.write_bytes(b"\x89PNG\r\n\x1a\n" + b"0" * 128)
            self.assertEqual(3, len(_verify_fixture(prepared)))


if __name__ == "__main__":
    unittest.main()
