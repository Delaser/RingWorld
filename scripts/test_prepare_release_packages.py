#!/usr/bin/env python3
"""Non-graphical tests for optional release-package assembly."""

from __future__ import annotations

import hashlib
import gzip
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PREPARE = ROOT / "scripts" / "prepare_release_packages.py"
VERSION = "0.2.0+mc26.1.2"
FABRIC_API_VERSION = "0.155.2+26.1.2"
REVISION = "a" * 40


def release_config(loader: str) -> dict:
    version = {
        "version_number": VERSION,
        "version_type": "alpha",
        "game_versions": ["26.1.2"],
        "loaders": [loader],
        "environment": "client_and_server",
        "dependencies": ([{"project_id": "P7dR8mSH", "dependency_type": "required"}]
                         if loader == "fabric" else []),
        "featured": False,
    }
    config = {
        "project": {"project_type": "mod", "client_side": "required", "server_side": "required",
                    "license_id": "MPL-2.0"},
        "version": version,
        "source": {"repository": "https://github.com/Delaser/RingWorld"},
    }
    if loader == "fabric":
        config["fabric"] = {
            "mod_id": "ringworld", "author": "Delaser",
            "homepage": "https://andwhatnotstudio.com/ringworld/", "environment": "*",
            "fabric_loader": ">=0.19.3", "minecraft": "26.1.2", "java": ">=25",
            "fabric_api": "*",
        }
    else:
        config["neoforge"] = {
            "mod_id": "ringworld", "author": "Delaser",
            "homepage": "https://modrinth.com/mod/ringworld", "minecraft": "26.1.2",
            "neoforge": "[26.1.2.87,)",
        }
    return config


