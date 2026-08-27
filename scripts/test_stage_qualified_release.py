#!/usr/bin/env python3
"""Focused pure/local tests for qualified release staging helpers."""

from __future__ import annotations

import json
import hashlib
from pathlib import Path
import tempfile
import unittest

from external_runtime_qualification_adapter import canonical_cells_from_manifest
from minecraft_qualification_model import QualificationPaths
from minecraft_support_contract import SupportContract, contract_from_manifest
from run_minecraft_qualification import load_manifest
from stage_qualified_release import QualifiedStageError, _load_config, _metadata, _render_changelog, validate_quick_matrix
from test_minecraft_qualification_evidence import passing_record


ROOT = Path(__file__).resolve().parents[1]
RUN_ID = "20260827T120000Z-0123456789ab"


class QualifiedReleaseStageTest(unittest.TestCase):
    @staticmethod
    def _write_26_2_quick(repository: Path) -> tuple[dict, SupportContract, dict[str, str]]:
        manifest = load_manifest(ROOT / "config/minecraft-version-matrix-26.2.json")
        contract = contract_from_manifest(manifest)
        canonical = canonical_cells_from_manifest(manifest["cells"])
        hashes: dict[str, str] = {}
        for cell in manifest["cells"]:
            paths = QualificationPaths.from_cell(repository, cell, RUN_ID)
            loader = cell["loader"]
            candidate = paths.run_root / f"frozen-candidates/{loader}/ringworld-qualification.jar"
            candidate.parent.mkdir(parents=True, exist_ok=True)
            candidate.write_bytes(("candidate-" + loader).encode())
            digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
            hashes[loader] = digest
            record = passing_record()
            record["cell"] = {field: canonical[cell["id"]][field] for field in (
                "id", "minecraft_version", "loader", "port", "world_config",
            )}
            identity = contract.range_identities()[loader]
            record["frozen_candidate"].update({  # type: ignore[index]
                "source_sha256": digest, "installed_sha256": digest, **identity,
            })
            record["runtime_inventory"][0]["sha256"] = digest  # type: ignore[index]
            record["same_file"] = {  # type: ignore[index]
                "group": contract.group + "-" + loader, "sha256": digest,
                "cell_ids": list(contract.cell_ids(loader)),
            }
            paths.evidence_directory.mkdir(parents=True, exist_ok=True)
            (paths.evidence_directory / "strict-terminal-evidence.json").write_text(json.dumps(record))
        return manifest, contract, hashes

    def test_config_is_exact_and_loader_metadata_is_separate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "release.json"
            source = Path("deploy/qualified/26.1.x-release.json")
            path.write_bytes(source.read_bytes())
            config = _load_config(path)
            changelog = _render_changelog(
                Path("deploy/qualified/26.1.x-changelog.md").read_text(),
                "fabric", "https://github.com/Delaser/RingWorld/commit/" + "a" * 40,
            )
            fabric_modrinth, fabric_curseforge = _metadata(config, "fabric", changelog)
            neo_modrinth, neo_curseforge = _metadata(config, "neoforge", changelog)
            self.assertEqual(["26.1", "26.1.1", "26.1.2"], fabric_modrinth["game_versions"])
            self.assertEqual("required", fabric_modrinth["dependencies"][0]["dependency_type"])
            self.assertEqual("requiredDependency", fabric_curseforge["relations"][0]["relation_type"])
            self.assertEqual([], neo_modrinth["dependencies"])
            self.assertEqual([], neo_curseforge["relations"])

    def test_rejects_unreviewed_version_and_missing_source_placeholder(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "release.json"
            config = json.loads(Path("deploy/qualified/26.1.x-release.json").read_text())
            config["game_versions"].append("26.2")
            path.write_text(json.dumps(config))
            with self.assertRaisesRegex(QualifiedStageError, "exactly match"):
                _load_config(path)
        with self.assertRaisesRegex(QualifiedStageError, "source placeholder"):
            _render_changelog("no source", "fabric", "https://example.invalid")

    def test_changelog_substitutions_are_complete(self) -> None:
        source_url = "https://github.com/Delaser/RingWorld/commit/" + "b" * 40
        rendered = _render_changelog(
            Path("deploy/qualified/26.1.x-changelog.md").read_text(), "neoforge", source_url,
        )
        self.assertIn("NeoForge", rendered)
        self.assertIn(source_url, rendered)
        self.assertNotIn("{{", rendered)

    def test_config_and_metadata_follow_a_single_version_candidate_group(self) -> None:
        contract = SupportContract("26.2", ("26.2",), ("26.2.0.69",))
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "release.json"
            config = json.loads(Path("deploy/qualified/26.1.x-release.json").read_text())
            config.update({"artifact_version": "1.2.0+mc26.2", "release_label": "1.2", "game_versions": ["26.2"]})
            path.write_text(json.dumps(config))
            loaded = _load_config(path, contract)
            modrinth, _ = _metadata(loaded, "fabric", "notes")
            self.assertIn("Minecraft 26.2", modrinth["name"])
            self.assertIn("RingWorld 1.2", modrinth["name"])
            config["game_versions"] = ["26.1"]
            path.write_text(json.dumps(config))
            with self.assertRaisesRegex(QualifiedStageError, "candidate group"):
                _load_config(path, contract)
            with self.assertRaisesRegex(QualifiedStageError, "reviewed support contract"):
                _load_config(path, "26.2")  # type: ignore[arg-type]

    def test_26_2_quick_matrix_requires_every_matching_passed_cell(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            # QualificationPaths resolves its root.  The public helper must
            # therefore also use that canonical root when it records evidence.
            repository = Path(temporary)
            manifest, contract, hashes = self._write_26_2_quick(repository)
            observed, records = validate_quick_matrix(repository, manifest, RUN_ID, contract)
            self.assertEqual(hashes, observed)
            self.assertEqual({"26.2-fabric", "26.2-neoforge"}, {record["cell"] for record in records})

            neo = next(cell for cell in manifest["cells"] if cell["loader"] == "neoforge")
            neo_paths = QualificationPaths.from_cell(repository, neo, RUN_ID)
            evidence = neo_paths.evidence_directory / "strict-terminal-evidence.json"
            evidence.unlink()
            with self.assertRaisesRegex(QualifiedStageError, "strict quick evidence"):
                validate_quick_matrix(repository, manifest, RUN_ID, contract)

            self._write_26_2_quick(repository)
            expected = canonical_cells_from_manifest(manifest["cells"])[neo["id"]]
            failed = {"schema_version": 1, "verdict": "FAIL", "cell": {
                field: expected[field] for field in ("id", "minecraft_version", "loader", "port", "world_config")
            }, "reason": "test"}
            evidence.write_text(json.dumps(failed))
            with self.assertRaisesRegex(QualifiedStageError, "does not bind"):
                validate_quick_matrix(repository, manifest, RUN_ID, contract)

            self._write_26_2_quick(repository)
            record = json.loads(evidence.read_text())
            wrong = "f" * 64
            record["frozen_candidate"]["source_sha256"] = wrong
            record["frozen_candidate"]["installed_sha256"] = wrong
            record["runtime_inventory"][0]["sha256"] = wrong
            record["same_file"]["sha256"] = wrong
            evidence.write_text(json.dumps(record))
            with self.assertRaisesRegex(QualifiedStageError, "does not bind"):
                validate_quick_matrix(repository, manifest, RUN_ID, contract)

            with self.assertRaisesRegex(QualifiedStageError, "does not match"):
                validate_quick_matrix(repository, manifest, RUN_ID, SupportContract("wrong", ("26.2",), ("26.2.0.69",)))
            with self.assertRaisesRegex(QualifiedStageError, "reviewed support contract"):
                validate_quick_matrix(repository, manifest, RUN_ID, "26.2")  # type: ignore[arg-type]


if __name__ == "__main__":
    unittest.main()
