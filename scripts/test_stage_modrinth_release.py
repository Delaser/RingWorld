#!/usr/bin/env python3

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
import zipfile
from types import SimpleNamespace
from unittest.mock import patch

try:
    import scripts.stage_modrinth_release as stage_tool
except ModuleNotFoundError:
    import stage_modrinth_release as stage_tool

try:
    from scripts.stage_modrinth_release import (
        VerificationError, current_public_source, java_major, require_build_java,
        SHARED_CRITICAL_ENTRIES, dual_build_command, stage_release,
        validate_candidate_pair, validate_release_config,
    )
except ModuleNotFoundError:
    from stage_modrinth_release import (
        VerificationError, current_public_source, java_major, require_build_java,
        SHARED_CRITICAL_ENTRIES, dual_build_command, stage_release,
        validate_candidate_pair, validate_release_config,
    )


VERSION = "0.2.0+mc26.1.2"
LICENSE = b"Mozilla Public License Version 2.0\n"
REVISION = "3f6cb9ee26578bd395cde9469977377a70314aad"


def release_config(loader: str = "fabric") -> dict:
    public_version = f"0.2.0-alpha.4-{loader}+mc26.1.2"
    public_loader = "Fabric" if loader == "fabric" else "NeoForge"
    config = {
        "project": {"slug": "ringworld", "project_type": "mod", "client_side": "required", "server_side": "required", "license_id": "MPL-2.0"},
        "version": {"name": f"RingWorld 0.2.0 alpha 4 for Minecraft 26.1.2 ({public_loader})", "version_number": public_version, "artifact_version": VERSION, "version_type": "alpha", "game_versions": ["26.1.2"], "loaders": [loader], "environment": "client_and_server", "dependencies": [{"project_id": "P7dR8mSH", "dependency_type": "required"}] if loader == "fabric" else [], "featured": False},
        "source": {"repository": "https://github.com/Delaser/RingWorld"},
    }
    if loader == "fabric":
        config["fabric"] = {"mod_id": "ringworld", "author": "Delaser", "homepage": "https://andwhatnotstudio.com/ringworld/", "environment": "*", "fabric_loader": ">=0.19.3", "minecraft": "26.1.2", "java": ">=25", "fabric_api": "*"}
    else:
        config["neoforge"] = {"mod_id": "ringworld", "author": "Delaser", "homepage": "https://modrinth.com/mod/ringworld", "minecraft": "26.1.2", "neoforge": "[26.1.2.87,)"}
    return config


def write_shared_contract(archive: zipfile.ZipFile, *, mutation: str | None = None) -> None:
    for name in SHARED_CRITICAL_ENTRIES:
        content = b"{}" if name.endswith(".json") else ("shared:" + name).encode("utf-8")
        if name == mutation:
            content = b"deliberately-different"
        archive.writestr(name, content)
    archive.writestr("assets/minecraft/shaders/core/terrain.vsh", b"shared shader")


def write_jar(path: Path, *, minecraft: str = "26.1.2", identifier: str = "MPL-2.0", environment: str = "*", compatibility_api: int = 1, sensitive: str | None = None, sensitive_content: str = "secret", embedded_license: bytes = LICENSE, contract_mutation: str | None = None) -> None:
    metadata = {"schemaVersion": 1, "id": "ringworld", "version": VERSION, "authors": ["Delaser"], "contact": {"homepage": "https://andwhatnotstudio.com/ringworld/"}, "custom": {"ringworld:compatibility_api": compatibility_api}, "license": identifier, "environment": environment, "depends": {"fabricloader": ">=0.19.3", "minecraft": minecraft, "java": ">=25", "fabric-api": "*"}}
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        archive.writestr("LICENSE-RINGWORLD.txt", embedded_license)
        write_shared_contract(archive, mutation=contract_mutation)
        archive.writestr("dev/ringworld/RingWorld.class", b"compiled")
        if sensitive:
            archive.writestr(sensitive, sensitive_content)


