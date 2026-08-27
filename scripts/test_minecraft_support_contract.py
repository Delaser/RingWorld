"""Version-extension tests: new reviewed manifests must not require code edits."""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from minecraft_support_contract import LEGACY_CONTRACT, contract_from_manifest
from minecraft_frozen_candidate import inspect_frozen_candidate, verify_same_file_coverage
from run_minecraft_qualification import _full_loader_triplet, frozen_candidate_plan
from minecraft_qualification_model import required_dependency_properties
from test_minecraft_frozen_candidate import write_candidate

ROOT = Path(__file__).resolve().parents[1]


class SupportContractTest(unittest.TestCase):
    def manifest(self):
        return json.loads((ROOT / "config/minecraft-version-matrix-26.2.json").read_text())

    def test_historical_manifest_preserves_exact_contract(self):
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text())
        self.assertEqual(LEGACY_CONTRACT, contract_from_manifest(manifest))

    def test_new_line_build_and_inspection_are_manifest_driven(self):
        for version, neo in (("26.2", "26.2.0.69"), ("27.4", "27.4.0.1")):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                manifest = self.manifest()
                manifest["line"] = version
                for cell in manifest["cells"]:
                    cell["id"] = f"{version}-{cell['loader']}"
                    cell["minecraft"]["version"] = version
                    for dep in cell["dependencies"]:
                        if dep["coordinate"] == "net.neoforged:neoforge":
                            dep["version"] = neo
                contract = contract_from_manifest(manifest)
                self.assertEqual((version,), contract.versions)
                for cell in manifest["cells"]:
                    loader = cell["loader"]
                    self.assertEqual((cell,), _full_loader_triplet(manifest["cells"], loader, contract))
                    plan = frozen_candidate_plan(cell, ROOT, "extension-test", contract=contract)
                    self.assertIn(f"-Pminecraft_version={version}", plan.command.argv)
                    self.assertIn(f"-PringQualificationMinecraftRange={contract.minecraft_range(loader)}", plan.command.argv)
                    path = Path(directory) / f"{loader}.jar"
                    write_candidate(path, loader, source_version=version,
                                    minecraft_range=contract.minecraft_range(loader), loader_range=contract.neoforge_range)
                    inspection = inspect_frozen_candidate(path, loader, contract=contract)
                    coverage = verify_same_file_coverage(loader, {cell["id"]: inspection}, contract=contract)
                    self.assertEqual(contract.versions, coverage.minecraft_versions)
                    self.assertEqual(version, coverage.group)

    def test_missing_loader_and_mismatched_neoforge_are_rejected(self):
        manifest = self.manifest()
        manifest["cells"].pop()
        with self.assertRaisesRegex(ValueError, "matching"):
            contract_from_manifest(manifest)
        manifest = self.manifest()
        for dependency in manifest["cells"][1]["dependencies"]:
            if dependency["coordinate"] == "net.neoforged:neoforge":
                dependency["version"] = "26.1.2.99"
        with self.assertRaisesRegex(ValueError, "does not match"):
            contract_from_manifest(manifest)

    def test_both_builds_pin_companion_project_configuration(self):
        for cell in self.manifest()["cells"]:
            properties = dict(required_dependency_properties(cell))
            self.assertEqual("1.17.20", properties["loom_version"])
            self.assertEqual("0.158.0+26.2", properties["fabric_api_version"])
            self.assertEqual("26.2.0.69", properties["neoforge_version"])
            self.assertEqual("2.0.144", properties["moddevgradle_version"])

    def test_partial_selection_cannot_claim_complete_group(self):
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text())
        self.assertIsNone(_full_loader_triplet(manifest["cells"][:2], "fabric", contract_from_manifest(manifest)))

    def test_patch_gaps_cannot_widen_metadata_beyond_tested_versions(self):
        manifest = json.loads((ROOT / "config/minecraft-version-matrix.json").read_text())
        manifest["cells"] = [c for c in manifest["cells"] if c["minecraft"]["version"] != "26.1.1"]
        with self.assertRaisesRegex(ValueError, "gaps"):
            contract_from_manifest(manifest)


if __name__ == "__main__":
    unittest.main()
