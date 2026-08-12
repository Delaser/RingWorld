#!/usr/bin/env python3
"""Pure/local tests for the fail-closed external-runtime phase bridge."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from external_runtime_executor import (  # noqa: E402
    DownloadResult,
    ExternalRuntimeSmokeResult,
    MarkerEvent,
    ModCopyResult,
    RuntimeIdentity,
)
from external_runtime_smoke import CandidateJar, RuntimeDownload  # noqa: E402
from minecraft_frozen_candidate import FrozenCandidateInspection, SameFileCoverage, inspect_frozen_candidate  # noqa: E402
from minecraft_qualification_executor import QualificationLock  # noqa: E402
from minecraft_qualification_evidence import validate_terminal_evidence  # noqa: E402
from minecraft_qualification_model import (  # noqa: E402
    PhaseName,
    QualificationPaths,
    Verdict,
)
from external_runtime_qualification_adapter import (  # noqa: E402
    EXTERNAL_RUNTIME_EXECUTOR_UNAVAILABLE,
    HELD_LOCK_UNAVAILABLE,
    SAME_FILE_GROUP_UNAVAILABLE,
    ExternalAdapterError,
    ExternalRuntimeQualificationAdapter,
    RuntimeSupportInputs,
    canonical_cells_from_manifest,
    capture_runtime_support,
    external_runtime_adapter_from_qualification_inputs,
    reviewed_range_identities,
    strict_provenance_from_source,
    write_strict_terminal_evidence,
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ranges() -> dict[str, dict[str, str]]:
    return {
        "fabric": {
            "oldest_abi_minecraft_version": "26.1",
            "minecraft_range": ">=26.1 <=26.1.2",
            "loader_range": "",
        },
        "neoforge": {
            "oldest_abi_minecraft_version": "26.1",
            "minecraft_range": "[26.1,26.1.2]",
            "loader_range": "[26.1.0.19-beta,26.1.2.87]",
        },
    }


class ExternalRuntimeQualificationAdapterTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text(encoding="utf-8"))
        cls.manifest_cells = manifest["cells"]
        cls.canonical = canonical_cells_from_manifest(cls.manifest_cells)

    @staticmethod
    def write_frozen_fabric_candidate(path: Path) -> FrozenCandidateInspection:
        path.parent.mkdir(parents=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("LICENSE-RINGWORLD.txt", (ROOT / "LICENSE").read_text(encoding="utf-8"))
            archive.writestr("ringworld-build.properties", "artifactVersion=0.0.0-qualification+mc26.1\nreleaseLabel=qualification-26.1-fabric\n")
            archive.writestr("fabric.mod.json", json.dumps({
                "id": "ringworld", "version": "0.0.0-qualification+mc26.1", "license": "MPL-2.0",
                "depends": {"minecraft": ">=26.1 <=26.1.2"},
            }))
        return inspect_frozen_candidate(path, "fabric")

    def test_manifest_conversion_binds_nested_minecraft_profile_and_safe_world(self) -> None:
        first = self.canonical["26.1-fabric"]
        self.assertEqual("26.1", first["minecraft_version"])
        self.assertEqual(26101, first["port"])
        self.assertEqual(2048, first["world_config"]["circumference_blocks"])
        self.assertEqual(416, first["world_config"]["width_blocks"])
        self.assertFalse(first["world_config"]["pregenerate_terrain_atlas"])
        self.assertEqual("26.1.2", self.canonical["26.1.2-neoforge"]["minecraft_version"])
        self.assertEqual(26122, self.canonical["26.1.2-neoforge"]["port"])

    def test_manifest_conversion_rejects_missing_nested_or_duplicate_identity(self) -> None:
        invalid = dict(self.manifest_cells[0])
        invalid["minecraft"] = {}
        with self.assertRaises(ExternalAdapterError):
            canonical_cells_from_manifest((invalid,))
        duplicate = (self.manifest_cells[0], self.manifest_cells[0])
        with self.assertRaises(ExternalAdapterError):
            canonical_cells_from_manifest(duplicate)

    def test_default_phase_adapter_never_calls_a_raw_executor(self) -> None:
        candidate = CandidateJar(Path("/frozen/ringworld.jar"), "a" * 64, "fabric")
        inspection = FrozenCandidateInspection(
            "/frozen/ringworld.jar", "fabric", "a" * 64, "0.0.0-qualification+mc26.1",
            "qualification-26.1-fabric", ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"),
        )
        coverage = SameFileCoverage("fabric", "a" * 64, ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"), ("26.1", "26.1.1", "26.1.2"))
        adapter = ExternalRuntimeQualificationAdapter(
            self.canonical, ranges(), {"fabric": candidate},
            {"fabric": RuntimeSupportInputs({}, inspection, coverage)},
        )
        context = SimpleNamespace(
            cell=self.manifest_cells[0], paths=SimpleNamespace(), phase=PhaseName.DEDICATED_SMOKE,
            command=None, ordinal=1,
        )
        result = adapter(context)
        self.assertEqual(Verdict.INCOMPLETE, result.verdict)
        self.assertEqual(EXTERNAL_RUNTIME_EXECUTOR_UNAVAILABLE, result.reason)

    def test_adapter_stops_before_any_executor_when_same_file_group_is_missing(self) -> None:
        calls: list[object] = []
        candidate = CandidateJar(Path("/frozen/ringworld.jar"), "a" * 64, "fabric")
        inspection = FrozenCandidateInspection(
            "/frozen/ringworld.jar", "fabric", "a" * 64, "0.0.0-qualification+mc26.1",
            "qualification-26.1-fabric", ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"),
        )
        adapter = ExternalRuntimeQualificationAdapter(
            self.canonical, ranges(), {"fabric": candidate},
            {"fabric": RuntimeSupportInputs({}, inspection, None)},
            smoke_executor=lambda *args: calls.append(args),  # type: ignore[arg-type]
        )
        context = SimpleNamespace(
            cell=self.manifest_cells[0], paths=SimpleNamespace(), phase=PhaseName.DEDICATED_SMOKE,
            command=None, ordinal=1,
        )
        result = adapter(context)
        self.assertEqual(SAME_FILE_GROUP_UNAVAILABLE, result.reason)
        self.assertEqual([], calls)

    def test_adapter_requires_a_runner_lent_lock_before_calling_executor(self) -> None:
        candidate = CandidateJar(Path("/frozen/ringworld.jar"), "a" * 64, "fabric")
        inspection = FrozenCandidateInspection(
            "/frozen/ringworld.jar", "fabric", "a" * 64, "0.0.0-qualification+mc26.1",
            "qualification-26.1-fabric", ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"),
        )
        coverage = SameFileCoverage("fabric", "a" * 64, ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"), ("26.1", "26.1.1", "26.1.2"))
        calls: list[object] = []
        adapter = ExternalRuntimeQualificationAdapter(
            self.canonical, ranges(), {"fabric": candidate},
            {"fabric": RuntimeSupportInputs({}, inspection, coverage)},
            smoke_executor=lambda *args, **kwargs: calls.append((args, kwargs)),  # type: ignore[arg-type]
        )
        context = SimpleNamespace(
            cell=self.manifest_cells[0], paths=SimpleNamespace(), phase=PhaseName.DEDICATED_SMOKE,
            command=None, ordinal=1,
        )
        result = adapter(context)
        self.assertEqual(HELD_LOCK_UNAVAILABLE, result.reason)
        self.assertEqual([], calls)

    def test_capture_binds_real_files_and_strict_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell = self.manifest_cells[0]
            paths = QualificationPaths.from_cell(root, cell, "20260812T120000Z-0123456789ab")
            paths.logs_directory.mkdir(parents=True)
            paths.evidence_directory.mkdir(parents=True)
            runtime = paths.run_directory / "external-dedicated"
            mods = runtime / "mods"
            mods.mkdir(parents=True)
            installer = paths.cache_directory / "external-runtime" / "installer.jar"
            installer.parent.mkdir(parents=True)
            installer.write_bytes(b"installer")
            installer_stdout, installer_stderr = paths.logs_directory / "01-install.out", paths.logs_directory / "01-install.err"
            installer_stdout.write_text("installer ok\n", encoding="utf-8")
            installer_stderr.write_text("", encoding="utf-8")
            server_log = paths.logs_directory / "02-server.log"
            server_log.write_text("server ok\n", encoding="utf-8")
            minecraft_server, launcher, candidate = runtime / "server.jar", runtime / "fabric-server-launch.jar", mods / "ringworld-candidate.jar"
            minecraft_server.write_bytes(b"minecraft")
            launcher.write_bytes(b"launcher")
            candidate.write_bytes(b"candidate")
            candidate_hash = digest(candidate)
            download = DownloadResult("runtime installer", str(installer), "sha256", digest(installer), digest(installer), False)
            result = ExternalRuntimeSmokeResult(
                "26.1-fabric", "fabric", "26.1", Verdict.PASS, None, (download,),
                SimpleNamespace(verdict=Verdict.PASS, argv=("java", "-jar", str(installer)), return_code=0,
                                started_at_utc="2026-08-12T12:00:00Z", elapsed_seconds=1.0,
                                stdout_log=str(installer_stdout), stderr_log=str(installer_stderr)),
                (ModCopyResult("RingWorld", str(candidate), str(candidate), candidate_hash, candidate_hash),),
                True,
                ("atlas-disabled", "loader-bootstrap", "ringworld-bootstrap", "server-ready"),
                tuple(MarkerEvent(name, f"2026-08-12T12:00:0{index}Z") for index, name in enumerate((
                    "runtime-start", "loader-bootstrap", "ringworld-bootstrap", "atlas-disabled", "server-ready",
                    "server-stop", "world-save", "clean-stop", "runtime-exit",
                ))),
                RuntimeIdentity("fabric", "0.19.3", str(launcher), str(minecraft_server), "unused", digest(minecraft_server)),
                "Stopping server", 0, str(server_log), "2026-08-12T12:00:00Z", 8.0,
            )
            plan = SimpleNamespace(
                loader="fabric", downloads=(RuntimeDownload("runtime installer", "https://example.invalid/installer.jar", "sha256", digest(installer), installer),),
                mods=(SimpleNamespace(name="RingWorld", destination=candidate, sha256=candidate_hash),),
                launch=SimpleNamespace(argv=("java", "-jar", str(launcher))),
            )
            inspection = FrozenCandidateInspection(
                "frozen/ringworld.jar", "fabric", candidate_hash, "0.0.0-qualification+mc26.1",
                "qualification-26.1-fabric", ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"),
            )
            provenance = {
                "commit": "1" * 40, "clean": True, "public_origin": "https://github.com/Delaser/RingWorld.git",
                "manifest_sha256": "a" * 64, "wrapper_sha256": "b" * 64,
                "java": {"major": 25, "version": "25.0.1"}, "platform": {"system": "Test", "machine": "test"},
            }
            support = capture_runtime_support(
                plan, result, paths,
                RuntimeSupportInputs(provenance, inspection, SameFileCoverage(
                    "fabric", candidate_hash, ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"), ("26.1", "26.1.1", "26.1.2"),
                )),
            )
            from minecraft_qualification_evidence import normalize_external_runtime_result
            terminal = normalize_external_runtime_result(result, support, self.canonical, ranges())
            self.assertEqual("PASS", validate_terminal_evidence(terminal, self.canonical, ranges()).verdict)
            self.assertEqual(candidate_hash, terminal["same_file"]["sha256"])
            reference = write_strict_terminal_evidence(terminal, paths, self.canonical, ranges())
            stored = paths.evidence_directory / "strict-terminal-evidence.json"
            self.assertTrue(stored.is_file())
            self.assertEqual("strict-terminal-evidence-json", reference.kind)
            self.assertIn("SHA-256 ", reference.detail)
            with self.assertRaises(Exception):
                write_strict_terminal_evidence(terminal, paths, self.canonical, ranges())

    def test_factory_keeps_partial_triplet_incomplete_without_calling_runtime(self) -> None:
        calls: list[object] = []
        source = SimpleNamespace(
            commit="1" * 40, manifest_sha256="a" * 64, gradle_wrapper_sha256="b" * 64,
            java_version="25.0.1", origin="https://github.com/Delaser/RingWorld.git",
        )
        adapter = external_runtime_adapter_from_qualification_inputs(
            (self.manifest_cells[0],), source, {},
            smoke_executor=lambda *args, **kwargs: calls.append((args, kwargs)),  # type: ignore[arg-type]
        )
        context = SimpleNamespace(
            cell=self.manifest_cells[0], paths=SimpleNamespace(), phase=PhaseName.DEDICATED_SMOKE,
            command=None, ordinal=1, held_lock=None,
        )
        result = adapter(context)
        self.assertEqual(Verdict.INCOMPLETE, result.verdict)
        self.assertEqual([], calls)

    def test_factory_derives_exact_full_triplet_same_file_inputs(self) -> None:
        source = SimpleNamespace(
            commit="1" * 40, manifest_sha256="a" * 64, gradle_wrapper_sha256="b" * 64,
            java_version="25.0.1", origin="https://github.com/Delaser/RingWorld.git",
        )
        candidate = Path("/tmp/qualification/frozen-candidates/fabric/ringworld-qualification.jar")
        inspection = FrozenCandidateInspection(
            str(candidate), "fabric", "a" * 64, "0.0.0-qualification+mc26.1", "qualification-26.1-fabric",
            ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"),
        )
        preparation = SimpleNamespace(
            verdict=Verdict.PASS, plan=SimpleNamespace(candidate_path=candidate), inspection=inspection,
        )
        adapter = external_runtime_adapter_from_qualification_inputs(
            tuple(cell for cell in self.manifest_cells if cell["loader"] == "fabric"), source,
            {"fabric": preparation}, smoke_executor=lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("runtime must not run")),  # type: ignore[arg-type]
        )
        self.assertEqual({"fabric"}, set(adapter._candidates))
        self.assertEqual(("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"), adapter._support_inputs["fabric"].same_file.cell_ids)  # type: ignore[union-attr]
        self.assertEqual(reviewed_range_identities()["fabric"]["minecraft_range"], adapter._range_identities["fabric"]["minecraft_range"])
        self.assertEqual(True, strict_provenance_from_source(source)["clean"])

    def test_changed_frozen_candidate_fails_before_executor_io(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cells = tuple(cell for cell in self.manifest_cells if cell["loader"] == "fabric")
            paths = QualificationPaths.from_cell(root, cells[0], "20260812T120000Z-0123456789ab")
            frozen_root = paths.run_root / "frozen-candidates"
            candidate_path = frozen_root / "fabric" / "ringworld-qualification.jar"
            inspection = self.write_frozen_fabric_candidate(candidate_path)
            coverage = SameFileCoverage(
                "fabric", inspection.sha256,
                ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"),
                ("26.1", "26.1.1", "26.1.2"),
            )
            calls: list[object] = []
            adapter = ExternalRuntimeQualificationAdapter(
                canonical_cells_from_manifest(cells), ranges(),
                {"fabric": CandidateJar(candidate_path, inspection.sha256, "fabric", inspection.minecraft_range)},
                {"fabric": RuntimeSupportInputs({}, inspection, coverage)},
                frozen_candidate_root=frozen_root,
                smoke_executor=lambda *args, **kwargs: calls.append((args, kwargs)),  # type: ignore[arg-type]
            )
            candidate_path.write_bytes(b"changed after preparation")
            paths.cell_root.mkdir(parents=True)
            with QualificationLock.acquire(paths.lock_path, paths.run_id) as held:
                result = adapter(SimpleNamespace(
                    cell=cells[0], paths=paths, phase=PhaseName.DEDICATED_SMOKE,
                    command=None, ordinal=1, held_lock=held,
                ))
            self.assertEqual(Verdict.FAIL, result.verdict)
            self.assertEqual([], calls)
            self.assertFalse((paths.evidence_directory / "strict-terminal-evidence.json").exists())

    def test_real_bridge_uses_runner_lock_and_persists_terminal_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cells = tuple(cell for cell in self.manifest_cells if cell["loader"] == "fabric")
            paths = QualificationPaths.from_cell(root, cells[0], "20260812T120000Z-0123456789ab")
            frozen_root = paths.run_root / "frozen-candidates"
            candidate_path = frozen_root / "fabric" / "ringworld-qualification.jar"
            inspection = self.write_frozen_fabric_candidate(candidate_path)
            coverage = SameFileCoverage(
                "fabric", inspection.sha256,
                ("26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"),
                ("26.1", "26.1.1", "26.1.2"),
            )
            observed: list[tuple[object, str]] = []

            def deterministic_failure(plan, received_paths, run_id, *, held_lock):
                held_lock.require_held_for(received_paths.lock_path, run_id)
                observed.append((held_lock, run_id))
                return ExternalRuntimeSmokeResult(
                    plan.cell_id, plan.loader, plan.minecraft_version, Verdict.FAIL,
                    "DETERMINISTIC_TEST_FAILURE", (), None, (), False, (), (), None,
                    None, None, None, "2026-08-12T12:00:00Z", 0.0,
                )

            adapter = ExternalRuntimeQualificationAdapter(
                canonical_cells_from_manifest(cells), ranges(),
                {"fabric": CandidateJar(candidate_path, inspection.sha256, "fabric", inspection.minecraft_range)},
                {"fabric": RuntimeSupportInputs({}, inspection, coverage)},
                frozen_candidate_root=frozen_root, smoke_executor=deterministic_failure,
            )
            paths.cell_root.mkdir(parents=True)
            paths.evidence_directory.mkdir()
            with QualificationLock.acquire(paths.lock_path, paths.run_id) as held:
                result = adapter(SimpleNamespace(
                    cell=cells[0], paths=paths, phase=PhaseName.DEDICATED_SMOKE,
                    command=None, ordinal=1, held_lock=held,
                ))
                self.assertIs(held, observed[0][0])
            self.assertEqual(Verdict.FAIL, result.verdict)
            self.assertEqual("DETERMINISTIC_TEST_FAILURE", result.reason)
            stored = paths.evidence_directory / "strict-terminal-evidence.json"
            self.assertTrue(stored.is_file())
            self.assertEqual("FAIL", json.loads(stored.read_text(encoding="utf-8"))["verdict"])


if __name__ == "__main__":
    unittest.main()
