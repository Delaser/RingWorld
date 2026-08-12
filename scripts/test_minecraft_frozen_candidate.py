#!/usr/bin/env python3
"""Pure tests for frozen 26.1.x candidate identity and range evidence."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from minecraft_frozen_candidate import (
    EXPECTED_VERSIONS,
    FrozenCandidateError,
    inspect_frozen_candidate,
    verify_same_file_coverage,
)


def write_candidate(path: Path, loader: str, *, minecraft_range: str | None = None,
                    loader_range: str | None = None, source_version: str = "26.1") -> None:
    version = f"0.0.0-qualification+mc{source_version}"
    label = f"qualification-{source_version}-{loader}"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("ringworld-build.properties", f"artifactVersion={version}\nreleaseLabel={label}\n")
        if loader == "fabric":
            archive.writestr("fabric.mod.json", json.dumps({
                "id": "ringworld", "version": version,
                "depends": {"minecraft": minecraft_range or ">=26.1 <=26.1.2"},
            }))
        else:
            archive.writestr("META-INF/neoforge.mods.toml", "\n".join((
                'license="MPL-2.0"',
                '[[mods]]', 'modId="ringworld"', f'version="{version}"',
                '[[dependencies.ringworld]]', 'modId="neoforge"',
                f'versionRange="{loader_range or "[26.1.0.19-beta,26.1.2.87]"}"',
                '[[dependencies.ringworld]]', 'modId="minecraft"',
                f'versionRange="{minecraft_range or "[26.1,26.1.2]"}"',
            )))


class FrozenCandidateTest(unittest.TestCase):
    def test_valid_fabric_and_neoforge_candidates_cover_all_versions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for loader in ("fabric", "neoforge"):
                path = root / f"{loader}.jar"
                write_candidate(path, loader)
                result = inspect_frozen_candidate(path, loader)
                self.assertEqual(EXPECTED_VERSIONS, result.covered_minecraft_versions)
                self.assertEqual(64, len(result.sha256))

    def test_rejects_wrong_minecraft_or_neoforge_range(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fabric = root / "fabric.jar"
            write_candidate(fabric, "fabric", minecraft_range=">=26.1 <=26.1.3")
            with self.assertRaises(FrozenCandidateError):
                inspect_frozen_candidate(fabric, "fabric")
            neo = root / "neo.jar"
            write_candidate(neo, "neoforge", loader_range="[26.1.0.19-beta,26.1.2.88]")
            with self.assertRaises(FrozenCandidateError):
                inspect_frozen_candidate(neo, "neoforge")

    def test_rejects_candidate_built_from_newer_abi(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fabric.jar"
            write_candidate(path, "fabric", source_version="26.1.2")
            with self.assertRaises(FrozenCandidateError):
                inspect_frozen_candidate(path, "fabric")

    def test_same_file_coverage_requires_one_path_and_hash_for_three_cells(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fabric.jar"
            write_candidate(path, "fabric")
            result = inspect_frozen_candidate(path, "fabric")
            evidence = {f"{version}-fabric": result for version in EXPECTED_VERSIONS}
            coverage = verify_same_file_coverage("fabric", evidence)
            self.assertEqual(result.sha256, coverage.sha256)
            changed = dict(evidence)
            changed["26.1.2-fabric"] = type(result)(
                str(Path(temporary) / "rebuilt.jar"), result.loader, "0" * 64,
                result.artifact_version, result.release_label, result.minecraft_range,
                result.loader_range, result.covered_minecraft_versions,
            )
            with self.assertRaises(FrozenCandidateError):
                verify_same_file_coverage("fabric", changed)

    def test_same_file_coverage_rejects_missing_or_mixed_loader_cell(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fabric.jar"
            write_candidate(path, "fabric")
            result = inspect_frozen_candidate(path, "fabric")
            with self.assertRaises(FrozenCandidateError):
                verify_same_file_coverage("fabric", {"26.1-fabric": result})


if __name__ == "__main__":
    unittest.main()
