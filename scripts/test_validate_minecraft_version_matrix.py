#!/usr/bin/env python3
"""Pure policy tests for ``validate_minecraft_version_matrix.py``."""

from __future__ import annotations

import copy
import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "config" / "minecraft-version-matrix.json"
VALIDATOR_PATH = ROOT / "scripts" / "validate_minecraft_version_matrix.py"
SPEC = importlib.util.spec_from_file_location("minecraft_version_matrix_validator", VALIDATOR_PATH)
assert SPEC and SPEC.loader
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class MinecraftVersionMatrixValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    def errors(self, manifest: dict) -> list[str]:
        return VALIDATOR.validate_manifest(manifest)

    def test_initial_six_cell_manifest_is_valid(self) -> None:
        self.assertEqual([], self.errors(self.manifest))
        self.assertEqual(
            {
                ("26.1", "fabric"),
                ("26.1", "neoforge"),
                ("26.1.1", "fabric"),
                ("26.1.1", "neoforge"),
                ("26.1.2", "fabric"),
                ("26.1.2", "neoforge"),
            },
            {(cell["minecraft"]["version"], cell["loader"]) for cell in self.manifest["cells"]},
        )

    def test_initial_manifest_pins_historical_patch_dependencies_without_claiming_support(self) -> None:
        cells = {cell["id"]: cell for cell in self.manifest["cells"]}
        expected = {
            "26.1-fabric": {"0.19.3", "0.145.1+26.1", "1.17.19"},
            "26.1.1-fabric": {"0.19.3", "0.145.4+26.1.1", "1.17.19"},
            "26.1-neoforge": {"26.1.0.19-beta", "2.0.143"},
            "26.1.1-neoforge": {"26.1.1.15-beta", "2.0.143"},
        }
        for cell_id, versions in expected.items():
            with self.subTest(cell=cell_id):
                cell = cells[cell_id]
                self.assertEqual("pending", cell["status"])
                self.assertEqual(
                    versions,
                    {dependency["version"] for dependency in cell["dependencies"]},
                )
                self.assertEqual("Qualification evidence", cell["pending_inputs"][0]["name"])

    def test_rejects_floating_dependency_version(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["cells"][0]["dependencies"][0]["version"] = "latest"
        self.assertTrue(any("non-floating version" in error for error in self.errors(manifest)))

    def test_rejects_missing_checksum(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        del manifest["cells"][0]["dependencies"][0]["checksum"]
        self.assertTrue(any("checksum" in error for error in self.errors(manifest)))

    def test_rejects_duplicate_cells(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        duplicate = copy.deepcopy(manifest["cells"][0])
        duplicate["id"] = "another-26.1-fabric"
        duplicate["profile"]["run_directory"] = "run/qualification/duplicate/fabric"
        duplicate["profile"]["cache_directory"] = "run/qualification-cache/duplicate/fabric"
        duplicate["profile"]["evidence_directory"] = "dist/qualification/ringworld/duplicate/fabric"
        duplicate["profile"]["server_port"] = 26301
        manifest["cells"].append(duplicate)
        self.assertTrue(any("duplicate qualification cell" in error for error in self.errors(manifest)))

    def test_rejects_invalid_status(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["cells"][0]["status"] = "complete"
        self.assertTrue(any("cells[0].status" in error for error in self.errors(manifest)))

    def test_rejects_shared_profile_path(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["cells"][1]["profile"]["run_directory"] = manifest["cells"][0]["profile"]["run_directory"]
        self.assertTrue(any("path" in error and "shared" in error for error in self.errors(manifest)))

    def test_rejects_profile_paths_outside_qualification_roots(self) -> None:
        for field, unsafe in (
            ("run_directory", "run-multiplayer/server"),
            ("cache_directory", "dist/client-bundle/.prism-data"),
            ("evidence_directory", "docs/evidence"),
        ):
            with self.subTest(field=field):
                manifest = copy.deepcopy(self.manifest)
                manifest["cells"][0]["profile"][field] = unsafe
                self.assertTrue(any("must be a child" in error for error in self.errors(manifest)))

    def test_rejects_shared_port(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["cells"][1]["profile"]["server_port"] = manifest["cells"][0]["profile"]["server_port"]
        self.assertTrue(any("port" in error and "shared" in error for error in self.errors(manifest)))

    def test_rejects_published_cell_without_immutable_evidence(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        del manifest["cells"][4]["evidence"]
        self.assertTrue(any("published cells require immutable evidence" in error for error in self.errors(manifest)))

    def test_rejects_published_cell_without_verified_host_state(self) -> None:
        for mutation in ("missing", "no-published-host", "wrong-download-hash"):
            with self.subTest(mutation=mutation):
                manifest = copy.deepcopy(self.manifest)
                cell = manifest["cells"][4]
                if mutation == "missing":
                    del cell["hosting"]
                elif mutation == "no-published-host":
                    cell["hosting"]["modrinth"]["state"] = "submitted"
                    del cell["hosting"]["modrinth"]["version_number"]
                    del cell["hosting"]["modrinth"]["download_verified_sha256"]
                else:
                    cell["hosting"]["modrinth"]["download_verified_sha256"] = "0" * 64
                self.assertTrue(any("published" in error or "download_verified" in error for error in self.errors(manifest)))

    def test_rejects_arbitrary_publication_evidence_url(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        evidence = manifest["cells"][4]["evidence"][0]
        evidence["uri"] = f"https://example.invalid/{evidence['source_revision']}/unrelated"
        self.assertTrue(any("source-repository blob URL" in error for error in self.errors(manifest)))

    def test_rejects_project_url_prefix_impersonation(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["cells"][4]["hosting"]["modrinth"]["project_url"] += "-fork"
        self.assertTrue(any("official RingWorld modrinth project" in error for error in self.errors(manifest)))

    def test_rejects_terminal_state_with_pending_inputs(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        cell = manifest["cells"][0]
        cell["status"] = "failing"
        cell["evidence"] = copy.deepcopy(manifest["cells"][4]["evidence"])
        self.assertTrue(any("must not retain unresolved inputs" in error for error in self.errors(manifest)))

    def test_rejects_terminal_qualification_without_evidence_or_artifact(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        cell = manifest["cells"][0]
        cell["status"] = "passing"
        del cell["pending_inputs"]
        errors = self.errors(manifest)
        self.assertTrue(any("passing cells require an immutable artifact" in error for error in errors))
        self.assertTrue(any("passing cells require immutable evidence" in error for error in errors))

    def test_rejects_malformed_same_artifact_claim(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["cells"][4]["same_artifact_claim"] = {
            "group": "fabric-26.1.x",
            "sha256": manifest["cells"][4]["artifact"]["sha256"],
            "minecraft_versions": ["26.1", "26.1", "26.1.2"],
        }
        self.assertTrue(any("same_artifact_claim" in error for error in self.errors(manifest)))


if __name__ == "__main__":
    unittest.main()
