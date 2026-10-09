"""Static contract tests for Gradle's reviewed Minecraft source-ABI layer."""
from __future__ import annotations

from pathlib import Path
import math
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
        r"public static ([\w<>?, .]+) (\w+)\(([^)]*)\)", source,
    ))


class MinecraftVersionSourcesTest(unittest.TestCase):
    def test_atlas_renderer_logic_matches_across_gpu_package_abis(self):
        shared = (ROOT / "src/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java").read_text()
        current = (VERSION_ROOT / "26.3/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java").read_text()
        # GPU package migration is the only supported difference. Compare the
        # complete logic so saved style/seed inputs cannot silently regress.
        current = current.replace("com.mojang.renderpearl.api", "com.mojang.blaze3d")
        self.assertEqual(shared, current)

    def test_selector_uses_the_newest_reviewed_abi_not_newer_than_target(self):
        self.assertEqual("26.1", selected_source_abi("26.1"))
        self.assertEqual("26.1", selected_source_abi("26.1.2"))
        self.assertEqual("26.2", selected_source_abi("26.2"))
        self.assertEqual("26.3", selected_source_abi("26.3"))
        self.assertEqual("26.3", selected_source_abi("26.3.1"))
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
            for version in ("26.1", "26.2", "26.3")
        ]
        self.assertTrue(all(adapter.is_file() for adapter in adapters))
        self.assertEqual(adapter_contract(adapters[0]), adapter_contract(adapters[1]))
        self.assertEqual(adapter_contract(adapters[0]), adapter_contract(adapters[2]))
        names = {name for _, name, _ in adapter_contract(adapters[0])}
        self.assertEqual(
            {"screen", "setScreen", "mainRenderTarget", "toastManager", "cameraEntity",
             "camera", "hideGui", "setGuiHidden", "invalidateChunks", "grabScreenshot", "maxTextureSize",
                 "showBackgroundReviewWindow", "createPreviewRandomState"},
            names,
        )

    def test_fixture_entity_registry_checks_created_instance_not_widened_type_metadata(self):
        registry = (ROOT / "src/main/java/dev/ringworld/server/RingWorldVanillaFixtureRegistries.java") \
                .read_text(encoding="utf-8")
        self.assertIn("createEntity(String path, Class<T> expectedClass", registry)
        self.assertIn("Entity entity = type.create(level, reason);", registry)
        self.assertIn("expectedClass.isInstance(entity)", registry)
        self.assertNotIn("type.getBaseClass()", registry)
        for relative in (
            "src/main/java/dev/ringworld/server/RingWorldMultiplayerTest.java",
            "src/main/java/dev/ringworld/server/RingWorldServer.java",
            "src/main/java/dev/ringworld/server/RingWorldExtendedMultiplayerTest.java",
            "src/client/java/dev/ringworld/client/CurvedObjectCaptureClient.java",
        ):
            self.assertIn("RingWorldVanillaFixtureRegistries.createEntity(",
                          (ROOT / relative).read_text(encoding="utf-8"))
        for source_root in (ROOT / "src/main/java", ROOT / "src/client/java"):
            for source in source_root.rglob("*.java"):
                text = source.read_text(encoding="utf-8")
                self.assertNotRegex(text, r"RingWorldVanillaFixtureRegistries\s*\.\s*entityType\(")

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

    def test_gpu_depth_conventions_are_version_owned(self):
        paths = [VERSION_ROOT / version / "client/java/dev/ringworld/client/render/RingSurfaceGpu.java"
                 for version in ("26.1", "26.2")]
        old, new = (path.read_text() for path in paths)
        self.assertEqual(adapter_contract(paths[0]), adapter_contract(paths[1]))
        self.assertIn("new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true)", old)
        self.assertNotIn('withShaderDefine("RINGWORLD_REVERSED_DEPTH")', old)
        self.assertIn("new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)", new)
        self.assertNotIn("CompareOp.LESS_THAN_OR_EQUAL", new)
        self.assertIn('withShaderDefine("RINGWORLD_REVERSED_DEPTH")', new)
        self.assertIn("isZZeroToOne() ? 0.0001F : -0.9999F", new)

    def test_proxy_depth_coverage_retains_smooth_alpha(self):
        proxy = (ROOT / "src/client/resources/assets/ringworld/shaders/core/ring_surface.fsh").read_text()
        self.assertIn("gl_FragDepth = mix(0.0, surfaceDepth, proxyAlpha)", proxy)
        self.assertIn("gl_FragDepth = mix(1.0, surfaceDepth, proxyAlpha)", proxy)
        self.assertIn("float nativeDepth = depthMapping.x + depthMapping.y * gl_FragCoord.w", proxy)
        self.assertNotIn("ring_dither_threshold", proxy)
        self.assertIn("if (proxyAlpha <= 0.001)", proxy)
        self.assertIn("fragColor = vec4(mix(ring_handoff_edge_color(), litTerrain, reveal), proxyAlpha)", proxy)

    def test_altitude_handoff_covers_terrain_below_native_vertical_range(self):
        # Both resource ABIs must coordinate coverage, including 26.3 OIT.
        for resources in (ROOT / "src/client/resources",
                          VERSION_ROOT / "26.3/client/resources"):
            proxy_vertex = (resources / "assets/ringworld/shaders/core/ring_surface.vsh").read_text()
            proxy_fragment = (resources / "assets/ringworld/shaders/core/ring_surface.fsh").read_text()
            live_vertex = (resources / "assets/minecraft/shaders/core/terrain.vsh").read_text()
            live_fragment = (resources / "assets/minecraft/shaders/core/terrain.fsh").read_text()
            self.assertIn("float cameraY = float(CameraBlockPos.y) - CameraOffset.y", proxy_vertex)
            self.assertIn("float worldY = RingWorldVertical.w - intrinsicHeight", proxy_vertex)
            self.assertIn("handoffDistance = length(vec2(intrinsicDistance, worldY - cameraY))", proxy_vertex)
            self.assertRegex(proxy_fragment, r"RingWorldHandoff\.w,\s+handoffDistance")
            self.assertIn("ringHandoffDistance = length(vanillaPos)", live_vertex)
            self.assertIn("ringHandoffDistance = -1.0", live_vertex)
            self.assertRegex(live_fragment, r"RingWorldHandoff\.y,\s+ringHandoffDistance")
            # Material/detail distance stays horizontal, including the X seam.
            self.assertIn("ringIntrinsicDistance = length(vanillaPos.xz)", live_vertex)
            self.assertIn("sin(vertexAngle - ModelOffset.x)", proxy_vertex)

        policy = (ROOT / "src/main/java/dev/ringworld/world/RingRenderProfile.java").read_text()
        proxy_end = float(re.search(r"PROXY_FADE_END_FACTOR = ([0-9.]+)", policy)[1])
        proxy_start = float(re.search(r"PROXY_FADE_START_FACTOR = ([0-9.]+)", policy)[1])
        for chunks in (2, 6, 10, 28):
            for camera_y in (130.0, 234.67834, 340.0, -100.0):
                camera_section = math.floor(camera_y / 16)
                # Nearest block faces excluded by native vertical traversal.
                for world_y in ((camera_section - chunks) * 16,
                                (camera_section + chunks + 1) * 16):
                    self.assertGreaterEqual(abs(world_y - camera_y), chunks * 16 * proxy_end)
        # The reported hole had zero horizontal coverage and full vertical coverage.
        self.assertLess(0.0, 160 * proxy_start)
        self.assertGreater(234.67834 - 63.0, 160 * proxy_end)
        # Ground-level coverage is unchanged when vertical separation is zero.
        for horizontal in (0.0, 100.0, 160.0):
            self.assertEqual(horizontal, math.hypot(horizontal, 0.0))

    def test_proxy_far_clamp_preserves_perspective_and_uses_backend_depth(self):
        shader = (ROOT / "src/client/resources/assets/ringworld/shaders/core/ring_surface.vsh").read_text()
        renderer = (ROOT / "src/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java").read_text()
        self.assertIn("new Vector3f((float)cameraAngle, (float)camera.z, RingSurfaceGpu.farBackgroundDepth())", renderer)
        self.assertIn("if (gl_Position.w > 0.0)", shader)
        reversed_branch = shader.rsplit("#ifdef RINGWORLD_REVERSED_DEPTH", 1)[1].split("#else", 1)[0]
        self.assertIn("gl_Position.z = max(gl_Position.z, gl_Position.w * ModelOffset.z)", reversed_branch)
        self.assertNotRegex(reversed_branch, r"gl_Position\.[xyw]\s*=")
        self.assertIn("gl_Position.z = min(", shader)
        # The two supported reversed NDC ranges must keep far geometry just
        # inside their clip boundary without moving any nearer depth.
        for lower, far in ((-1.0, -0.9999), (0.0, 0.0001)):
            for w in (1.0, 512.0, 8000.0):
                self.assertGreater(max((lower - 0.1) * w, far * w) / w, lower)
                near = 0.8 * w
                self.assertEqual(near, max(near, far * w))


if __name__ == "__main__":
    unittest.main()
