package dev.ringworld.client.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingShaderContractTest {
    @Test void adaptedEntryPointsArePrivateAndForeignShadersStayUnchanged() {
        for (String path : List.of("terrain", "core/terrain", "rendertype_clouds", "clouds")) {
            var original = Identifier.withDefaultNamespace(path);
            var adapted = RingShaderContract.materialShader(original);
            assertEquals("ringworld", adapted.getNamespace());
            assertTrue(adapted.getPath().startsWith("core/"));
            assertEquals(adapted, RingShaderContract.materialShader(adapted));
        }
        var foreign = Identifier.fromNamespaceAndPath("another_mod", "terrain");
        assertSame(foreign, RingShaderContract.materialShader(foreign));
        var vanillaUi = Identifier.withDefaultNamespace("core/position_tex");
        assertSame(vanillaUi, RingShaderContract.materialShader(vanillaUi));
    }

    @Test void onlyCurvedMaterialsReceiveThePrivateBinding() {
        for (String path : List.of("terrain", "core/terrain", "rendertype_clouds", "clouds")) {
            assertTrue(RingShaderContract.usesRingUniforms(Identifier.withDefaultNamespace(path)));
        }
        assertTrue(RingShaderContract.usesRingUniforms(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface")));
        assertFalse(RingShaderContract.usesRingUniforms(null));
        assertFalse(RingShaderContract.usesRingUniforms(Identifier.withDefaultNamespace("core/position_tex")));
        assertFalse(RingShaderContract.usesRingUniforms(Identifier.fromNamespaceAndPath("another_mod", "terrain")));
    }

    @Test void privateUniformAbiIsCompleteAndSeparateFromMinecraft() throws IOException {
        String own = resource("assets/ringworld/shaders/include/render_uniforms.glsl");
        var matcher = Pattern.compile("\\b(?:ivec4|vec4)\\s+(RingWorld\\w+)\\s*;").matcher(own);
        var names = new java.util.ArrayList<String>();
        while (matcher.find()) names.add(matcher.group(1));
        assertEquals(List.of("RingWorldLayout", "RingWorldVertical", "RingWorldRender", "RingWorldHandoff",
                "RingWorldDetail", "RingWorldAtmosphere", "RingWorldAtmosphere2", "RingWorldAtlasLight",
                "RingWorldWaterStill", "RingWorldWaterFlow", "RingWorldDistortion"), names);
        assertEquals(names.size() * 16, RingShaderContract.UNIFORM_BYTES);
        assertTrue(own.contains("uniform " + RingShaderContract.UNIFORM));
        // Fabric exposes stock game assets in its test classpath; NeoForge's
        // unit-test game module omits them. Neither may expose our old override.
        try (var vanilla = RingShaderContractTest.class.getClassLoader()
                .getResourceAsStream("assets/minecraft/shaders/include/globals.glsl")) {
            if (vanilla != null) {
                assertFalse(new String(vanilla.readAllBytes(), StandardCharsets.UTF_8).contains("RingWorld"));
            }
        }
    }

    private static String resource(String name) throws IOException {
        try (var input = RingShaderContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
