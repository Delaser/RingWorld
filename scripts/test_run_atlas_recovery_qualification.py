#!/usr/bin/env python3
"""No-network contract tests for the bounded Atlas-recovery CLI."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from minecraft_frozen_candidate import FrozenCandidateInspection  # noqa: E402
from minecraft_qualification_model import QualificationPaths  # noqa: E402
from external_runtime_qualification_adapter import canonical_cells_from_manifest  # noqa: E402
from run_atlas_recovery_qualification import (  # noqa: E402
    AtlasRecoveryInvocationError,
    prepare_invocation,
    run,
)
from run_minecraft_qualification import SourceProvenance  # noqa: E402
from test_minecraft_qualification_evidence import passing_record  # noqa: E402


QUICK_RUN = "20260812T170742Z-d5ff11778395"
NEW_RUN = "20260812T180000Z-abcdef123456"


class AtlasRecoveryCliTest(unittest.TestCase):
    def _repository(self, root: Path, cell_id: str = "26.1-fabric") -> tuple[dict, Path, Path]:
        (root / "config").mkdir()
        manifest_path = root / "config/minecraft-version-matrix.json"
        shutil.copy2(ROOT / "config/minecraft-version-matrix.json", manifest_path)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        cell = next(item for item in manifest["cells"] if item["id"] == cell_id)
        quick = QualificationPaths.from_cell(root, cell, QUICK_RUN)
        oldest = next(item for item in manifest["cells"] if item["id"] == "26.1-fabric")
        candidate_owner = QualificationPaths.from_cell(root, oldest, QUICK_RUN)
        candidate = candidate_owner.run_root / "frozen-candidates/fabric/ringworld-qualification.jar"
        candidate.parent.mkdir(parents=True)
        candidate.write_bytes(b"test-frozen-candidate")
        digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
        record = passing_record()
        expected = canonical_cells_from_manifest(manifest["cells"])[cell["id"]]
        record["cell"] = {name: expected[name] for name in (
            "id", "minecraft_version", "loader", "port", "world_config",
        )}
        record["frozen_candidate"]["source_sha256"] = digest
        record["frozen_candidate"]["installed_sha256"] = digest
        record["runtime_inventory"][0]["sha256"] = digest
        record["same_file"] = {
            "group": "26.1.x-fabric", "sha256": digest,
            "cell_ids": ["26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"],
        }
        quick.evidence_directory.mkdir(parents=True)
        (quick.evidence_directory / "strict-terminal-evidence.json").write_text(json.dumps(record), encoding="utf-8")
        return cell, manifest_path, candidate

    @staticmethod
    def _inspection(path: Path, loader: str) -> FrozenCandidateInspection:
        return FrozenCandidateInspection(
            str(path), loader, hashlib.sha256(path.read_bytes()).hexdigest(),
            "0.0.0-qualification+mc26.1", f"qualification-26.1-{loader}",
            ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"),
        )

    @staticmethod
    def _provenance(repository: Path, manifest: Path) -> SourceProvenance:
        return SourceProvenance(
            "d" * 40, "codex/test", "d" * 40,
            "https://github.com/Delaser/RingWorld.git",
            hashlib.sha256(manifest.read_bytes()).hexdigest(), "e" * 64,
            'openjdk version "25.0.4"',
        )

    def test_prepares_only_exact_quick_evidence_and_frozen_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell, manifest, candidate_path = self._repository(root)
            prepared = prepare_invocation(
                repository_root=root, manifest_path=manifest, cell_id=cell["id"], quick_run_id=QUICK_RUN,
                run_id=NEW_RUN, provenance_provider=self._provenance, candidate_inspector=self._inspection,
            )
            self.assertEqual(candidate_path, prepared.candidate.path)
            self.assertEqual(QUICK_RUN, prepared.quick_paths.run_id)
            self.assertEqual(NEW_RUN, prepared.paths.run_id)
            self.assertEqual("d" * 40, prepared.source_provenance["commit"])
            self.assertEqual("d" * 40, prepared.source_provenance["upstream"])

    def test_patch_cell_uses_one_oldest_abi_candidate_and_its_own_quick_record(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell, manifest, candidate_path = self._repository(root, "26.1.2-fabric")
            prepared = prepare_invocation(
                repository_root=root, manifest_path=manifest, cell_id=cell["id"], quick_run_id=QUICK_RUN,
                run_id=NEW_RUN, provenance_provider=self._provenance, candidate_inspector=self._inspection,
            )
            self.assertEqual(candidate_path, prepared.candidate.path)
            self.assertEqual("26.1.2-fabric", prepared.quick_paths.cell_id)
            self.assertEqual("26.1.2-fabric", prepared.paths.cell_id)

    def test_duplicate_loader_candidate_roots_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell, manifest, candidate_path = self._repository(root, "26.1.1-fabric")
            duplicate_owner = QualificationPaths.from_cell(root, cell, QUICK_RUN)
            duplicate = duplicate_owner.run_root / "frozen-candidates/fabric/ringworld-qualification.jar"
            duplicate.parent.mkdir(parents=True)
            duplicate.write_bytes(candidate_path.read_bytes())
            with self.assertRaisesRegex(AtlasRecoveryInvocationError, "exactly one"):
                prepare_invocation(
                    repository_root=root, manifest_path=manifest, cell_id=cell["id"], quick_run_id=QUICK_RUN,
                    run_id=NEW_RUN, provenance_provider=self._provenance, candidate_inspector=self._inspection,
                )

    def test_invalid_quick_record_stops_before_current_source_or_executor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell, manifest, _candidate = self._repository(root)
            quick = QualificationPaths.from_cell(root, cell, QUICK_RUN)
            quick_file = quick.evidence_directory / "strict-terminal-evidence.json"
            record = json.loads(quick_file.read_text(encoding="utf-8"))
            record["verdict"] = "FAIL"
            record["reason"] = "bad fixture"
            for key in ("provenance", "commands", "installer", "runtime_inventory", "frozen_candidate", "markers", "runtime", "same_file"):
                record.pop(key, None)
            quick_file.write_text(json.dumps(record), encoding="utf-8")
            source_called = False

            def source(repository: Path, manifest_path: Path) -> SourceProvenance:
                nonlocal source_called
                source_called = True
                return self._provenance(repository, manifest_path)

            with self.assertRaises(AtlasRecoveryInvocationError):
                prepare_invocation(
                    repository_root=root, manifest_path=manifest, cell_id=cell["id"], quick_run_id=QUICK_RUN,
                    run_id=NEW_RUN, provenance_provider=source, candidate_inspector=self._inspection,
                )
            self.assertFalse(source_called)

    def test_run_passes_real_stage_adapter_and_current_provenance_to_executor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell, manifest, _candidate = self._repository(root)
            received: dict[str, object] = {}

            def executor(plan, paths, run_id, **kwargs):
                received.update({"plan": plan, "paths": paths, "run_id": run_id, **kwargs})
                return SimpleNamespace(cell_id=cell["id"], loader="fabric", minecraft_version="26.1",
                                       verdict=SimpleNamespace(value="PASS"), reason=None, evidence_json="evidence/terminal.json")

            result = run(
                argparse.Namespace(cell=cell["id"], quick_run_id=QUICK_RUN, manifest=str(manifest.relative_to(root))),
                repository_root=root, provenance_provider=self._provenance, candidate_inspector=self._inspection,
                run_id_factory=lambda: NEW_RUN, executor=executor,
            )
            self.assertEqual("PASS", result.verdict.value)
            self.assertEqual(NEW_RUN, received["run_id"])
            self.assertEqual("d" * 40, received["execution_source_provenance"]["commit"])
            self.assertEqual("run_external_runtime_atlas_recovery_stage", received["stage_runner"].__name__)


if __name__ == "__main__":
    unittest.main()
