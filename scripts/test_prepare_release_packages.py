#!/usr/bin/env python3
"""Non-graphical tests for optional release-package assembly."""

from __future__ import annotations

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


class ReleasePackagePreparationTest(unittest.TestCase):
    def make_jar(
        self, path: Path, *, mod_id: str, license_id: str = "MPL-2.0",
        version: str = VERSION, compatibility_api: int = 1,
    ) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            metadata: dict[str, object] = {"id": mod_id, "version": version}
            if mod_id == "ringworld":
                metadata["license"] = license_id
                metadata["custom"] = {
                    "ringworld:compatibility_api": compatibility_api
                }
            archive.writestr("fabric.mod.json", json.dumps(metadata))
            if mod_id == "ringworld":
                archive.writestr("LICENSE-RINGWORLD.txt", (ROOT / "LICENSE").read_bytes())

    def make_instance(self, root: Path) -> Path:
        instance = root / "instance"
        (instance / ".minecraft" / "config").mkdir(parents=True)
        (instance / "mmc-pack.json").write_text(
            json.dumps({"components": [
                {"uid": "net.minecraft", "version": "26.1.2"},
                {"uid": "net.fabricmc.fabric-loader", "version": "0.19.3"},
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

    def inputs(self, temporary: Path) -> tuple[Path, Path, Path]:
        jar = temporary / f"ringworld-{VERSION}.jar"
        fabric = temporary / "fabric-api-0.155.2+26.1.2.jar"
        self.make_jar(jar, mod_id="ringworld")
        self.make_jar(fabric, mod_id="fabric-api", version=FABRIC_API_VERSION)
        return jar, fabric, self.make_instance(temporary)

    def run_prepare(
        self, temporary: Path, jar: Path, fabric: Path, instance: Path,
        *, output_name: str = "out", revision: str = REVISION,
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(PREPARE),
                "--jar", str(jar),
                "--fabric-api", str(fabric),
                "--instance-template", str(instance),
                "--output", str(temporary / output_name),
                "--version", VERSION,
                "--source-revision", revision,
            ],
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
                f"RingWorld-{VERSION}-Client-macOS-universal.zip",
                f"RingWorld-{VERSION}-Client-Windows.zip",
                f"RingWorld-{VERSION}-Server-Overlay.zip",
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

            with zipfile.ZipFile(output / names[2]) as archive:
                contents = set(archive.namelist())
                self.assertIn("LICENSE", contents)
                self.assertIn(f"mods/ringworld-{VERSION}.jar", contents)
                self.assertIn("config/ringworld.properties", contents)
                self.assertNotIn("RingWorld-Prism-Instance.zip", contents)

    @unittest.skipIf(os.name == "nt", "POSIX launcher fixture")
    def test_mac_launcher_upgrade_preserves_existing_user_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertEqual(result.returncode, 0, result.stderr)
            bundle = temporary / "bundle"
            with zipfile.ZipFile(
                temporary / "out" / f"RingWorld-{VERSION}-Client-macOS-universal.zip"
            ) as archive:
                archive.extractall(bundle)

            prism = bundle / ".launcher" / "macos" / "Prism Launcher.app" / "Contents" / "MacOS" / "prismlauncher"
            prism.parent.mkdir(parents=True)
            prism.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            prism.chmod(0o755)
            launcher = bundle / "Launch RingWorld.command"
            launcher.chmod(0o755)
            fresh = subprocess.run([str(launcher)], capture_output=True, text=True, check=False)
            self.assertEqual(fresh.returncode, 0, fresh.stderr)

            installed = bundle / ".prism-data" / "instances" / "RingWorld-Test"
            mods = installed / ".minecraft" / "mods"
            self.assertTrue((mods / f"ringworld-{VERSION}.jar").is_file())
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
            (mods / "fabric-api-old.jar").write_bytes(b"old")

            launched = subprocess.run([str(launcher)], capture_output=True, text=True, check=False)
            self.assertEqual(launched.returncode, 0, launched.stderr)
            self.assertTrue((mods / f"ringworld-{VERSION}.jar").is_file())
            self.assertFalse((mods / "ringworld-old.jar").exists())
            self.assertFalse((mods / "fabric-api-old.jar").exists())
            self.assertEqual(config.read_text(encoding="utf-8"), "widthBlocks=416\n")
            self.assertEqual(
                (installed / ".minecraft" / "options.txt").read_text(encoding="utf-8"),
                "keep=true\n",
            )
            self.assertTrue((installed / ".minecraft" / "saves" / "sentinel").is_dir())
            instance_config = (installed / "instance.cfg").read_text(encoding="utf-8")
            self.assertIn("user-edited=true", instance_config)
            self.assertIn("AutomaticJava=true", instance_config)
            self.assertIn("OverrideJavaLocation=false", instance_config)
            self.assertIn("JavaPath=/old/java-21/bin/java", instance_config)

    @unittest.skipUnless(os.name == "nt", "Windows launcher fixture")
    def test_windows_launcher_fresh_and_upgrade_preserve_existing_user_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            jar, fabric, instance = self.inputs(temporary)
            result = self.run_prepare(temporary, jar, fabric, instance)
            self.assertEqual(result.returncode, 0, result.stderr)
            bundle = temporary / "bundle"
            with zipfile.ZipFile(
                temporary / "out" / f"RingWorld-{VERSION}-Client-Windows.zip"
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
            (mods / "fabric-api-old.jar").write_bytes(b"old")

            upgraded = subprocess.run(
                ["cmd.exe", "/d", "/c", str(launcher)],
                cwd=bundle, capture_output=True, text=True, check=False,
            )
            self.assertEqual(upgraded.returncode, 0, upgraded.stderr + upgraded.stdout)
            self.assertFalse((mods / "ringworld-old.jar").exists())
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
            self.assertIn("compatibility API version", result.stderr)


if __name__ == "__main__":
    unittest.main()