class ReleasePackagePreparationTest(unittest.TestCase):
    def make_jar(
        self, path: Path, *, mod_id: str, license_id: str = "MPL-2.0",
        version: str = VERSION, compatibility_api: int = 1,
        loader: str = "fabric",
    ) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            if loader == "neoforge" and mod_id == "ringworld":
                archive.writestr(
                    "META-INF/neoforge.mods.toml",
                    f'license="{license_id}"\n\n[[mods]]\nmodId="ringworld"\n'
                    f'version="{version}"\nauthors="Delaser"\n'
                    'displayURL="https://modrinth.com/mod/ringworld"\n\n'
                    '[[mixins]]\nconfig="ringworld.mixins.json"\n\n'
                    '[[mixins]]\nconfig="ringworld.client.mixins.json"\n\n'
                    '[[dependencies.ringworld]]\nmodId="neoforge"\ntype="required"\n'
                    'versionRange="[26.1.2.87,)"\n\n[[dependencies.ringworld]]\n'
                    'modId="minecraft"\ntype="required"\nversionRange="[26.1.2]"\n',
                )
                archive.writestr("LICENSE-RINGWORLD.txt", (ROOT / "LICENSE").read_bytes())
                archive.writestr("ringworld.mixins.json", "{}")
                archive.writestr("ringworld.client.mixins.json", "{}")
                archive.writestr("dev/ringworld/RingWorld.class", b"compiled")
                return
            metadata: dict[str, object] = {"id": mod_id, "version": version}
            if mod_id == "ringworld":
                metadata.update({
                    "schemaVersion": 1, "authors": ["Delaser"],
                    "contact": {"homepage": "https://andwhatnotstudio.com/ringworld/"},
                    "license": license_id, "environment": "*",
                    "depends": {"fabricloader": ">=0.19.3", "minecraft": "26.1.2",
                                "java": ">=25", "fabric-api": "*"},
                })
                metadata["custom"] = {
                    "ringworld:compatibility_api": compatibility_api
                }
            archive.writestr("fabric.mod.json", json.dumps(metadata))
            if mod_id == "ringworld":
                archive.writestr("LICENSE-RINGWORLD.txt", (ROOT / "LICENSE").read_bytes())
                archive.writestr("ringworld.mixins.json", "{}")
                archive.writestr("ringworld.client.mixins.json", "{}")
                archive.writestr("dev/ringworld/RingWorld.class", b"compiled")

    def make_stage_manifest(
        self, root: Path, jar: Path, *, loader: str, revision: str = REVISION,
    ) -> Path:
        stage = root / "stage" / loader
        stage.mkdir(parents=True, exist_ok=True)
        staged_jar = stage / jar.name
        shutil.copy2(jar, staged_jar)
        data = staged_jar.read_bytes()
        manifest = {
            "format": 2, "generated": True, "upload_file": staged_jar.name,
            "upload_file_only": True, "size": len(data),
            "hashes": {"sha256": hashlib.sha256(data).hexdigest(),
                       "sha512": hashlib.sha512(data).hexdigest()},
            "mod_id": "ringworld", "version": VERSION, "loader": loader,
            "game_version": "26.1.2", "environment": "client_and_server",
            "source": {"revision": revision,
                       "url": f"https://github.com/Delaser/RingWorld/commit/{revision}"},
            "release_config": release_config(loader),
            "publication_action": "manual_owner_authorization_required",
        }
        (stage / ".ringworld-modrinth-stage").write_text("generated\n", encoding="utf-8")
        path = stage / "STAGING-MANIFEST.json"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        return path

    def make_instance(self, root: Path, *, loader: str = "fabric") -> Path:
        instance = root / "instance"
        (instance / ".minecraft" / "config").mkdir(parents=True)
        (instance / "mmc-pack.json").write_text(
            json.dumps({"components": [
                {"uid": "net.minecraft", "version": "26.1.2"},
                {
                    "uid": "net.fabricmc.fabric-loader" if loader == "fabric" else "net.neoforged",
                    "version": "0.19.3" if loader == "fabric" else "26.1.2.87",
                },
            ]}) + "\n",
            encoding="utf-8",
        )
        (instance / "instance.cfg").write_text(
            "name=RingWorld-Test\nAutomaticJava=true\nOverrideJavaLocation=false\n"
            "JoinServerOnLaunch=false\n",
            encoding="utf-8",
        )
        (instance / ".minecraft" / "config" / "ringworld.properties").write_text(
            "circumferenceBlocks=16384\nwidthBlocks=256\n", encoding="utf-8"
        )
        return instance

    def inputs(self, temporary: Path, *, loader: str = "fabric") -> tuple[Path, Path, Path]:
        temporary.mkdir(parents=True, exist_ok=True)
        jar = temporary / (f"ringworld-{VERSION}.jar" if loader == "fabric"
                           else f"ringworld-neoforge-{VERSION}.jar")
        fabric = temporary / "fabric-api-0.155.2+26.1.2.jar"
        self.make_jar(jar, mod_id="ringworld", loader=loader)
        self.make_jar(fabric, mod_id="fabric-api", version=FABRIC_API_VERSION)
        return jar, fabric, self.make_instance(temporary, loader=loader)

    def run_prepare(
        self, temporary: Path, jar: Path, fabric: Path, instance: Path,
        *, loader: str = "fabric", include_fabric_api: bool | None = None,
        output_name: str = "out", revision: str = REVISION, stage_manifest: Path | None = None,
    ) -> subprocess.CompletedProcess[str]:
        stage_manifest = stage_manifest or self.make_stage_manifest(
            temporary, jar, loader=loader, revision=revision,
        )
        command = [
            sys.executable,
            str(PREPARE),
            "--loader", loader,
            "--stage-manifest", str(stage_manifest),
        ]
        if include_fabric_api is None:
            include_fabric_api = loader == "fabric"
        if include_fabric_api:
            command += ["--fabric-api", str(fabric)]
        command += [
            "--instance-template", str(instance),
            "--output", str(temporary / output_name),
        ]
        return subprocess.run(
            command,
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_builds_reproducible_client_and_server_packages_without_web_content(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            first = self.run_prepare(temporary, jar, fabric, instance)
            second = self.run_prepare(temporary, jar, fabric, instance, output_name="out-again")
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(second.returncode, 0, second.stderr)

            output = temporary / "out"
            names = (
                f"RingWorld-{VERSION}-Fabric-Client-macOS-universal.zip",
                f"RingWorld-{VERSION}-Fabric-Client-Windows.zip",
                f"RingWorld-{VERSION}-Fabric-Server-Overlay.zip",
            )
            self.assertFalse((output / "web").exists())
            self.assertTrue((output / "SHA256SUMS.txt").is_file())
            release_manifest = json.loads((output / "RELEASE-MANIFEST.json").read_text())
            self.assertEqual(release_manifest["sourceRevision"], REVISION)
            self.assertEqual(len(release_manifest["artifacts"]), 3)
            for name in names:
                self.assertEqual(
                    (output / name).read_bytes(),
                    (temporary / "out-again" / name).read_bytes(),
                )

            with zipfile.ZipFile(output / names[0]) as archive:
                contents = set(archive.namelist())
                self.assertIn("LICENSE", contents)
                self.assertIn("README-FIRST.txt", contents)
                self.assertIn("RingWorld-Prism-Instance.zip", contents)
                self.assertIn(f"instance/.minecraft/mods/ringworld-{VERSION}.jar", contents)
                self.assertNotIn("Launch RingWorld.bat", contents)
                manifest = json.loads(archive.read("PACKAGE-MANIFEST.json"))
                self.assertEqual(manifest["license"], "MPL-2.0")
                self.assertEqual(manifest["sourceRevision"], REVISION)

            with zipfile.ZipFile(output / names[1]) as archive:
                contents = set(archive.namelist())
                self.assertIn("Launch RingWorld.bat", contents)
                self.assertNotIn("Launch RingWorld.command", contents)
                self.assertIn("instance/.minecraft/servers.dat", contents)
                server_data = gzip.decompress(archive.read("instance/.minecraft/servers.dat"))
                self.assertIn(b"RingWorld Test Server", server_data)
                self.assertIn(b"andwhatnotstudio.com:25565", server_data)
                self.assertIn("RingWorld-Prism-Instance.zip", contents)
                with zipfile.ZipFile(io.BytesIO(archive.read("RingWorld-Prism-Instance.zip"))) as nested:
                    nested_server_data = gzip.decompress(nested.read(".minecraft/servers.dat"))
                    self.assertEqual(server_data, nested_server_data)
                manifest = json.loads(archive.read("PACKAGE-MANIFEST.json"))
                self.assertEqual(
                    {"name": "RingWorld Test Server", "address": "andwhatnotstudio.com:25565",
                     "autoJoin": False},
                    manifest["preconfiguredServer"],
                )

            with zipfile.ZipFile(output / names[2]) as archive:
                contents = set(archive.namelist())
                self.assertIn("LICENSE", contents)
                self.assertIn(f"mods/ringworld-{VERSION}.jar", contents)
                self.assertIn("config/ringworld.properties", contents)
                self.assertNotIn("RingWorld-Prism-Instance.zip", contents)

    def test_builds_neoforge_packages_without_fabric_api(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary, loader="neoforge")
            result = self.run_prepare(temporary, jar, fabric, instance, loader="neoforge")
            repeat = self.run_prepare(
                temporary, jar, fabric, instance, loader="neoforge", output_name="out-again"
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(repeat.returncode, 0, repeat.stderr)
            output = temporary / "out"
            names = (
                f"RingWorld-{VERSION}-NeoForge-Client-macOS-universal.zip",
                f"RingWorld-{VERSION}-NeoForge-Client-Windows.zip",
                f"RingWorld-{VERSION}-NeoForge-Server-Overlay.zip",
            )
            release_manifest = json.loads((output / "RELEASE-MANIFEST.json").read_text())
            self.assertEqual(release_manifest["loader"], "neoforge")
            for name in names:
                self.assertTrue((output / name).is_file())
                self.assertEqual((output / name).read_bytes(),
                                 (temporary / "out-again" / name).read_bytes())
            with zipfile.ZipFile(output / names[0]) as archive:
                contents = set(archive.namelist())
                self.assertIn("RINGWORLD-LOADER.txt", contents)
                self.assertEqual("neoforge\n", archive.read("RINGWORLD-LOADER.txt").decode())
                self.assertIn(
                    f"instance/.minecraft/mods/ringworld-neoforge-{VERSION}.jar", contents
                )
                self.assertFalse(any("fabric-api-" in name for name in contents))
                pack = json.loads(archive.read("instance/mmc-pack.json"))
                components = {item["uid"]: item["version"] for item in pack["components"]}
                self.assertEqual("26.1.2.87", components["net.neoforged"])
                self.assertNotIn("net.fabricmc.fabric-loader", components)
                manifest = json.loads(archive.read("PACKAGE-MANIFEST.json"))
                self.assertEqual(manifest["loader"], "neoforge")
                self.assertNotIn("fabricApiJar", manifest)
            with zipfile.ZipFile(output / names[2]) as archive:
                contents = set(archive.namelist())
                self.assertFalse(any("fabric-api-" in name for name in contents))
                deployment = archive.read("DEPLOYMENT.md").decode("utf-8")
                service = archive.read("ringworld.service").decode("utf-8")
                self.assertIn("NeoForge", deployment)
                self.assertIn("neoforge-server-launch.jar", service)
                self.assertNotIn("ringworld-fabric.service", contents)

    def test_rejects_loader_jar_and_component_mismatches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary, loader="neoforge")
            result = self.run_prepare(
                temporary, jar, fabric, instance, loader="neoforge", include_fabric_api=True
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("must not include --fabric-api", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary, loader="fabric")
            result = self.run_prepare(temporary, jar, fabric, instance, loader="neoforge")
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("missing META-INF/neoforge.mods.toml", result.stderr)

    def test_rejects_empty_and_malformed_or_decoy_staged_runtime_jars(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            with zipfile.ZipFile(jar, "w"):
                pass
            stage = self.make_stage_manifest(temporary, jar, loader="fabric")
            result = self.run_prepare(
                temporary, jar, fabric, instance, stage_manifest=stage,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("missing fabric.mod.json", result.stderr)

        for descriptor, expected in (
            ('license="MPL-2.0"\n[[mods]\n', "invalid META-INF/neoforge.mods.toml"),
            (
                'license="MPL-2.0"\n\n[[mods]]\nmodId="ringworld"\n'
                f'version="{VERSION}"\nauthors="Delaser"\n'
                'displayURL="https://modrinth.com/mod/ringworld"\n\n'
                '[[mods]]\nmodId="ringworld"\n'
                f'version="{VERSION}"\nauthors="Delaser"\n'
                'displayURL="https://modrinth.com/mod/ringworld"\n',
                "exactly one ringworld mod",
            ),
        ):
            with self.subTest(descriptor=expected), tempfile.TemporaryDirectory() as directory:
                temporary = Path(directory)
                jar, fabric, instance = self.inputs(temporary, loader="neoforge")
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr("META-INF/neoforge.mods.toml", descriptor)
                    archive.writestr("LICENSE-RINGWORLD.txt", (ROOT / "LICENSE").read_bytes())
                    archive.writestr("ringworld.mixins.json", "{}")
                    archive.writestr("ringworld.client.mixins.json", "{}")
                    archive.writestr("dev/ringworld/RingWorld.class", b"compiled")
                stage = self.make_stage_manifest(temporary, jar, loader="neoforge")
                result = self.run_prepare(
                    temporary, jar, fabric, instance, loader="neoforge", stage_manifest=stage,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected, result.stderr)

    def test_rejects_staged_jar_hash_or_loader_provenance_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            stage = self.make_stage_manifest(temporary, jar, loader="fabric")
            staged_jar = stage.parent / jar.name
            staged_jar.write_bytes(staged_jar.read_bytes() + b"tampered")
            result = self.run_prepare(
                temporary, jar, fabric, instance, stage_manifest=stage,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("sha256 does not match staging manifest", result.stderr)

            neo_jar, _, neo_instance = self.inputs(temporary / "neo", loader="neoforge")
            neo_stage = self.make_stage_manifest(temporary / "neo", neo_jar, loader="neoforge")
            result = self.run_prepare(
                temporary / "neo", neo_jar, fabric, neo_instance,
                loader="fabric", stage_manifest=neo_stage,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("staging manifest is for 'neoforge'", result.stderr)

    @unittest.skipIf(os.name == "nt", "POSIX launcher fixture")
    def test_mac_launcher_upgrade_preserves_existing_user_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertEqual(result.returncode, 0, result.stderr)
            bundle = temporary / "bundle"
            with zipfile.ZipFile(
                temporary / "out" / f"RingWorld-{VERSION}-Fabric-Client-macOS-universal.zip"
            ) as archive:
                archive.extractall(bundle)

            prism = bundle / ".launcher" / "macos" / "Prism Launcher.app" / "Contents" / "MacOS" / "prismlauncher"
            prism.parent.mkdir(parents=True)
            prism.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            prism.chmod(0o755)
            launcher = bundle / "Launch RingWorld.command"
            launcher.chmod(0o755)
            home = temporary / "home"
            home.mkdir()
            environment = os.environ | {"HOME": str(home)}
            fresh = subprocess.run(
                [str(launcher)], capture_output=True, text=True, check=False,
                env=environment,
            )
            self.assertEqual(fresh.returncode, 0, fresh.stderr)

            installed = bundle / ".prism-data" / "instances" / "RingWorld-Test"
            mods = installed / ".minecraft" / "mods"
            self.assertTrue((mods / f"ringworld-{VERSION}.jar").is_file())
            fresh_config = (installed / "instance.cfg").read_text(encoding="utf-8")
            self.assertIn("AutomaticJava=true", fresh_config)
            self.assertIn("OverrideJavaLocation=false", fresh_config)
            self.assertIn("Prism will install or select it", fresh.stdout)
            (installed / ".minecraft" / "saves" / "sentinel").mkdir(parents=True)
            (installed / "mmc-pack.json").write_text("old\n", encoding="utf-8")
            (installed / "instance.cfg").write_text(
                "user-edited=true\nAutomaticJava=false\nOverrideJavaLocation=true\n"
                "JavaPath=/old/java-21/bin/java\n",
                encoding="utf-8",
            )
            (installed / ".minecraft" / "options.txt").write_text("keep=true\n", encoding="utf-8")
            config = installed / ".minecraft" / "config" / "ringworld.properties"
            config.write_text("widthBlocks=416\n", encoding="utf-8")
            (mods / "ringworld-old.jar").write_bytes(b"old")
            (mods / "ringworld-neoforge-old.jar").write_bytes(b"old")
            (mods / "fabric-api-old.jar").write_bytes(b"old")

            java25 = home / ".local" / "jdks" / "jdk-25-test" / "Contents" / "Home" / "bin" / "java"
            java25.parent.mkdir(parents=True)
            java25.write_text(
                "#!/bin/sh\necho 'openjdk version \"25.0.4\"' >&2\n",
                encoding="utf-8",
            )
            java25.chmod(0o755)

            launched = subprocess.run(
                [str(launcher)], capture_output=True, text=True, check=False,
                env=environment,
            )
            self.assertEqual(launched.returncode, 0, launched.stderr)
            self.assertTrue((mods / f"ringworld-{VERSION}.jar").is_file())
            self.assertFalse((mods / "ringworld-old.jar").exists())
            self.assertFalse((mods / "ringworld-neoforge-old.jar").exists())
            self.assertFalse((mods / "fabric-api-old.jar").exists())
            self.assertEqual(config.read_text(encoding="utf-8"), "widthBlocks=416\n")
            self.assertEqual(
                (installed / ".minecraft" / "options.txt").read_text(encoding="utf-8"),
                "keep=true\n",
            )
            self.assertTrue((installed / ".minecraft" / "saves" / "sentinel").is_dir())
            instance_config = (installed / "instance.cfg").read_text(encoding="utf-8")
            self.assertIn("user-edited=true", instance_config)
            self.assertIn("AutomaticJava=false", instance_config)
            self.assertIn("OverrideJavaLocation=true", instance_config)
            self.assertIn(f"JavaPath={java25}", instance_config)

    @unittest.skipIf(os.name == "nt", "POSIX launcher fixture")
    def test_mac_neoforge_launcher_uses_separate_instance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary, loader="neoforge")
            result = self.run_prepare(temporary, jar, fabric, instance, loader="neoforge")
            self.assertEqual(result.returncode, 0, result.stderr)
            bundle = temporary / "bundle"
            with zipfile.ZipFile(
                temporary / "out" / f"RingWorld-{VERSION}-NeoForge-Client-macOS-universal.zip"
            ) as archive:
                archive.extractall(bundle)

            prism = bundle / ".launcher" / "macos" / "Prism Launcher.app" / "Contents" / "MacOS" / "prismlauncher"
            prism.parent.mkdir(parents=True)
            prism.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            prism.chmod(0o755)
            launcher = bundle / "Launch RingWorld.command"
            launcher.chmod(0o755)
            home = temporary / "home"
            home.mkdir()
            environment = os.environ | {"HOME": str(home)}
            fresh = subprocess.run(
                [str(launcher)], capture_output=True, text=True, check=False, env=environment
            )
            self.assertEqual(fresh.returncode, 0, fresh.stderr)

            mods = bundle / ".prism-data" / "instances" / "RingWorld-NeoForge" / ".minecraft" / "mods"
            self.assertTrue((mods / f"ringworld-neoforge-{VERSION}.jar").is_file())
            self.assertFalse(any(mods.glob("fabric-api-*.jar")))
            (mods / "ringworld-neoforge-old.jar").write_bytes(b"old")
            (mods / "fabric-api-user-installed.jar").write_bytes(b"unrelated")
            upgraded = subprocess.run(
                [str(launcher)], capture_output=True, text=True, check=False, env=environment
            )
            self.assertEqual(upgraded.returncode, 0, upgraded.stderr)
            self.assertFalse((mods / "ringworld-neoforge-old.jar").exists())
            self.assertTrue((mods / "fabric-api-user-installed.jar").exists())

    @unittest.skipIf(os.name == "nt", "POSIX launcher fixture")
    def test_mac_fabric_and_neoforge_packages_keep_separate_instances(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            fabric_root = temporary / "fabric-input"
            neoforge_root = temporary / "neoforge-input"
            fabric_root.mkdir()
            neoforge_root.mkdir()
            fabric_jar, fabric_api, fabric_instance = self.inputs(fabric_root)
            neo_jar, neo_api, neo_instance = self.inputs(neoforge_root, loader="neoforge")
            fabric_result = self.run_prepare(
                temporary, fabric_jar, fabric_api, fabric_instance, output_name="fabric-out"
            )
            neo_result = self.run_prepare(
                temporary, neo_jar, neo_api, neo_instance, loader="neoforge",
                output_name="neoforge-out",
            )
            self.assertEqual(fabric_result.returncode, 0, fabric_result.stderr)
            self.assertEqual(neo_result.returncode, 0, neo_result.stderr)

            bundle = temporary / "bundle"
            fabric_package = temporary / "fabric-out" / (
                f"RingWorld-{VERSION}-Fabric-Client-macOS-universal.zip"
            )
            neo_package = temporary / "neoforge-out" / (
                f"RingWorld-{VERSION}-NeoForge-Client-macOS-universal.zip"
            )
            with zipfile.ZipFile(fabric_package) as archive:
                archive.extractall(bundle)
            prism = (bundle / ".launcher" / "macos" / "Prism Launcher.app" /
                     "Contents" / "MacOS" / "prismlauncher")
            prism.parent.mkdir(parents=True)
            prism.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            prism.chmod(0o755)
            home = temporary / "home"
            home.mkdir()
            environment = os.environ | {"HOME": str(home)}
            launcher = bundle / "Launch RingWorld.command"
            launcher.chmod(0o755)
            first = subprocess.run(
                [str(launcher)], capture_output=True, text=True, check=False, env=environment
            )
            self.assertEqual(first.returncode, 0, first.stderr)
            fabric_installed = bundle / ".prism-data" / "instances" / "RingWorld-Test"
            sentinel = fabric_installed / ".minecraft" / "mods" / "fabric-only-sentinel.jar"
            sentinel.write_bytes(b"preserve")

            with zipfile.ZipFile(neo_package) as archive:
                archive.extractall(bundle)
            launcher.chmod(0o755)
            second = subprocess.run(
                [str(launcher)], capture_output=True, text=True, check=False, env=environment
            )
            self.assertEqual(second.returncode, 0, second.stderr)
            neo_installed = bundle / ".prism-data" / "instances" / "RingWorld-NeoForge"
            self.assertTrue(sentinel.is_file())
            self.assertTrue((neo_installed / ".minecraft" / "mods" /
                             f"ringworld-neoforge-{VERSION}.jar").is_file())
            self.assertFalse(any((neo_installed / ".minecraft" / "mods").glob("fabric-api-*.jar")))

    @unittest.skipUnless(os.name == "nt", "Windows launcher fixture")
    def test_windows_launcher_fresh_and_upgrade_preserve_existing_user_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertEqual(result.returncode, 0, result.stderr)
            bundle = temporary / "bundle"
            with zipfile.ZipFile(
                temporary / "out" / f"RingWorld-{VERSION}-Fabric-Client-Windows.zip"
            ) as archive:
                archive.extractall(bundle)

            prism = bundle / ".launcher" / "windows" / "prismlauncher.exe"
            prism.parent.mkdir(parents=True)
            shutil.copy2(shutil.which("where.exe"), prism)
            launcher = bundle / "Launch RingWorld.bat"
            fresh = subprocess.run(
                ["cmd.exe", "/d", "/c", str(launcher)],
                cwd=bundle, capture_output=True, text=True, check=False,
            )
            self.assertEqual(fresh.returncode, 0, fresh.stderr + fresh.stdout)

            installed = bundle / ".prism-data" / "instances" / "RingWorld-Test"
            mods = installed / ".minecraft" / "mods"
            self.assertTrue((mods / f"ringworld-{VERSION}.jar").is_file())
            (installed / ".minecraft" / "saves" / "sentinel").mkdir(parents=True)
            (installed / ".minecraft" / "options.txt").write_text(
                "keep=true\n", encoding="utf-8"
            )
            config = installed / ".minecraft" / "config" / "ringworld.properties"
            config.write_text("widthBlocks=416\n", encoding="utf-8")
            (mods / "ringworld-old.jar").write_bytes(b"old")
            (mods / "ringworld-neoforge-old.jar").write_bytes(b"old")
            (mods / "fabric-api-old.jar").write_bytes(b"old")

            upgraded = subprocess.run(
                ["cmd.exe", "/d", "/c", str(launcher)],
                cwd=bundle, capture_output=True, text=True, check=False,
            )
            self.assertEqual(upgraded.returncode, 0, upgraded.stderr + upgraded.stdout)
            self.assertFalse((mods / "ringworld-old.jar").exists())
            self.assertFalse((mods / "ringworld-neoforge-old.jar").exists())
            self.assertFalse((mods / "fabric-api-old.jar").exists())
            self.assertTrue((installed / ".minecraft" / "saves" / "sentinel").is_dir())
            self.assertEqual(config.read_text(encoding="utf-8"), "widthBlocks=416\n")
            self.assertEqual(
                (installed / ".minecraft" / "options.txt").read_text(encoding="utf-8"),
                "keep=true\n",
            )
            instance_config = (installed / "instance.cfg").read_text(encoding="utf-8")
            self.assertIn("AutomaticJava=true", instance_config)
            self.assertIn("OverrideJavaLocation=false", instance_config)

    @unittest.skipUnless(os.name == "nt", "Windows launcher fixture")
    def test_windows_neoforge_launcher_uses_separate_instance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary, loader="neoforge")
            result = self.run_prepare(temporary, jar, fabric, instance, loader="neoforge")
            self.assertEqual(result.returncode, 0, result.stderr)
            bundle = temporary / "bundle"
            with zipfile.ZipFile(
                temporary / "out" / f"RingWorld-{VERSION}-NeoForge-Client-Windows.zip"
            ) as archive:
                archive.extractall(bundle)

            prism = bundle / ".launcher" / "windows" / "prismlauncher.exe"
            prism.parent.mkdir(parents=True)
            shutil.copy2(shutil.which("where.exe"), prism)
            launcher = bundle / "Launch RingWorld.bat"
            fresh = subprocess.run(
                ["cmd.exe", "/d", "/c", str(launcher)],
                cwd=bundle, capture_output=True, text=True, check=False,
            )
            self.assertEqual(fresh.returncode, 0, fresh.stderr + fresh.stdout)

            mods = bundle / ".prism-data" / "instances" / "RingWorld-NeoForge" / ".minecraft" / "mods"
            self.assertTrue((mods / f"ringworld-neoforge-{VERSION}.jar").is_file())
            self.assertFalse(any(mods.glob("fabric-api-*.jar")))
            (mods / "ringworld-neoforge-old.jar").write_bytes(b"old")
            (mods / "fabric-api-user-installed.jar").write_bytes(b"unrelated")
            upgraded = subprocess.run(
                ["cmd.exe", "/d", "/c", str(launcher)],
                cwd=bundle, capture_output=True, text=True, check=False,
            )
            self.assertEqual(upgraded.returncode, 0, upgraded.stderr + upgraded.stdout)
            self.assertFalse((mods / "ringworld-neoforge-old.jar").exists())
            self.assertTrue((mods / "fabric-api-user-installed.jar").exists())

    def test_rejects_runtime_source_stale_licence_and_bad_revision(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            (instance / ".minecraft" / "saves" / "world").mkdir(parents=True)
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("forbidden runtime directory", result.stderr)
            self.assertFalse((temporary / "out").exists())

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            (instance / "notes.java").write_text("class DoNotShip {}", encoding="utf-8")
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("source artifact", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            self.make_jar(jar, mod_id="ringworld", license_id="MIT")
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("expected licence", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            result = self.run_prepare(temporary, jar, fabric, instance, revision="short")
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("full 40-character", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            (instance / "LICENSE-RINGWORLD.txt").write_bytes(b"stale")
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("stale LICENSE-RINGWORLD.txt", result.stderr)

    def test_rejects_stale_platform_versions_and_autojoin(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            self.make_jar(fabric, mod_id="fabric-api", version="0.1.0")
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Fabric API version", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            pack = json.loads((instance / "mmc-pack.json").read_text(encoding="utf-8"))
            pack["components"][0]["version"] = "1.21.11"
            (instance / "mmc-pack.json").write_text(json.dumps(pack), encoding="utf-8")
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("net.minecraft version", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            config = instance / "instance.cfg"
            config.write_text(
                config.read_text(encoding="utf-8").replace(
                    "JoinServerOnLaunch=false", "JoinServerOnLaunch=true"
                ),
                encoding="utf-8",
            )
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("JoinServerOnLaunch=false", result.stderr)

        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            self.make_jar(jar, mod_id="ringworld", compatibility_api=0)
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("custom.ringworld:compatibility_api", result.stderr)


if __name__ == "__main__":
    unittest.main()
