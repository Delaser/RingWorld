"""Offline installer-to-Prism metadata contract tests; no Minecraft launch."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from scripts.prism_neoforge_component import ComponentError, WRAPPER, component_from_installer


class PrismNeoForgeComponentTest(unittest.TestCase):
    def fixture(self, root, mutate=None):
        lib = {"name": "net.neoforged:example:1", "downloads": {"artifact": {
            "url": "https://maven.neoforged.net/releases/net/neoforged/example/1/example-1.jar",
            "sha1": "a" * 40, "size": 42}}}
        log4j = {"name": "org.apache.logging.log4j:log4j-api:1"}
        profile = {"minecraft": "26.2", "version": "neoforge-26.2.0.69",
                   "json": "/version.json", "libraries": [lib, log4j]}
        runtime = {"id": "neoforge-26.2.0.69", "inheritsFrom": "26.2",
                   "releaseTime": "2026-08-26T14:16:23", "libraries": [lib, log4j],
                   "arguments": {"game": ["--fml.neoForgeVersion", "26.2.0.69", "--fml.mcVersion", "26.2"]}}
        if mutate:
            mutate(profile, runtime)
        path = root / "installer.jar"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("install_profile.json", json.dumps(profile))
            archive.writestr("version.json", json.dumps(runtime))
        return path

    def generate(self, path, **kwargs):
        return component_from_installer(path, minecraft="26.2", version="26.2.0.69",
                                       expected_sha256=kwargs.get("sha256", hashlib.sha256(path.read_bytes()).hexdigest()))

    def test_exact_installer_produces_portable_hash_bound_component(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.fixture(Path(directory))
            result = self.generate(path)
            self.assertEqual(result, self.generate(path))
            self.assertEqual("26.2.0.69", result["version"])
            self.assertEqual([{"uid": "net.minecraft", "equals": "26.2"}], result["requires"])
            self.assertEqual(WRAPPER, result["libraries"][0])
            self.assertEqual(2, len(result["libraries"]))
            self.assertEqual(2, len(result["mavenFiles"]))
            artifact = result["mavenFiles"][0]["downloads"]["artifact"]
            self.assertEqual(hashlib.sha1(path.read_bytes()).hexdigest(), artifact["sha1"])
            self.assertEqual(path.stat().st_size, artifact["size"])
            self.assertNotIn(str(directory), json.dumps(result))

    def test_rejects_unpinned_or_wrong_installer(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.fixture(Path(directory))
            for checksum in (None, "", "a" * 64):
                with self.subTest(checksum=checksum), self.assertRaises(ComponentError):
                    self.generate(path, sha256=checksum)

    def test_rejects_incompatible_or_malformed_metadata(self):
        mutations = [
            lambda p, r: p.update(minecraft="26.1.2"),
            lambda p, r: r.update(id="neoforge-26.2.0.67"),
            lambda p, r: r["arguments"].update(game=["--fml.neoForgeVersion", "26.2.0.67"]),
            lambda p, r: r["arguments"].update(game=[{"rules": []}]),
            lambda p, r: r["libraries"][0]["downloads"]["artifact"].update(sha1="invalid"),
            lambda p, r: r["libraries"][0]["downloads"]["artifact"].update(url="http://example.com/evil.jar"),
            lambda p, r: r["libraries"].append(r["libraries"][0]),
        ]
        with tempfile.TemporaryDirectory() as directory:
            for i, mutation in enumerate(mutations):
                with self.subTest(case=i), self.assertRaises(ComponentError):
                    self.generate(self.fixture(Path(directory), mutation))


if __name__ == "__main__":
    unittest.main()
