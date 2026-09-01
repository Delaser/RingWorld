package dev.ringworld.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-ABI guard for the shared 1.21.1 wall-appearance adapter. */
class RingWallAppearanceSourceContractTest {
    private static final Path ROOT = findProjectRoot();

    @Test
    void gpuAdapterCarriesEncodedArgbThroughPositionTextureColor() throws IOException {
        String gpu = read("src/client/java/dev/ringworld/client/render/RingSurfaceGpu.java");
        String renderer = read(
                "src/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java");

        assertTrue(gpu.contains("DefaultVertexFormat.POSITION_TEX_COLOR"));
        assertTrue(gpu.contains(".setColor(vertexArgb)"));
        assertTrue(renderer.contains("RingWallShaderStyle.encode("));
        assertTrue(renderer.contains("RingSurfaceGpu.createVertexBuffer("));
        assertTrue(renderer.contains("encoded.vertexArgb()"));
        assertTrue(renderer.contains("shader.getUniform(\"RingWorldWallPalette\")"));
        assertTrue(renderer.contains("shader.getUniform(\"RingWorldWallStyle\")"));
    }

    @Test
    void shaderStylesOnlyWallMarkersAndLeavesTerrainSamplingUntinted() throws IOException {
        String vertex = read("src/client/resources/assets/ringworld/shaders/core/ring_surface.vsh");
        String fragment = read("src/client/resources/assets/ringworld/shaders/core/ring_surface.fsh");
        String descriptor = read("src/client/resources/assets/ringworld/shaders/core/ring_surface.json");

        assertTrue(vertex.contains("intrinsicWidth = Position.z"));
        assertTrue(fragment.contains("uniform mat4 RingWorldWallPalette"));
        assertTrue(fragment.contains("uniform vec4 RingWorldWallStyle"));
        assertTrue(fragment.contains("bool rimBridge = texCoord0.y < 0.0 || texCoord0.y > 1.0"));
        assertTrue(fragment.contains("float roll = wallRoll("));
        assertTrue(fragment.contains("RingWorldWallStyle.x"));
        assertTrue(fragment.contains("return vertexColor.rgb"));
        assertFalse(fragment.contains("* vertexColor;"),
                "ordinary terrain must not be tinted by packed wall metadata");
        assertTrue(descriptor.contains("\"RingWorldWallPalette\""));
        assertTrue(descriptor.contains("\"RingWorldWallStyle\""));
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
