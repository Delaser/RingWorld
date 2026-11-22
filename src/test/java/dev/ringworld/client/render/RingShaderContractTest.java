package dev.ringworld.client.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingShaderContractTest {
    @Test void curvedMaterialsOwnBothStagesOrNeither() {
        var foreign = Identifier.fromNamespaceAndPath("another_mod", "core/terrain");
        var atlas = Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface");
        assertEquals(atlas, RingShaderContract.pairedMaterial(atlas, atlas));
        for (String path : List.of("terrain", "core/terrain", "rendertype_clouds", "clouds")) {
            var stock = Identifier.withDefaultNamespace(path);
            var adapted = RingShaderContract.pairedMaterial(stock, stock);
            assertEquals("ringworld", adapted.getNamespace());
            assertTrue(adapted.getPath().startsWith("core/"));
            assertEquals(adapted, RingShaderContract.pairedMaterial(adapted, adapted));
            assertNull(RingShaderContract.pairedMaterial(foreign, stock));
            assertNull(RingShaderContract.pairedMaterial(stock, foreign));
            assertNull(RingShaderContract.pairedMaterial(stock, null));
            assertNull(RingShaderContract.pairedMaterial(null, stock));
        }
        assertNull(RingShaderContract.pairedMaterial(foreign, foreign));
        assertNull(RingShaderContract.pairedMaterial(Identifier.withDefaultNamespace("core/terrain"),
                Identifier.withDefaultNamespace("core/clouds")));
        var ui = Identifier.withDefaultNamespace("core/position_tex");
        assertNull(RingShaderContract.pairedMaterial(ui, ui));
    }

    @Test void privateUniformAbiIsCompleteAndSeparateFromMinecraft() throws IOException {
        String own = resource("assets/ringworld/shaders/include/render_uniforms.glsl");
        var matcher = Pattern.compile("\\b(?:ivec4|vec4)\\s+(RingWorld\\w+)\\s*;").matcher(own);
        var names = new java.util.ArrayList<String>();
        while (matcher.find()) names.add(matcher.group(1));
        assertEquals(List.of("RingWorldLayout", "RingWorldVertical", "RingWorldRender", "RingWorldHandoff",
                "RingWorldDetail", "RingWorldAtmosphere", "RingWorldAtmosphere2", "RingWorldAtlasLight",
                "RingWorldWaterStill", "RingWorldWaterFlow", "RingWorldDistortion", "RingWorldWaterChart"), names);
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
