#!/usr/bin/env python3

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
import zipfile

try:
    from scripts.stage_modrinth_release import VerificationError, stage_release
except ModuleNotFoundError:
    from stage_modrinth_release import VerificationError, stage_release


VERSION = "0.2.0+mc26.1.2"
LICENSE = b"Mozilla Public License Version 2.0\n"
REVISION = "3f6cb9ee26578bd395cde9469977377a70314aad"


def release_config() -> dict:
    return {
        "project": {"slug": "ringworld", "project_type": "mod", "client_side": "required", "server_side": "required", "license_id": "MPL-2.0"},
        "version": {"version_number": VERSION, "version_type": "alpha", "game_versions": ["26.1.2"], "loaders": ["fabric"], "environment": "client_and_server", "dependencies": [{"project_id": "P7dR8mSH", "dependency_type": "required"}], "featured": False},
        "fabric": {"mod_id": "ringworld", "author": "Delaser", "homepage": "https://andwhatnotstudio.com/ringworld/", "environment": "*", "fabric_loader": ">=0.19.3", "minecraft": "26.1.2", "java": ">=25", "fabric_api": "*"},
        "source": {"revision": REVISION, "url": f"https://github.com/Delaser/RingWorld/commit/{REVISION}"},
    }


def write_jar(path: Path, *, minecraft: str = "26.1.2", identifier: str = "MPL-2.0", environment: str = "*", sensitive: str | None = None, sensitive_content: str = "secret", embedded_license: bytes = LICENSE) -> None:
    metadata = {"schemaVersion": 1, "id": "ringworld", "version": VERSION, "authors": ["Delaser"], "contact": {"homepage": "https://andwhatnotstudio.com/ringworld/"}, "license": identifier, "environment": environment, "depends": {"fabricloader": ">=0.19.3", "minecraft": minecraft, "java": ">=25", "fabric-api": "*"}}
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        archive.writestr("LICENSE-RINGWORLD.txt", embedded_license)
        archive.writestr("ringworld.mixins.json", "{}")
        archive.writestr("ringworld.client.mixins.json", "{}")
        archive.writestr("dev/ringworld/RingWorld.class", b"compiled")
        if sensitive:
            archive.writestr(sensitive, sensitive_content)


class ModrinthStagingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.config, self.description, self.changelog, self.license = (self.root / name for name in ("release.json", "description.md", "changelog.md", "LICENSE"))
        self.jar = self.root / f"ringworld-{VERSION}.jar"
        self.config.write_text(json.dumps(release_config()), encoding="utf-8")
        self.description.write_text("Description\n", encoding="utf-8")
        self.changelog.write_text("Changes\n", encoding="utf-8")
        self.license.write_bytes(LICENSE)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def stage(self) -> Path:
        return stage_release(self.jar, self.config, self.description, self.changelog, self.license, self.root / "out")

    def test_stages_exactly_one_runtime_jar_with_checksum_and_source(self) -> None:
        write_jar(self.jar)
        target = self.stage()
        manifest = json.loads((target / "STAGING-MANIFEST.json").read_text())
        self.assertEqual(list(target.glob("*.jar")), [target / self.jar.name])
        self.assertTrue(manifest["upload_file_only"])
        self.assertEqual(manifest["source"]["revision"], REVISION)
        self.assertIn(manifest["hashes"]["sha256"], (target / "SHA256SUMS.txt").read_text())

    def test_rejects_wrong_minecraft_dependency(self) -> None:
        write_jar(self.jar, minecraft="26.1.1")
        with self.assertRaisesRegex(VerificationError, "depends.minecraft"):
            self.stage()

    def test_rejects_stale_license_identifier(self) -> None:
        write_jar(self.jar, identifier="MIT")
        with self.assertRaisesRegex(VerificationError, "expected licence"):
            self.stage()

    def test_rejects_missing_or_wrong_embedded_license(self) -> None:
        write_jar(self.jar, embedded_license=b"wrong")
        with self.assertRaisesRegex(VerificationError, "embedded licence"):
            self.stage()

    def test_rejects_client_only_environment(self) -> None:
        write_jar(self.jar, environment="client")
        with self.assertRaisesRegex(VerificationError, "environment"):
            self.stage()

    def test_rejects_credentials_and_source_content(self) -> None:
        for name, expected, content in (("accounts.json", "sensitive runtime", "secret"), ("dev/ringworld/Source.java", "source file", "secret"), ("META-INF/private.pem", "private-key", "-----BEGIN PRIVATE KEY-----\\nsecret")):
            with self.subTest(name=name):
                write_jar(self.jar, sensitive=name, sensitive_content=content)
                with self.assertRaisesRegex(VerificationError, expected):
                    self.stage()

    def test_rejects_source_and_dev_artifacts(self) -> None:
        for suffix in ("-sources.jar", "-dev.jar"):
            with self.subTest(suffix=suffix):
                jar = self.root / f"ringworld-{VERSION}{suffix}"
                write_jar(jar)
                with self.assertRaisesRegex(VerificationError, "non-runtime artifact"):
                    stage_release(jar, self.config, self.description, self.changelog, self.license, self.root / "out")

    def test_rejects_invalid_source_revision(self) -> None:
        write_jar(self.jar)
        config = release_config()
        config["source"]["revision"] = "not-a-commit"
        self.config.write_text(json.dumps(config), encoding="utf-8")
        with self.assertRaisesRegex(VerificationError, "source.revision"):
            self.stage()

    def test_refuses_to_replace_unrecognized_directory(self) -> None:
        write_jar(self.jar)
        target = self.root / "out" / VERSION / "fabric"
        target.mkdir(parents=True)
        (target / "user-file").write_text("keep", encoding="utf-8")
        with self.assertRaisesRegex(VerificationError, "unrecognized directory"):
            self.stage()


if __name__ == "__main__":
    unittest.main()
