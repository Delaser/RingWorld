#!/usr/bin/env python3
"""No-network tests for qualified host publication planning."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from publish_qualified_release import PublishPlanError, _authorization, publication_plan
from stage_qualified_release import _metadata


class QualifiedPublisherTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.stage = Path(self.temp.name) / "stage"
        self.stage.mkdir()
        (self.stage / ".ringworld-qualified-stage").write_text("generated\n")
        for loader in ("fabric", "neoforge"):
            folder = self.stage / loader
            folder.mkdir()
            jar = folder / ("ringworld-1.3.0+mc26.1.jar" if loader == "fabric"
                            else "ringworld-neoforge-1.3.0+mc26.1.jar")
            jar.write_bytes(("jar-" + loader).encode())
            source = {"revision": "a" * 40,
                      "url": "https://github.com/Delaser/RingWorld/commit/" + "a" * 40}
            manifest = {"format": 1, "generated": True, "upload_file_only": True,
                        "artifact_version": "1.3.0+mc26.1", "release_label": "1.3",
                        "loader": loader, "publication_action": "manual_owner_authorization_required",
                        "upload_file": jar.name, "hashes": {"sha256": hashlib.sha256(jar.read_bytes()).hexdigest()},
                        "source": source, "game_versions": ["26.1", "26.1.1", "26.1.2"]}
            (folder / "STAGING-MANIFEST.json").write_text(json.dumps(manifest))
            notes = "Changes. Corresponding source: " + source["url"]
            (folder / "CHANGELOG.md").write_text(notes)
            config = {"release_label": "1.3", "game_versions": manifest["game_versions"],
                      "modrinth": {"project_id": "ringworld", "fabric_api_project_id": "P7dR8mSH",
                                   "fabric_version_number": "1.3.0-fabric+mc26.1",
                                   "neoforge_version_number": "1.3.0-neoforge+mc26.1"},
                      "curseforge": {"project_id": 1645598, "fabric_api_project_id": 306612}}
            modrinth, curseforge = _metadata(config, loader, notes)
            (folder / "MODRINTH-VERSION.json").write_text(json.dumps(modrinth))
            (folder / "CURSEFORGE-UPLOAD.json").write_text(json.dumps(curseforge))

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_dry_run_plans_unlisted_modrinth_and_manual_curseforge(self) -> None:
        modrinth = publication_plan(self.stage, "modrinth", "fabric")
        self.assertTrue(modrinth["dry_run"])
        self.assertEqual("unlisted", modrinth["metadata"]["status"])
        curseforge = publication_plan(self.stage, "curseforge", "neoforge")
        self.assertTrue(curseforge["metadata"]["isMarkedForManualRelease"])
        self.assertEqual(["Client", "Server", "26.1", "26.1.1", "26.1.2", "NeoForge"],
                         curseforge["metadata"]["gameVersionNames"])
        self.assertNotIn("relations", curseforge["metadata"])

    def test_curseforge_preserves_required_fabric_api(self) -> None:
        path = self.stage / "fabric" / "CURSEFORGE-UPLOAD.json"
        metadata = json.loads(path.read_text())
        metadata["relations"] = [{"project_id": 306612,
                                   "relation_type": "requiredDependency"}]
        path.write_text(json.dumps(metadata))
        plan = publication_plan(self.stage, "curseforge", "fabric")
        self.assertEqual({"projects": [{"projectID": 306612, "slug": "fabric-api",
                                        "type": "requiredDependency"}]},
                         plan["metadata"]["relations"])

    def test_curseforge_rejects_unreviewed_dependency(self) -> None:
        path = self.stage / "fabric" / "CURSEFORGE-UPLOAD.json"
        metadata = json.loads(path.read_text())
        metadata["relations"] = [{"project_id": 123, "relation_type": "requiredDependency"}]
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(PublishPlanError, "metadata"):
            publication_plan(self.stage, "curseforge", "fabric")

    def test_rejects_host_metadata_drift(self) -> None:
        for host, name, field, value in (
            ("modrinth", "MODRINTH-VERSION.json", "loaders", ["neoforge"]),
            ("modrinth", "MODRINTH-VERSION.json", "game_versions", ["26.3"]),
            ("modrinth", "MODRINTH-VERSION.json", "project_id", "wrong-project"),
            ("modrinth", "MODRINTH-VERSION.json", "version_number", "1.0.0-fabric+mc26.1"),
            ("modrinth", "MODRINTH-VERSION.json", "dependencies", []),
            ("curseforge", "CURSEFORGE-UPLOAD.json", "project_id", 123),
            ("curseforge", "CURSEFORGE-UPLOAD.json", "game_versions", ["26.3"]),
            ("curseforge", "CURSEFORGE-UPLOAD.json", "relations", []),
            ("curseforge", "CURSEFORGE-UPLOAD.json", "changelog", "unreviewed notes"),
        ):
            with self.subTest(host=host, field=field):
                path = self.stage / "fabric" / name
                original = path.read_text()
                metadata = json.loads(original)
                metadata[field] = value
                path.write_text(json.dumps(metadata))
                try:
                    with self.assertRaises(PublishPlanError):
                        publication_plan(self.stage, host, "fabric")
                finally:
                    path.write_text(original)

    def test_rejects_changelog_without_corresponding_source(self) -> None:
        path = self.stage / "fabric" / "CHANGELOG.md"
        path.write_text("notes without source")
        with self.assertRaises(PublishPlanError):
            publication_plan(self.stage, "modrinth", "fabric")

    def test_rejects_legacy_or_incomplete_stage(self) -> None:
        path = self.stage / "fabric" / "STAGING-MANIFEST.json"
        original = json.loads(path.read_text())
        for field, value in (("format", 2), ("generated", False),
                             ("source", {"revision": "bad", "url": "https://github.com/Delaser/RingWorld/commit/bad"})):
            with self.subTest(field=field):
                path.write_text(json.dumps({**original, field: value}))
                with self.assertRaises(PublishPlanError):
                    publication_plan(self.stage, "modrinth", "fabric")

    def test_rejects_changed_jar_and_wrong_authorization(self) -> None:
        plan = publication_plan(self.stage, "modrinth", "fabric")
        Path(plan["jar_path"]).write_bytes(b"changed")
        with self.assertRaisesRegex(PublishPlanError, "hash"):
            publication_plan(self.stage, "modrinth", "fabric")
        plan = publication_plan(self.stage, "curseforge", "neoforge")
        auth = Path(self.temp.name) / "auth.json"
        auth.write_text(json.dumps({
            "format": 1, "action": "publish-qualified-release", "host": "curseforge",
            "loader": "fabric", "source_revision": plan["source_revision"],
            "jar_sha256": plan["jar_sha256"],
            "expires_utc": (datetime.now(timezone.utc) + timedelta(minutes=10)).isoformat().replace("+00:00", "Z"),
        }))
        with self.assertRaisesRegex(PublishPlanError, "does not bind"):
            _authorization(auth, plan)


if __name__ == "__main__":
    unittest.main()
