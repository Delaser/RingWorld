package dev.ringworld.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-ABI guard for the shared 1.21.1 Atlas-light render path. */
class RingAtlasLightGpuSourceContractTest {
    private static final Path ROOT = findProjectRoot();

    @Test
    void completeAndPartialBuildsPackLightAndFilterChannelsIndependently()
            throws IOException {
        String renderer = read(
                "src/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java");

        assertTrue(renderer.contains("RingSurfaceLod.blockLightAlpha(sample.blockLight())"));
        assertTrue(renderer.contains("sample.present()"));
        assertTrue(renderer.contains("pixels[index] = blockLights[index] << 24 | shaded"));
        assertTrue(renderer.contains("RingSurfaceLod.buildNextMipRgbLight("));
    }

    @Test
    void shaderUsesAuthoredLightOnlyForWarmNightContribution() throws IOException {
        String shader = read("src/client/resources/assets/ringworld/shaders/core/ring_surface.fsh");
        String descriptor = read(
                "src/client/resources/assets/ringworld/shaders/core/ring_surface.json");
        String globals = read(
                "src/client/java/dev/ringworld/client/mixin/GlobalSettingsMixin.java");

        assertTrue(shader.contains("uniform vec4 RingWorldAtlasLight"));
        assertTrue(shader.contains("float authoredLight = clamp(sampled.a"));
        assertTrue(shader.contains("float nightVisibility = 1.0 - smootherstep("));
        assertTrue(shader.contains("pow(lightCore, lightFalloff) * nightVisibility"));
        assertTrue(shader.contains("vec3 lampColor = vec3(1.00, 0.63, 0.28)"));
        assertTrue(shader.contains("sampled = vec4(styled * textureNoise, 0.0)"));
        assertTrue(shader.contains("mix(FogColor.rgb, litTerrain, reveal), proxyAlpha)"));
        assertFalse(shader.contains("if (sampled.a == 0.0) discard"));
        assertFalse(shader.contains("proxyAlpha * sampled.a"));
        assertTrue(descriptor.contains("\"RingWorldAtlasLight\""));
        assertTrue(globals.contains("RingAtlasLightTuning.profile()"));
        assertTrue(globals.contains("set(shader, \"RingWorldAtlasLight\""));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "client", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("RingWorld project root is unavailable");
    }
}