def write_neoforge_jar(path: Path, *, minecraft: str = "26.1.2", neoforge: str = "[26.1.2.87,)", identifier: str = "MPL-2.0", mod_id: str = "ringworld", embedded_license: bytes = LICENSE, contract_mutation: str | None = None) -> None:
    metadata = f'''license="{identifier}"

[[mods]]
modId="{mod_id}"
version="{VERSION}"
displayName="RingWorld"
displayURL="https://modrinth.com/mod/ringworld"
authors="Delaser"
description=''' + "'''" + '''
RingWorld staging test descriptor.
''' + "'''" + f'''

[[mixins]]
config="ringworld.mixins.json"

[[mixins]]
config="ringworld.client.mixins.json"

[[dependencies.ringworld]]
modId="neoforge"
type="required"
versionRange="{neoforge}"

[[dependencies.ringworld]]
modId="minecraft"
type="required"
versionRange="[{minecraft}]"
'''
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("META-INF/neoforge.mods.toml", metadata)
        archive.writestr("LICENSE-RINGWORLD.txt", embedded_license)
        write_shared_contract(archive, mutation=contract_mutation)
        archive.writestr("dev/ringworld/RingWorld.class", b"compiled")


class ModrinthStagingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.config, self.neo_config, self.description, self.changelog, self.license = (self.root / name for name in ("release.json", "release-neoforge.json", "description.md", "changelog.md", "LICENSE"))
        self.jar = self.root / f"ringworld-{VERSION}.jar"
        self.neo_jar = self.root / f"ringworld-neoforge-{VERSION}.jar"
        self.config.write_text(json.dumps(release_config()), encoding="utf-8")
        self.neo_config.write_text(json.dumps(release_config("neoforge")), encoding="utf-8")
        self.description.write_text(
            "Description\n\nSource: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
            encoding="utf-8",
        )
        self.changelog.write_text(
            "Changes\n\nSource: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
            encoding="utf-8",
        )
        self.license.write_bytes(LICENSE)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def stage(self) -> Path:
        return stage_release(
            self.jar, self.config, self.description, self.changelog, self.license,
            self.root / "out", {"revision": REVISION,
            "url": f"https://github.com/Delaser/RingWorld/commit/{REVISION}"},
        )

    def stage_neoforge(self) -> Path:
        return stage_release(
            self.neo_jar, self.neo_config, self.description, self.changelog, self.license,
            self.root / "out", {"revision": REVISION,
            "url": f"https://github.com/Delaser/RingWorld/commit/{REVISION}"}, loader="neoforge",
        )

    def test_stages_exactly_one_runtime_jar_with_checksum_and_source(self) -> None:
        write_jar(self.jar)
        target = self.stage()
        manifest = json.loads((target / "STAGING-MANIFEST.json").read_text())
        self.assertEqual(list(target.glob("*.jar")), [target / self.jar.name])
        self.assertTrue(manifest["upload_file_only"])
        self.assertEqual(manifest["source"]["revision"], REVISION)
        self.assertEqual(manifest["public_version"],
                         "0.2.0-alpha.4-fabric+mc26.1.2")
        self.assertIn(manifest["hashes"]["sha256"], (target / "SHA256SUMS.txt").read_text())
        source_url = f"https://github.com/Delaser/RingWorld/commit/{REVISION}"
        self.assertIn(source_url, (target / "PROJECT_DESCRIPTION.md").read_text())
        self.assertIn(source_url, (target / "CHANGELOG.md").read_text())
        self.assertNotIn("{{RINGWORLD_CORRESPONDING_SOURCE_URL}}",
                         (target / "PROJECT_DESCRIPTION.md").read_text())

    def test_stages_neoforge_in_a_separate_loader_directory(self) -> None:
        write_neoforge_jar(self.neo_jar)
        target = self.stage_neoforge()
        manifest = json.loads((target / "STAGING-MANIFEST.json").read_text())
        self.assertEqual(target, self.root / "out" / VERSION / "neoforge")
        self.assertEqual(list(target.glob("*.jar")), [target / self.neo_jar.name])
        self.assertEqual(manifest["loader"], "neoforge")
        self.assertEqual(manifest["public_version"],
                         "0.2.0-alpha.4-neoforge+mc26.1.2")
        self.assertEqual(manifest["source"]["revision"], REVISION)
        source_url = f"https://github.com/Delaser/RingWorld/commit/{REVISION}"
        self.assertIn(source_url, (target / "PROJECT_DESCRIPTION.md").read_text())
        self.assertIn(source_url, (target / "CHANGELOG.md").read_text())

    def test_rejects_ambiguous_or_wrong_loader_public_version(self) -> None:
        for loader, public_version in (
                ("fabric", VERSION),
                ("fabric", "0.2.0-alpha.4-neoforge+mc26.1.2"),
                ("neoforge", "0.2.0-alpha.4-fabric+mc26.1.2")):
            with self.subTest(loader=loader, public_version=public_version):
                config = release_config(loader)
                config["version"]["version_number"] = public_version
                with self.assertRaisesRegex(VerificationError, "version.version_number"):
                    validate_release_config(config, loader)

    def test_rejects_missing_or_mismatched_artifact_version(self) -> None:
        for artifact_version in (None, "", "0.2.1+mc26.1.2"):
            with self.subTest(artifact_version=artifact_version):
                config = release_config("fabric")
                if artifact_version is None:
                    del config["version"]["artifact_version"]
                else:
                    config["version"]["artifact_version"] = artifact_version
                with self.assertRaisesRegex(VerificationError, "version.artifact_version"):
                    validate_release_config(config, "fabric")

    def test_rejects_absent_or_duplicate_public_source_placeholder(self) -> None:
        write_jar(self.jar)
        for path, content in (
                (self.description, "Description without source\n"),
                (self.changelog, "Changes without source\n"),
                (self.description,
                 "One {{RINGWORLD_CORRESPONDING_SOURCE_URL}} two "
                 "{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n")):
            with self.subTest(path=path.name, content=content):
                original = path.read_text(encoding="utf-8")
                path.write_text(content, encoding="utf-8")
                with self.assertRaisesRegex(VerificationError, "exactly one.*placeholder"):
                    self.stage()
                path.write_text(original, encoding="utf-8")

    def test_rejects_hard_coded_or_unverified_public_source_url(self) -> None:
        write_jar(self.jar)
        for content in (
                "Source: https://github.com/Delaser/RingWorld/commit/" + REVISION
                + "\n{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
                "Source revision: " + REVISION
                + "\n{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
                "Source revision: " + REVISION.upper()
                + "\n{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
                "Source revision: " + REVISION[:7].upper()
                + "\n{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
                "Source: https://GitHub.com/Delaser/RingWorld/tree/" + REVISION[:7].upper()
                + "\n{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
                "Source: https://github.com/Delaser/RingWorld/blob/" + REVISION[:7]
                + "/deploy/modrinth/project-description.md\n"
                + "\n{{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n"):
            with self.subTest(content=content):
                self.description.write_text(content, encoding="utf-8")
                with self.assertRaisesRegex(VerificationError, "must not hard-code"):
                    self.stage()
        self.description.write_text(
            "Source: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n", encoding="utf-8",
        )
        with self.assertRaisesRegex(VerificationError, "source URL"):
            stage_release(
                self.jar, self.config, self.description, self.changelog,
                self.license, self.root / "out",
                {"revision": REVISION, "url": "https://example.invalid/commit/" + REVISION},
            )

    def test_allows_normal_release_versions_and_non_revision_hex_word(self) -> None:
        write_jar(self.jar)
        self.description.write_text(
            "RingWorld 0.2.0+mc26.1.2 requires Java 25; its defaced cobblestone rim "
            "supports a 16384-block ring.\n"
            "Source: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}\n",
            encoding="utf-8",
        )
        target = self.stage()
        self.assertTrue((target / "PROJECT_DESCRIPTION.md").is_file())

    def test_validates_matching_dual_loader_contract_and_rejects_mismatch(self) -> None:
        write_jar(self.jar)
        write_neoforge_jar(self.neo_jar)
        validate_candidate_pair(self.jar, self.config, self.neo_jar, self.neo_config, self.license)
        write_neoforge_jar(self.neo_jar, contract_mutation="dev/ringworld/world/RingGeometry.class")
        with self.assertRaisesRegex(VerificationError, "shared contract entry differs.*RingGeometry.class"):
            validate_candidate_pair(self.jar, self.config, self.neo_jar, self.neo_config, self.license)

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

    def test_rejects_stale_compatibility_api_metadata(self) -> None:
        write_jar(self.jar, compatibility_api=0)
        with self.assertRaisesRegex(VerificationError, "compatibility_api"):
            self.stage()

    def test_rejects_neoforge_mismatched_metadata_and_fabric_dependency(self) -> None:
        write_neoforge_jar(self.neo_jar, minecraft="26.1.1")
        with self.assertRaisesRegex(VerificationError, "minecraft versionRange"):
            self.stage_neoforge()
        write_neoforge_jar(self.neo_jar, neoforge="[26.1.2.86,)")
        with self.assertRaisesRegex(VerificationError, "neoforge versionRange"):
            self.stage_neoforge()
        write_neoforge_jar(self.neo_jar, mod_id="othermod")
        with self.assertRaisesRegex(VerificationError, "ringworld mod"):
            self.stage_neoforge()
        config = release_config("neoforge")
        config["version"]["dependencies"] = [{"project_id": "P7dR8mSH", "dependency_type": "required"}]
        with self.assertRaisesRegex(VerificationError, "no external dependencies"):
            validate_release_config(config)

    def test_rejects_credentials_and_source_content(self) -> None:
        for name, expected, content in (
                ("accounts.json", "credential/runtime|sensitive runtime", "secret"),
                ("credentials.json", "credential/runtime|sensitive runtime", "secret"),
                ("token.txt", "credential/runtime|sensitive runtime", "secret"),
                ("keys/release.p12", "credential/runtime|sensitive runtime", "secret"),
                ("dev/ringworld/Source.java", "source (?:artifact|file)", "secret"),
                ("META-INF/private.pem", "private-key", "-----BEGIN PRIVATE KEY-----\\nsecret")):
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
                    stage_release(
                        jar, self.config, self.description, self.changelog,
                        self.license, self.root / "out", {"revision": REVISION,
                        "url": f"https://github.com/Delaser/RingWorld/commit/{REVISION}"},
                    )

    def test_rejects_wrong_public_source_repository(self) -> None:
        write_jar(self.jar)
        config = release_config()
        config["source"]["repository"] = "https://example.invalid/not-ringworld"
        self.config.write_text(json.dumps(config), encoding="utf-8")
        with self.assertRaisesRegex(VerificationError, "source.repository"):
            self.stage()

    def test_rejects_invalid_generated_source_revision(self) -> None:
        write_jar(self.jar)
        with self.assertRaisesRegex(VerificationError, "source revision"):
            stage_release(
                self.jar, self.config, self.description, self.changelog,
                self.license, self.root / "out",
                {"revision": "not-a-commit", "url": "https://example.invalid"},
            )

    def test_requires_clean_pushed_https_public_source(self) -> None:
        values = {
            ("git", "rev-parse", "--verify", "HEAD"): REVISION,
            ("git", "remote", "get-url", "origin"): "https://github.com/Delaser/RingWorld.git",
            ("git", "symbolic-ref", "--quiet", "--short", "HEAD"): "codex/release",
            ("git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"): "origin/codex/release",
            ("git", "rev-parse", "--verify", "@{upstream}"): REVISION,
            ("git", "status", "--porcelain", "--untracked-files=all"): "",
        }

        def runner(arguments, _root):
            return values[tuple(arguments)]

        self.assertEqual(current_public_source(self.root, runner)["revision"], REVISION)
        for changed, expected in (
                (("git", "status", "--porcelain", "--untracked-files=all"), "dirty source"),
                (("git", "remote", "get-url", "origin"), "origin must"),
                (("git", "rev-parse", "--verify", "@{upstream}"), "pushed origin upstream")):
            with self.subTest(changed=changed):
                prior = values[changed]
                values[changed] = "different" if changed[-1] == "@{upstream}" else "dirty"
                with self.assertRaisesRegex(VerificationError, expected):
                    current_public_source(self.root, runner)
                values[changed] = prior

        def no_upstream(arguments, root):
            if arguments[-1] == "@{upstream}" and "--abbrev-ref" in arguments:
                raise subprocess.CalledProcessError(1, arguments)
            return runner(arguments, root)

        with self.assertRaisesRegex(VerificationError, "clean checkout"):
            current_public_source(self.root, no_upstream)

    def test_refuses_to_replace_unrecognized_directory(self) -> None:
        write_jar(self.jar)
        target = self.root / "out" / VERSION / "fabric"
        target.mkdir(parents=True)
        (target / "user-file").write_text("keep", encoding="utf-8")
        with self.assertRaisesRegex(VerificationError, "unrecognized directory"):
            self.stage()

    def test_build_preflight_requires_java_25(self) -> None:
        def result(version: str, returncode: int = 0):
            return subprocess.CompletedProcess(["java", "-version"], returncode, "", version)

        self.assertEqual(java_major('openjdk version "25.0.4" 2026-07-21 LTS'), 25)
        self.assertEqual(java_major('java version "1.8.0_472"'), 8)
        self.assertIn("25.0.4", require_build_java(lambda *args, **kwargs: result('openjdk version "25.0.4"')))
        with self.assertRaisesRegex(VerificationError, "Java 25 is required.*active runtime"):
            require_build_java(lambda *args, **kwargs: result('openjdk version "21.0.11"'))
        with self.assertRaisesRegex(VerificationError, "could not identify"):
            require_build_java(lambda *args, **kwargs: result("unexpected output"))
        with self.assertRaisesRegex(VerificationError, "java -version failed"):
            require_build_java(lambda *args, **kwargs: result("", 1))

    def test_dual_build_command_cleans_tests_and_builds_both_loaders(self) -> None:
        command = dual_build_command()
        self.assertEqual(command[0], "./gradlew")
        self.assertIn("clean", command)
        self.assertIn("test", command)
        self.assertIn("build", command)
        self.assertIn(":neoforge:clean", command)
        self.assertIn(":neoforge:test", command)
        self.assertIn(":neoforge:build", command)

    def test_cli_always_runs_the_clean_dual_build_gate(self) -> None:
        write_jar(self.jar)
        write_neoforge_jar(self.neo_jar)
        args = SimpleNamespace(
            loader="fabric", build=False, output_root=self.root / "out",
        )
        source = {"revision": REVISION,
                  "url": f"https://github.com/Delaser/RingWorld/commit/{REVISION}"}
        with patch.object(stage_tool, "parse_args", return_value=args), \
                patch.object(stage_tool, "current_public_source", return_value=source), \
                patch.object(stage_tool, "require_build_java") as require_java, \
                patch.object(stage_tool, "run_dual_build") as run_build, \
                patch.object(stage_tool, "FABRIC_JAR", self.jar), \
                patch.object(stage_tool, "NEOFORGE_JAR", self.neo_jar), \
                patch.object(stage_tool, "FABRIC_CONFIG", self.config), \
                patch.object(stage_tool, "NEOFORGE_CONFIG", self.neo_config), \
                patch.object(stage_tool, "DESCRIPTION", self.description), \
                patch.object(stage_tool, "FABRIC_CHANGELOG", self.changelog), \
                patch.object(stage_tool, "NEOFORGE_CHANGELOG", self.changelog), \
                patch.object(stage_tool, "LICENSE_PATH", self.license):
            self.assertEqual(stage_tool.main(), 0)
        require_java.assert_called_once_with()
        run_build.assert_called_once_with()

    def test_cli_rejects_alternate_jar_paths(self) -> None:
        with patch.object(sys, "argv", ["stage_modrinth_release.py", "--jar", str(self.jar)]), \
                self.assertRaises(SystemExit):
            stage_tool.parse_args()


if __name__ == "__main__":
    unittest.main()
