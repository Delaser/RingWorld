#!/usr/bin/env python3
"""No-network binding test for the copied-world upgrade CLI."""

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
import run_world_upgrade_qualification as world_upgrade_cli  # noqa: E402
from run_world_upgrade_qualification import run  # noqa: E402
from test_external_runtime_worldgen_executor import settings_bytes  # noqa: E402
from test_minecraft_qualification_evidence import passing_record  # noqa: E402


SOURCE_RUN = "20260813T120000Z-0123456789ab"
QUICK_RUN = "20260813T130000Z-abcdef123456"
NEW_RUN = "20260813T180000Z-abcdef123456"


class RunWorldUpgradeQualificationTest(unittest.TestCase):
    def repository(self, root: Path):
        (root / "config").mkdir()
        manifest_path = root / "config/minecraft-version-matrix.json"
        shutil.copy2(ROOT / "config/minecraft-version-matrix.json", manifest_path)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        source = next(item for item in manifest["cells"] if item["id"] == "26.1-fabric")
        target = next(item for item in manifest["cells"] if item["id"] == "26.1.1-fabric")
        quick = QualificationPaths.from_cell(root, target, QUICK_RUN)
        candidate = quick.run_root / "frozen-candidates/fabric/ringworld-qualification.jar"
        candidate.parent.mkdir(parents=True)
        candidate.write_bytes(b"candidate")
        digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
        record = passing_record()
        expected = canonical_cells_from_manifest(manifest["cells"])[target["id"]]
        record["cell"] = {name: expected[name] for name in ("id", "minecraft_version", "loader", "port", "world_config")}
        record["frozen_candidate"]["source_sha256"] = digest
        record["frozen_candidate"]["installed_sha256"] = digest
        record["runtime_inventory"][0]["sha256"] = digest
        record["same_file"] = {"group": "26.1.x-fabric", "sha256": digest,
                               "cell_ids": ["26.1-fabric", "26.1.1-fabric", "26.1.2-fabric"]}
        quick.evidence_directory.mkdir(parents=True)
        (quick.evidence_directory / "strict-terminal-evidence.json").write_text(json.dumps(record), encoding="utf-8")
        source_paths = QualificationPaths.from_cell(root, source, SOURCE_RUN)
        worldgen = source_paths.evidence_directory / "nightly/02-worldgen-seam-structures"
        worldgen.mkdir(parents=True)
        terminal = {"fixture": "worldgen-seam-structures", "cell_id": source["id"], "loader": "fabric",
                    "minecraft_version": "26.1", "verdict": "PASS", "qualification": {
                    "frozenCandidateSha256": digest,
                    "stages": ["production-fresh", "production-resume", "seam-crossing", "terminal-policy"],
                    "captures": {"production-resume": {"logSha256": "placeholder"}},
                }}
        (worldgen / "terminal.json").write_text(json.dumps(terminal), encoding="utf-8")
        (worldgen / "production-resume.log").write_text("[worldgen-matrix] placeholder\n", encoding="utf-8")
        settings = source_paths.run_directory / "nightly/02-worldgen-seam-structures/production/runtime/world/dimensions/minecraft/overworld/data/ringworld/settings.dat"
        settings.parent.mkdir(parents=True)
        settings.write_bytes(settings_bytes(256, 16384, 1))
        return source, target, manifest_path

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

    def test_cli_binds_source_terminal_copy_and_target_quick_to_existing_resume_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target, manifest = self.repository(root)
            received = {}

            def executor(*args, **kwargs):
                received["args"], received["kwargs"] = args, kwargs
                return SimpleNamespace(source_cell_id=source["id"], target_cell_id=target["id"], loader="fabric",
                                       verdict=SimpleNamespace(value="PASS"), reason=None, evidence_json="terminal.json")

            result = run(
                argparse.Namespace(source_cell=source["id"], source_worldgen_run_id=SOURCE_RUN,
                                   target_cell=target["id"], target_quick_run_id=QUICK_RUN,
                                   manifest=str(manifest.relative_to(root))),
                repository_root=root, provenance_provider=self.provenance,
                candidate_inspector=self.inspect, run_id_factory=lambda: NEW_RUN, executor=executor,
            )
            self.assertEqual("PASS", result.verdict.value)
            source_input, _, _, _, stage, paths, run_id = received["args"]
            self.assertEqual(source["id"], source_input.cell_id)
            self.assertEqual(target["id"], paths.cell_id)
            self.assertEqual(NEW_RUN, run_id)
            self.assertTrue(source_input.world_root.name == "world")
            self.assertEqual("production-resume", stage.name)
            self.assertTrue(stage.resume)

    def test_cli_wires_target_contract_and_distinct_source_target_ranges(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, _, source_manifest = self.repository(root)
            target_manifest = root / "config/minecraft-version-matrix-26.2.json"
            shutil.copy2(ROOT / "config/minecraft-version-matrix-26.2.json", target_manifest)
            target_matrix = json.loads(target_manifest.read_text(encoding="utf-8"))
            target = next(item for item in target_matrix["cells"] if item["id"] == "26.2-fabric")
            quick = QualificationPaths.from_cell(root, target, QUICK_RUN)
            candidate = quick.run_root / "frozen-candidates/fabric/ringworld-qualification.jar"
            candidate.parent.mkdir(parents=True)
            candidate.write_bytes(b"26.2 candidate")
            digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
            record = passing_record()
            expected = canonical_cells_from_manifest(target_matrix["cells"])[target["id"]]
            record["cell"] = {name: expected[name] for name in ("id", "minecraft_version", "loader", "port", "world_config")}
            record["frozen_candidate"].update({  # type: ignore[index]
                "source_sha256": digest, "installed_sha256": digest,
                "oldest_abi_minecraft_version": "26.2", "minecraft_range": ">=26.2 <=26.2",
            })
            record["runtime_inventory"][0]["sha256"] = digest  # type: ignore[index]
            record["same_file"] = {"group": "26.2-fabric", "sha256": digest, "cell_ids": ["26.2-fabric"]}
            quick.evidence_directory.mkdir(parents=True)
            (quick.evidence_directory / "strict-terminal-evidence.json").write_text(json.dumps(record), encoding="utf-8")
            received, contracts = {}, []

            def inspect(path: Path, loader: str, *, contract):
                contracts.append(contract)
                return FrozenCandidateInspection(
                    str(path), loader, hashlib.sha256(path.read_bytes()).hexdigest(),
                    contract.artifact_version, contract.release_label(loader), contract.minecraft_range(loader),
                    None, contract.versions, contract,
                )

            def executor(*args, **kwargs):
                received["kwargs"] = kwargs
                return SimpleNamespace(source_cell_id=source["id"], target_cell_id=target["id"], loader="fabric",
                                       verdict=SimpleNamespace(value="PASS"), reason=None, evidence_json="terminal.json")

            original = world_upgrade_cli.inspect_frozen_candidate
            world_upgrade_cli.inspect_frozen_candidate = inspect
            try:
                result = run(
                    argparse.Namespace(source_cell=source["id"], source_worldgen_run_id=SOURCE_RUN,
                                       target_cell=target["id"], target_quick_run_id=QUICK_RUN,
                                       manifest=str(target_manifest.relative_to(root)),
                                       source_manifest=str(source_manifest.relative_to(root))),
                    repository_root=root, provenance_provider=self.provenance,
                    candidate_inspector=inspect, run_id_factory=lambda: NEW_RUN, executor=executor,
                )
            finally:
                world_upgrade_cli.inspect_frozen_candidate = original
            self.assertEqual("PASS", result.verdict.value)
            self.assertEqual(["26.2"], [contract.oldest for contract in contracts])
            self.assertEqual(">=26.2 <=26.2", received["kwargs"]["range_identities"]["fabric"]["minecraft_range"])
            self.assertEqual(">=26.1 <=26.1.2", received["kwargs"]["source_range_identities"]["fabric"]["minecraft_range"])


if __name__ == "__main__":
    unittest.main()
