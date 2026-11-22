package dev.ringworld.client.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class SodiumTerrainShadersTest {
    private static Identifier shader(String path) {
        return Identifier.fromNamespaceAndPath("sodium", path);
    }

    @Test void leavesUnrelatedMaterialsUnchanged() {
        String source = "unrelated shader";
        assertSame(source, SodiumTerrainShaders.adapt(shader("include/fog.glsl"), source));
        assertSame(source, SodiumTerrainShaders.adapt(Identifier.withDefaultNamespace("terrain.vsh"), source));
    }

    @Test void unrelatedResourceKeepsItsReaderAndPosition() throws IOException {
        try (var reader = new BufferedReader(new StringReader("first\nsecond"))) {
            assertEquals("first", reader.readLine());
            assertSame(reader, SodiumTerrainShaders.adaptReader(
                    Identifier.fromNamespaceAndPath("another_mod", "core/block_layer_opaque.vsh"), reader));
            assertSame(reader, SodiumTerrainShaders.adaptReader(shader("include/fog.glsl"), reader));
            assertEquals("second", reader.readLine());
        }
    }

    @Test void claimedReaderClosesEvenWhenShaderAdaptationFails() {
        var reader = new BufferedReader(new StringReader("void main() {}"));
        assertThrows(IllegalStateException.class, () ->
                SodiumTerrainShaders.adaptReader(shader("blocks/block_layer_opaque.vsh"), reader));
        assertThrows(IOException.class, reader::readLine);
    }

    @Test void unknownTerrainContractsFailClearlyRatherThanRenderFlat() {
        assertThrows(IllegalStateException.class,
                () -> SodiumTerrainShaders.adapt(shader("blocks/block_layer_opaque.vsh"), "void main() {}"));
    }

    @Test void vertexAdapterReusesSharedProjectionAndPreservesFlatInputDistance() throws IOException {
        String source = """
                #version 330
                #import <sodium:include/fog.glsl>
                void main() {
                    vec3 position = _vert_position + translation;
                    v_FragDistance = getFragDistance(position);
                }
                """;
        String adapted = SodiumTerrainShaders.adapt(shader("blocks/block_layer_opaque.vsh"), source);
        assertTrue(adapted.contains("ring_project_position(position + ringworldCamera, ringworldCamera)"));
        assertTrue(adapted.contains("ringworldDistances = vec2(length(position.xz), length(position))"));
        assertTrue(adapted.contains("uniform float RingWorldCameraY"));
        assertTrue(adapted.contains("max(v_FragDistance, getFragDistance(ringworldFlat))"));
        assertEquals(adapted, SodiumTerrainShaders.adapt(shader("blocks/block_layer_opaque.vsh"), adapted));
        var original = new BufferedReader(new StringReader(source));
        try (var transformed = SodiumTerrainShaders.adaptReader(shader("blocks/block_layer_opaque.vsh"), original)) {
            var contents = new java.io.StringWriter();
            transformed.transferTo(contents);
            assertEquals(adapted, contents.toString());
        }
        assertThrows(IOException.class, original::readLine);
    }

    @Test void projectsTheRenderersDecodedPositionIncludingItsCameraOffset() {
        var id = shader("blocks/block_layer_opaque.vsh");
        String source = """
                #include <sodium:globals.glsl>
                void main() {
                    vec3 position = (_vert_position + translation) - u_CameraOffset;
                #ifdef USE_FOG
                    v_FragDistance = getFragDistance(position);
                #endif
                    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);
                }
                """;
        String adapted = SodiumTerrainShaders.adapt(id, source);
        int decode = adapted.indexOf("vec3 position = (_vert_position + translation) - u_CameraOffset;");
        int project = adapted.indexOf("position = ring_project_position(");
        assertTrue(decode >= 0 && decode < project && project < adapted.indexOf("#ifdef USE_FOG"));
        assertEquals(adapted, SodiumTerrainShaders.adapt(id, adapted));
        assertThrows(IllegalStateException.class, () -> SodiumTerrainShaders.adapt(id,
                source.replace("#ifdef USE_FOG", "{\nvec3 position = other;\n}\n#ifdef USE_FOG")));
    }

    @Test void modernOitRunsCoverageBeforeAlphaAndAtmosphereBeforePremultiplication() {
        String source = """
                #version 460 core
                #include <sodium:globals.glsl>
                layout(location = 0) in vec4 v_Color;
                vec4 calculateFinalColor(vec4 color) {
                    return color;
                }
                void main() {
                    color *= v_Color;
                #ifdef ALPHA_CUTOUT
                    if (color.a < ALPHA_CUTOUT) {
                        discard;
                    }
                #endif
                #ifdef OIT_ALPHA_ONLY
                    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
                #endif
                }
                """;
        String adapted = SodiumTerrainShaders.adapt(shader("shaders/blocks/block_layer_opaque.fsh"), source);
        assertTrue(adapted.contains("layout(location = 4) in vec2 ringworldDistances"));
        assertTrue(adapted.indexOf("vec4 ring_material_atmosphere(") < adapted.indexOf("vec4 calculateFinalColor("));
        assertTrue(adapted.indexOf("color = ring_material_atmosphere(") < adapted.indexOf("color = sampleColorForAccumulation("));
        assertTrue(adapted.indexOf("discard;", adapted.indexOf("void main()")) < adapted.indexOf("color = ring_material_coverage("));
        assertTrue(adapted.indexOf("color = ring_material_coverage(") < adapted.indexOf("executeAlphaOnlyPhase("));
    }
}
