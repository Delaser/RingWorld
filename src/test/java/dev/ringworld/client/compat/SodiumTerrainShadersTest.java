package dev.ringworld.client.compat;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test void unknownTerrainContractsFailClearlyRatherThanRenderFlat() {
        assertThrows(IllegalStateException.class,
                () -> SodiumTerrainShaders.adapt(shader("blocks/block_layer_opaque.vsh"), "void main() {}"));
    }

    @Test void vertexAdapterReusesSharedProjectionAndPreservesFlatInputDistance() {
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
