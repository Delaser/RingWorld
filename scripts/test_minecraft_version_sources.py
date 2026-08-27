"""Static contract tests for Gradle's reviewed Minecraft source-ABI layer."""
from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
VERSION_SOURCES = ROOT / "gradle" / "version-sources.gradle"
FABRIC_BUILD = ROOT / "build.gradle"
NEOFORGE_BUILD = ROOT / "neoforge" / "build.gradle"
VERSION_ROOT = ROOT / "src" / "versions"


def version_components(value: str) -> tuple[int, int, int]:
    if not re.fullmatch(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?", value):
        raise ValueError(f"not a stable Minecraft version: {value}")
    components = [int(component) for component in value.split(".")]
    return tuple((components + [0, 0])[:3])


def selected_source_abi(target: str) -> str:
    target_components = version_components(target)
    candidates = sorted(
        (directory.name for directory in VERSION_ROOT.iterdir()
         if directory.is_dir() and version_components(directory.name) <= target_components),
        key=version_components,
    )
    if not candidates:
        raise ValueError("no reviewed source ABI")
    return candidates[-1]


def adapter_contract(path: Path) -> set[tuple[str, str, str]]:
    source = path.read_text(encoding="utf-8")
    return set(re.findall(
        r"public static ([\w<>?, ]+) (\w+)\(([^)]*)\)", source,
    ))


class MinecraftVersionSourcesTest(unittest.TestCase):
    def test_selector_uses_the_newest_reviewed_abi_not_newer_than_target(self):
        self.assertEqual("26.1", selected_source_abi("26.1"))
        self.assertEqual("26.1", selected_source_abi("26.1.2"))
        self.assertEqual("26.2", selected_source_abi("26.2"))
        self.assertEqual("26.2", selected_source_abi("26.3"))
        with self.assertRaises(ValueError):
            selected_source_abi("26")

    def test_gradle_selector_remains_fail_closed_and_version_derived(self):
        source = VERSION_SOURCES.read_text(encoding="utf-8")
        self.assertIn("Version source selection requires a stable Minecraft version", source)
        self.assertIn("compare(components(it.name), target) <= 0", source)
        self.assertIn("No reviewed source ABI exists for the target version", source)
        self.assertIn("ringSourceAbiDirectory = candidates.last()", source)

    def test_common_client_helper_is_not_duplicated_and_adapters_match(self):
        shared_helper = ROOT / "src/client/java/dev/ringworld/client/RingMinecraftClientAccess.java"
        self.assertFalse(shared_helper.exists())
        adapters = [
            VERSION_ROOT / version / "client/java/dev/ringworld/client/RingMinecraftClientAccess.java"
            for version in ("26.1", "26.2")
        ]
        self.assertTrue(all(adapter.is_file() for adapter in adapters))
        self.assertEqual(adapter_contract(adapters[0]), adapter_contract(adapters[1]))
        names = {name for _, name, _ in adapter_contract(adapters[0])}
        self.assertEqual(
            {"screen", "setScreen", "mainRenderTarget", "toastManager", "cameraEntity",
             "camera", "hideGui", "invalidateChunks", "grabScreenshot"},
            names,
        )

    def test_gui_and_level_renderer_mixins_are_version_owned(self):
        for version in ("26.1", "26.2"):
            root = VERSION_ROOT / version / "client/java/dev/ringworld/client/mixin"
            self.assertTrue((root / "GuiMixin.java").is_file())
            self.assertTrue((root / "LevelRendererMixin.java").is_file())
        shared_root = ROOT / "src/client/java/dev/ringworld/client/mixin"
        self.assertFalse((shared_root / "GuiMixin.java").exists())
        self.assertFalse((shared_root / "LevelRendererMixin.java").exists())

    def test_fabric_and_neoforge_include_selected_java_and_resource_roots(self):
        fabric = FABRIC_BUILD.read_text(encoding="utf-8")
        neoforge = NEOFORGE_BUILD.read_text(encoding="utf-8")
        for build in (fabric, neoforge):
            for relative_path in ("main/java", "client/java", "main/resources", "client/resources"):
                self.assertRegex(
                    build,
                    rf"new File\((?:rootProject\.ext\.)?ringSourceAbiDirectory, ['\"]{relative_path}['\"]\)",
                )
        self.assertIn("apply from: 'gradle/version-sources.gradle'", fabric)
        self.assertIn("rootProject.ext.ringSourceAbiDirectory", neoforge)


if __name__ == "__main__":
    unittest.main()
