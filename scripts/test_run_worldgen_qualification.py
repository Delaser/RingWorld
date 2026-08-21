#!/usr/bin/env python3
"""No-network contract test for the worldgen nightly CLI."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
from types import SimpleNamespace
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(ROOT / "scripts"))

from external_runtime_qualification_adapter import canonical_cells_from_manifest  # noqa: E402
from minecraft_frozen_candidate import FrozenCandidateInspection  # noqa: E402
from minecraft_qualification_model import QualificationPaths  # noqa: E402
from run_minecraft_qualification import SourceProvenance  # noqa: E402
from run_worldgen_qualification import run  # noqa: E402
from test_minecraft_qualification_evidence import passing_record  # noqa: E402


QUICK = "20260812T170742Z-d5ff11778395"
NEW = "20260813T180000Z-abcdef123456"


class RunWorldgenQualificationTest(unittest.TestCase):
    def repository(self, root: Path):
        (root / "config").mkdir()
        manifest_path = root / "config/minecraft-version-matrix.json"
        shutil.copy2(ROOT / "config/minecraft-version-matrix.json", manifest_path)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        cell = next(item for item in manifest["cells"] if item["id"] == "26.1-fabric")
        quick = QualificationPaths.from_cell(root, cell, QUICK)
        candidate = quick.run_root / "frozen-candidates/fabric/ringworld-qualification.jar"
        candidate.parent.mkdir(parents=True)
        candidate.write_bytes(b"candidate")
        digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
        record = passing_record()
        expected = canonical_cells_from_manifest(manifest["cells"])[cell["id"]]
        record["cell"] = {name: expected[name] for name in ("id", "minecraft_version", "loader", "port", "world_config")}
        record["frozen_candidate"]["source_sha256"] = digest
        record["frozen_candidate"]["installed_sha256"] = digest
        record["runtime_inventory"][0]["sha256"] = digest
        record["same_file"] = {"group": "26.1.x-fabric", "sha256": digest, "cell_ids": ["26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"]}
        quick.evidence_directory.mkdir(parents=True)
        (quick.evidence_directory / "strict-terminal-evidence.json").write_text(json.dumps(record), encoding="utf-8")
        return cell, manifest_path

    @staticmethod
    def inspect(path: Path, loader: str) -> FrozenCandidateInspection:
        return FrozenCandidateInspection(str(path), loader, hashlib.sha256(path.read_bytes()).hexdigest(),
                                         "0.0.0-qualification+mc26.1", "qualification",
                                         ">=26.1 <=26.1.2", None, ("26.1", "26.1.1", "26.1.2"))

    @staticmethod
    def provenance(repository: Path, manifest: Path) -> SourceProvenance:
        return SourceProvenance("d" * 40, "codex/test", "d" * 40,
                                "https://github.com/Delaser/RingWorld.git",
                                hashlib.sha256(manifest.read_bytes()).hexdigest(), "e" * 64,
                                'openjdk version "25.0.1"')

    def test_run_binds_plan_and_current_source_to_executor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cell, manifest = self.repository(root)
            received = {}

            def executor(plan, paths, run_id, **kwargs):
                received.update({"plan": plan, "paths": paths, "run_id": run_id, **kwargs})
                return SimpleNamespace(cell_id=cell["id"], loader="fabric", minecraft_version="26.1",
                                       verdict=SimpleNamespace(value="PASS"), reason=None,
                                       evidence_json="terminal.json")

            result = run(
                argparse.Namespace(cell=cell["id"], quick_run_id=QUICK, manifest=str(manifest.relative_to(root))),
                repository_root=root, provenance_provider=self.provenance,
                candidate_inspector=self.inspect, run_id_factory=lambda: NEW, executor=executor,
            )
            self.assertEqual("PASS", result.verdict.value)
            self.assertEqual(NEW, received["run_id"])
            self.assertEqual("d" * 40, received["execution_source_provenance"]["commit"])
            self.assertEqual(4, len(received["plan"].stages))


if __name__ == "__main__":
    unittest.main()
