#!/usr/bin/env python3
"""No-network tests for the external dedicated-runtime executor."""

from __future__ import annotations

from dataclasses import replace
import hashlib
import importlib.util
import json
from pathlib import Path
import socket
import sys
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


MODEL = load("minecraft_qualification_model", SCRIPTS / "minecraft_qualification_model.py")
SMOKE = load("external_runtime_smoke", SCRIPTS / "external_runtime_smoke.py")
EXECUTOR = load("external_runtime_executor", SCRIPTS / "external_runtime_executor.py")
from minecraft_qualification_executor import LockError  # noqa: E402


class Response:
    def __init__(self, url: str, body: bytes, final_url: str | None = None) -> None:
        self.url, self.body, self.final_url = url, body, final_url or url
        self.offset = 0

    def read(self, count: int = -1) -> bytes:
        if count < 0:
            count = len(self.body) - self.offset
        value = self.body[self.offset:self.offset + count]
        self.offset += len(value)
        return value

    def geturl(self) -> str:
        return self.final_url

    def close(self) -> None:
        pass


def sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


class ExternalRuntimeExecutorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        self.cells = {cell["id"]: cell for cell in self.manifest["cells"]}

    def plan(self, temporary: Path, cell_id: str):
        cell = self.cells[cell_id]
        paths = MODEL.QualificationPaths.from_cell(temporary, cell, "20260812T120000Z-0123456789ab")
        paths.build_directory.mkdir(parents=True)
        candidate = paths.build_directory / "ringworld-candidate.jar"
        candidate.write_bytes(b"candidate-" + cell_id.encode("ascii"))
        candidate_entry = SMOKE.CandidateJar(candidate, sha256(candidate.read_bytes()), cell["loader"])
        planned = SMOKE.external_runtime_smoke_plan(cell, candidate_entry, paths)
        minecraft_server_body = b"fake-mojang-server-" + cell_id.encode("ascii")
        minecraft_server = replace(
            planned.minecraft_server,
            url="https://test.invalid/mojang-server.jar",
            checksum=hashlib.sha1(minecraft_server_body).hexdigest(),
        )
        installer_body = b"fake-installer-" + cell_id.encode("ascii")
        downloads = []
        mods = list(planned.mods)
        for index, download in enumerate(planned.downloads):
            body = installer_body if index == 0 else b"fake-fabric-api-" + cell_id.encode("ascii")
            replacement = SMOKE.RuntimeDownload(download.name, f"https://test.invalid/{index}.jar", "sha256", sha256(body), download.destination)
            downloads.append(replacement)
            if index > 0:
                mods[1] = replace(mods[1], source=replacement.destination, sha256=replacement.checksum)
        bodies = {minecraft_server.url: minecraft_server_body}
        bodies.update({item.url: (installer_body if index == 0 else b"fake-fabric-api-" + cell_id.encode("ascii")) for index, item in enumerate(downloads)})
        return paths, replace(planned, minecraft_server=minecraft_server, downloads=tuple(downloads), mods=tuple(mods)), bodies

    @staticmethod
    def opener(bodies):
        def open_url(url: str, *, timeout: int):
            assert timeout > 0
            return Response(url, bodies[url])
        return open_url

    @staticmethod
    def launcher_script(root: Path) -> Path:
        script = root / "fake-server.py"
        script.write_text(
            "import sys\n"
            "print('Fabric Loader bootstrapped', flush=True)\n"
            "print('RingWorld bootstrap settings: width=416, circumference=2048, wallHeight=160', flush=True)\n"
            "print('pregenerateTerrainAtlas=false', flush=True)\n"
            "print('Done (0.1s)! For help, type \\\"help\\\"', flush=True)\n"
            "sys.stdin.readline()\n"
            "print('Saving worlds', flush=True)\n"
            "print('Stopping server', flush=True)\n",
            encoding="utf-8",
        )
        return script

    @staticmethod
    def installer_for(plan, *, extra_ringworld: bool = False, wrong_mojang_server: bool = False):
        def run(record, paths, *, ordinal: int):
            assert ordinal == 1
            plan.layout.root.mkdir(parents=True)
            if plan.loader == "fabric":
                assert plan.layout.fabric_server_jar is not None
                plan.layout.fabric_server_jar.write_bytes(b"fake fabric launcher")
            else:
                assert plan.layout.neoforge_run_script is not None
                assert plan.layout.neoforge_user_jvm_args is not None
                plan.layout.neoforge_run_script.write_text("#!/bin/sh\n", encoding="utf-8")
                plan.layout.neoforge_run_script.chmod(0o700)
                plan.layout.neoforge_user_jvm_args.write_text("-Xmx1G\n", encoding="utf-8")
            server_body = b"wrong-mojang-server" if wrong_mojang_server else b"fake-mojang-server-" + plan.cell_id.encode("ascii")
            (plan.layout.root / "server.jar").write_bytes(server_body)
            if extra_ringworld:
                plan.layout.mods_directory.mkdir()
                (plan.layout.mods_directory / "ringworld-old.jar").write_bytes(b"wrong")
            return EXECUTOR.ExecutedCommand("DEDICATED_SMOKE", MODEL.Verdict.PASS, record.argv, 0, "now", 0.0, "", "")
        return run

    def _with_fake_launch(self, plan: object, root: Path):
        script = self.launcher_script(root)
        return replace(plan, launch=replace(plan.launch, argv=(sys.executable, str(script)), timeout_seconds=5))

    @staticmethod
    def successful_server(plan, paths, ledger):
        log = paths.logs_directory / "fake-server.log"
        log.parent.mkdir(parents=True, exist_ok=True)
        log.write_text("fake clean server\n", encoding="utf-8")
        for marker in ("runtime-start", "loader-bootstrap", "ringworld-bootstrap", "atlas-disabled", "server-ready", "stop-sent", "server-stop", "world-save", "clean-stop", "runtime-exit"):
            ledger.add(marker)
        return MODEL.Verdict.PASS, None, ("atlas-disabled", "loader-bootstrap", "ringworld-bootstrap", "server-ready"), "Stopping server", 0, str(log), ledger.events()

    def test_fabric_downloads_installs_copies_and_stops_cleanly(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-fabric")
            result = EXECUTOR.execute_external_runtime_smoke(
                plan, paths, paths.run_id, opener=self.opener(bodies), command_executor=self.installer_for(plan),
                server_runner=self.successful_server,
            )
            self.assertEqual(MODEL.Verdict.PASS, result.verdict)
            self.assertEqual({"loader-bootstrap", "ringworld-bootstrap", "atlas-disabled", "server-ready"}, set(result.observed_markers))
            self.assertEqual("Stopping server", result.stop_marker)
            self.assertEqual(0, result.server_return_code)
            self.assertEqual(["RingWorld", "Fabric API"], [item.name for item in result.mods])
            self.assertTrue(plan.layout.eula_path.read_text(encoding="utf-8").startswith("eula=true"))
            self.assertTrue(Path(result.server_log).is_file())
            self.assertTrue((paths.evidence_directory / "external-runtime-smoke.json").is_file())
            self.assertTrue((paths.evidence_directory / "external-runtime-smoke.md").is_file())
            assert result.runtime_identity is not None
            self.assertEqual(plan.minecraft_server.checksum, result.runtime_identity.minecraft_server_expected)
            self.assertEqual(plan.minecraft_server.checksum, result.runtime_identity.minecraft_server_actual)
            self.assertEqual(
                ("installer-start", "installer-complete", "runtime-start", "loader-bootstrap", "ringworld-bootstrap", "atlas-disabled", "server-ready", "stop-sent", "server-stop", "world-save", "clean-stop", "runtime-exit"),
                tuple(item.name for item in result.marker_ledger),
            )

    def test_runner_held_exact_lock_runs_without_reacquiring(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-fabric")
            with EXECUTOR.QualificationLock.acquire(paths.lock_path, paths.run_id) as held:
                with patch.object(EXECUTOR.QualificationLock, "acquire", side_effect=AssertionError("must not reacquire")):
                    result = EXECUTOR.execute_external_runtime_smoke(
                        plan, paths, paths.run_id, opener=self.opener(bodies),
                        command_executor=self.installer_for(plan), server_runner=self.successful_server,
                        held_lock=held,
                    )
            self.assertEqual(MODEL.Verdict.PASS, result.verdict)

    def test_borrowed_lock_rejects_unheld_wrong_path_and_wrong_run_before_network(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-fabric")
            unheld = EXECUTOR.QualificationLock.acquire(paths.lock_path, paths.run_id)
            unheld.release()
            with self.assertRaises(LockError):
                EXECUTOR.execute_external_runtime_smoke(
                    plan, paths, paths.run_id,
                    opener=lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("network must not run")),
                    held_lock=unheld,
                )
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-fabric")
            with EXECUTOR.QualificationLock.acquire(paths.lock_path.with_name("wrong.lock"), paths.run_id) as wrong_path:
                with self.assertRaises(LockError):
                    EXECUTOR.execute_external_runtime_smoke(
                        plan, paths, paths.run_id,
                        opener=lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("network must not run")),
                        held_lock=wrong_path,
                    )
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-fabric")
            other_run = "20260812T120001Z-0123456789ab"
            with EXECUTOR.QualificationLock.acquire(paths.lock_path, other_run) as wrong_run:
                with self.assertRaises(LockError):
                    EXECUTOR.execute_external_runtime_smoke(
                        plan, paths, paths.run_id,
                        opener=lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("network must not run")),
                        held_lock=wrong_run,
                    )

    def test_neoforge_generated_contract_and_clean_stop(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-neoforge")
            result = EXECUTOR.execute_external_runtime_smoke(
                plan, paths, paths.run_id, opener=self.opener(bodies), command_executor=self.installer_for(plan),
                server_runner=self.successful_server,
            )
            self.assertEqual(MODEL.Verdict.PASS, result.verdict)
            self.assertTrue(result.launcher_verified)
            self.assertEqual(["RingWorld"], [item.name for item in result.mods])

    def test_redirect_or_checksum_failure_is_fail_closed_before_installer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-fabric")
            calls = []
            def redirect(url: str, *, timeout: int):
                return Response(url, bodies[url], "https://evil.invalid/installer.jar")
            def never(*args, **kwargs):
                calls.append(True)
                raise AssertionError("installer must not run")
            with self.assertRaises(EXECUTOR.ExternalRuntimeExecutionError):
                EXECUTOR.execute_external_runtime_smoke(plan, paths, paths.run_id, opener=redirect, command_executor=never)
            self.assertEqual([], calls)
            self.assertFalse(plan.layout.root.exists())

    def test_query_url_is_rejected_before_network_access(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-fabric")
            changed = replace(plan.minecraft_server, url="https://test.invalid/server.jar?mirror=1")
            plan = replace(plan, minecraft_server=changed)
            with self.assertRaises(EXECUTOR.ExternalRuntimeExecutionError):
                EXECUTOR.execute_external_runtime_smoke(
                    plan, paths, paths.run_id,
                    opener=lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("network must not run")),
                    command_executor=lambda *args, **kwargs: None,
                )

    def test_pinned_cache_is_rechecked_before_reuse(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-fabric")
            download = plan.downloads[0]
            first = EXECUTOR.fetch_pinned_https(download, paths, opener=self.opener(bodies))
            second = EXECUTOR.fetch_pinned_https(
                download, paths,
                opener=lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("cache must avoid network")),
            )
            self.assertFalse(first.reused_cache)
            self.assertTrue(second.reused_cache)
            download.destination.write_bytes(b"corrupt")
            with self.assertRaises(EXECUTOR.ExternalRuntimeExecutionError):
                EXECUTOR.fetch_pinned_https(download, paths, opener=self.opener(bodies))

    def test_extra_ringworld_jar_is_rejected_after_copy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-fabric")
            with self.assertRaises(EXECUTOR.ExternalRuntimeExecutionError):
                EXECUTOR.execute_external_runtime_smoke(
                    plan, paths, paths.run_id, opener=self.opener(bodies),
                    command_executor=self.installer_for(plan, extra_ringworld=True),
                    server_runner=self.successful_server,
                )

    def test_installed_mojang_server_must_match_the_manifest_pin(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, bodies = self.plan(Path(directory), "26.1-neoforge")
            with self.assertRaises(EXECUTOR.ExternalRuntimeExecutionError):
                EXECUTOR.execute_external_runtime_smoke(
                    plan, paths, paths.run_id, opener=self.opener(bodies),
                    command_executor=self.installer_for(plan, wrong_mojang_server=True),
                    server_runner=self.successful_server,
                )

    def test_interactive_fake_server_requires_markers_and_clean_stop(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-fabric")
            paths.logs_directory.mkdir(parents=True)
            plan.layout.root.mkdir(parents=True)
            plan = self._with_fake_launch(plan, Path(directory))
            verdict, reason, markers, stop, code, log, marker_ledger = EXECUTOR._run_server(plan, paths)
            self.assertEqual(MODEL.Verdict.PASS, verdict)
            self.assertIsNone(reason)
            self.assertEqual({"loader-bootstrap", "ringworld-bootstrap", "atlas-disabled", "server-ready"}, set(markers))
            self.assertEqual("Stopping server", stop)
            self.assertEqual(0, code)
            self.assertIn("Stopping server", Path(log).read_text(encoding="utf-8"))
            self.assertEqual(("server-stop", "world-save", "clean-stop", "runtime-exit"), tuple(event.name for event in marker_ledger[-4:]))

    def test_fatal_server_output_fails_even_after_ready_marker(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-fabric")
            paths.logs_directory.mkdir(parents=True)
            plan.layout.root.mkdir(parents=True)
            script = Path(directory) / "fatal-server.py"
            script.write_text(
                "import sys\n"
                "print('Fabric Loader bootstrapped', flush=True)\n"
                "print('RingWorld bootstrap settings: width=416, circumference=2048, wallHeight=160', flush=True)\n"
                "print('pregenerateTerrainAtlas=false', flush=True)\n"
                "print('Done (0.1s)!', flush=True)\n"
                "print('Crash report', flush=True)\n"
                "sys.stdin.readline()\n",
                encoding="utf-8",
            )
            plan = replace(plan, launch=replace(plan.launch, argv=(sys.executable, str(script)), timeout_seconds=5))
            verdict, reason, _, _, _, _, _ = EXECUTOR._run_server(plan, paths)
            self.assertEqual(MODEL.Verdict.FAIL, verdict)
            self.assertEqual("FATAL_SERVER_LOG:Crash report", reason)

    def test_occupied_exact_port_prevents_server_launch(self) -> None:
        with tempfile.TemporaryDirectory() as directory, socket.socket(socket.AF_INET, socket.SOCK_STREAM) as reservation:
            paths, plan, bodies = self.plan(Path(directory), "26.1-fabric")
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
            files = tuple(
                replace(file, contents=file.contents.replace("server-port=26101", f"server-port={port}"))
                if file.path == plan.layout.server_properties_path else file
                for file in plan.files
            )
            plan = replace(plan, files=files)
            result = EXECUTOR.execute_external_runtime_smoke(
                plan, paths, paths.run_id, opener=self.opener(bodies), command_executor=self.installer_for(plan),
                server_runner=lambda *args: (_ for _ in ()).throw(AssertionError("server must not launch")),
            )
            self.assertEqual(MODEL.Verdict.FAIL, result.verdict)
            self.assertEqual("SERVER_PORT_UNAVAILABLE", result.reason)

    def test_existing_runtime_is_never_replaced(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths, plan, _ = self.plan(Path(directory), "26.1-neoforge")
            plan.layout.root.mkdir(parents=True)
            with self.assertRaises(EXECUTOR.ExternalRuntimeExecutionError):
                EXECUTOR.execute_external_runtime_smoke(plan, paths, paths.run_id, opener=self.opener({}), command_executor=lambda *args, **kwargs: None)


if __name__ == "__main__":
    unittest.main()
