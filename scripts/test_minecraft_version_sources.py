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

    def test_version_specific_mixins_are_owned_and_have_audited_targets(self):
        for version in ("26.1", "26.2"):
            client_root = VERSION_ROOT / version / "client/java/dev/ringworld/client/mixin"
            self.assertTrue((client_root / "GuiMixin.java").is_file())
            self.assertTrue((client_root / "LevelRendererMixin.java").is_file())
            self.assertTrue((client_root / "ChunkBuilderBuiltChunkMixin.java").is_file())
            main_root = VERSION_ROOT / version / "main/java/dev/ringworld/mixin"
            self.assertTrue((main_root / "SurfaceNoiseThresholdMixin.java").is_file())
            self.assertTrue((main_root / "DensityCoordinateConsumerMixin.java").is_file())
        self.assertFalse((ROOT / "src/client/java/dev/ringworld/client/mixin/GuiMixin.java").exists())
        self.assertFalse((ROOT / "src/client/java/dev/ringworld/client/mixin/LevelRendererMixin.java").exists())
        self.assertFalse((ROOT / "src/client/java/dev/ringworld/client/mixin/ChunkBuilderBuiltChunkMixin.java").exists())
        self.assertFalse((ROOT / "src/main/java/dev/ringworld/mixin/SurfaceNoiseThresholdMixin.java").exists())
        self.assertFalse((ROOT / "src/main/java/dev/ringworld/mixin/DensityCoordinateConsumerMixin.java").exists())
        chunk_region = (ROOT / "src/main/java/dev/ringworld/mixin/ChunkRegionMixin.java").read_text()
        self.assertIn('method = {"markPosForPostprocessing", "markPosForPostProcessing"}', chunk_region)
        self.assertIn(
            "WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            chunk_region,
        )

        threshold_26_1 = (VERSION_ROOT / "26.1/main/java/dev/ringworld/mixin/SurfaceNoiseThresholdMixin.java").read_text()
        threshold_26_2 = (VERSION_ROOT / "26.2/main/java/dev/ringworld/mixin/SurfaceNoiseThresholdMixin.java").read_text()
        self.assertIn("NoiseThresholdConditionSource$1NoiseThresholdCondition", threshold_26_1)
        self.assertIn('method = "compute"', threshold_26_1)
        self.assertIn("SurfaceRules$Context$1", threshold_26_2)
        self.assertIn("SurfaceRules$Context$2", threshold_26_2)
        self.assertIn('method = "getAsDouble"', threshold_26_2)

        density_26_1 = (VERSION_ROOT / "26.1/main/java/dev/ringworld/mixin/DensityCoordinateConsumerMixin.java").read_text()
        density_26_2 = (VERSION_ROOT / "26.2/main/java/dev/ringworld/mixin/DensityCoordinateConsumerMixin.java").read_text()
        self.assertIn("DensityFunctions$WeirdScaledSampler", density_26_1)
        self.assertNotIn("DensityFunctions$WeirdScaledSampler", density_26_2)

        readiness = (VERSION_ROOT / "26.2/client/java/dev/ringworld/client/mixin/ChunkBuilderBuiltChunkMixin.java").read_text()
        self.assertIn("@Mixin(SectionUpdateTracker.class)", readiness)
        self.assertIn("geometry.isExteriorChunkZ(SectionPos.z(sectionPos))", readiness)
        self.assertIn("return doesChunkExistAt(level, sectionPos)", readiness)


    def test_26_2_gpu_does_not_duplicate_inherited_dynamic_transforms(self):
        source = (VERSION_ROOT / "26.2/client/java/dev/ringworld/client/render/RingSurfaceGpu.java").read_text()
        self.assertIn("RenderPipelines.GUI_TEXTURED_SNIPPET", source)
        self.assertNotIn(".withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)", source)
        self.assertIn("pass.draw(vertexCount, 1, 0, 0)", source)

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
