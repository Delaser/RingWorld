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


class QualifiedPublisherTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.stage = Path(self.temp.name) / "stage"
        self.stage.mkdir()
        (self.stage / ".ringworld-qualified-stage").write_text("generated\n")
        for loader in ("fabric", "neoforge"):
            folder = self.stage / loader
            folder.mkdir()
            jar = folder / f"ringworld-{loader}.jar"
            jar.write_bytes(("jar-" + loader).encode())
            source = {"revision": "a" * 40,
                      "url": "https://github.com/Delaser/RingWorld/commit/" + "a" * 40}
            manifest = {"loader": loader, "publication_action": "manual_owner_authorization_required",
                        "upload_file": jar.name, "hashes": {"sha256": hashlib.sha256(jar.read_bytes()).hexdigest()},
                        "source": source, "game_versions": ["26.1", "26.1.1", "26.1.2"]}
            (folder / "STAGING-MANIFEST.json").write_text(json.dumps(manifest))
            (folder / "MODRINTH-VERSION.json").write_text(json.dumps({
                "project_id": "ringworld", "name": "release", "version_number": "1.1", "version_type": "release",
                "featured": True, "game_versions": manifest["game_versions"], "loaders": [loader],
                "dependencies": [], "changelog": "source",
            }))
            relations = ([{"project_id": 306612, "slug": "fabric-api",
                           "relation_type": "requiredDependency"}]
                         if loader == "fabric" else [])
            (folder / "CURSEFORGE-UPLOAD.json").write_text(json.dumps({
                "project_id": 1645598, "display_name": "release", "release_type": "release",
                "game_versions": manifest["game_versions"], "loader": loader, "relations": relations,
                "changelog": "source", "execution": "manual_owner_authorization_required",
            }))

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
        fabric_curseforge = publication_plan(self.stage, "curseforge", "fabric")
        self.assertEqual({"projects": [{
            "projectID": 306612,
            "slug": "fabric-api",
            "type": "requiredDependency",
        }]}, fabric_curseforge["metadata"]["relations"])

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
