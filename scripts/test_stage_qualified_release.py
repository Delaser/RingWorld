#!/usr/bin/env python3
"""Focused pure/local tests for qualified release staging helpers."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from stage_qualified_release import QualifiedStageError, _load_config, _metadata, _render_changelog


class QualifiedReleaseStageTest(unittest.TestCase):
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
            self.assertEqual(306612, fabric_curseforge["relations"][0]["project_id"])
            self.assertEqual("fabric-api", fabric_curseforge["relations"][0]["slug"])
            self.assertEqual([], neo_modrinth["dependencies"])
            self.assertEqual([], neo_curseforge["relations"])

    def test_rejects_unreviewed_version_and_missing_source_placeholder(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "release.json"
            config = json.loads(Path("deploy/qualified/26.1.x-release.json").read_text())
            config["game_versions"].append("26.2")
            path.write_text(json.dumps(config))
            with self.assertRaisesRegex(QualifiedStageError, "exactly the qualified"):
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


if __name__ == "__main__":
    unittest.main()
