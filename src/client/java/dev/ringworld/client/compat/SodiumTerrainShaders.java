package dev.ringworld.client.compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/** Format adapter only: all projection, water and handoff math stays in shared GLSL. */
public final class SodiumTerrainShaders {
    // Hook the decoded position, preserving camera offsets and other provider-owned arithmetic.
    private static final Pattern DECODED_POSITION = Pattern.compile("(?m)^\\h*vec3\\h+position\\h*=\\s*[^;{}#]+;");
    private SodiumTerrainShaders() { }

    // Minecraft 26.2 supplies a live reader. Leave its ownership, contents and position
    // alone for all shaders outside the adapter, including other mods' resources.
    public static BufferedReader adaptReader(Identifier name, BufferedReader reader) throws IOException {
        if (!isTerrainStage(name)) return reader;
        try (reader) {
            var source = new StringWriter();
            reader.transferTo(source);
            return new BufferedReader(new StringReader(adapt(name, source.toString())));
        }
    }

    private static boolean isTerrainStage(Identifier name) {
        if (!name.getNamespace().equals("sodium")) return false;
        String path = name.getPath();
        if (path.startsWith("shaders/")) path = path.substring("shaders/".length());
        return switch (path) {
            case "blocks/block_layer_opaque.vsh", "core/block_layer_opaque.vsh",
                 "blocks/block_layer_opaque.fsh", "core/block_layer_opaque.fsh" -> true;
            default -> false;
        };
    }

    public static String adapt(Identifier name, String source) {
        if (!isTerrainStage(name)) return source;
        boolean vertex = name.getPath().endsWith(".vsh");
        if (source.contains("// RingWorld terrain adapter")) return source;
        boolean engine = source.contains("#moj_import") || source.contains("#include");
        boolean explicit = source.contains("layout(location = 0)");
        String direction = vertex ? "out" : "in";
        String header = "\n// RingWorld terrain adapter\n" + include("render_uniforms")
                + (vertex ? include("projection") : include("handoff"))
                + (explicit ? "layout(location = 4) " : "") + direction + " vec2 ringworldDistances;\n";
        header += (explicit ? "layout(location = 5) " : "") + direction + " vec2 ringWaterUv;\n";
        if (!vertex) header += "#ifndef OIT_ALPHA_ONLY\nuniform sampler2D u_LightTex;\n#define RING_WATER_LIGHTMAP u_LightTex\n#endif\n" + include("material");
        if (vertex) {
            header += engine
                    ? (explicit ? "#include" : "#moj_import") + " <minecraft:globals.glsl>\n"
                    : "uniform float RingWorldCameraY;\n";
            String cameraY = engine ? "float(CameraBlockPos.y) - CameraOffset.y" : "RingWorldCameraY";
            var position = DECODED_POSITION.matcher(source);
            if (!position.find()) throw new IllegalStateException("Missing decoded terrain position");
            int endOfPosition = position.end();
            if (position.find()) throw new IllegalStateException("Ambiguous decoded terrain position");
            source = source.substring(0, endOfPosition) + "\n" + """
                    vec3 ringworldFlat = position;
                    ringworldDistances = vec2(-1.0);
                    ringWaterUv = vec2(0.0);
                    if (RingWorldLayout.x != 0) {
                        vec3 ringworldCamera = vec3(0.0, CAMERA_Y, 0.0);
                        ringworldDistances = vec2(length(position.xz), length(position));
                        ringWaterUv = (position.xz + RingWorldWaterChart.xy - vec2(0.0, RingWorldRender.x))
                                / vec2(RingWorldLayout.yz);
                        position = ring_project_position(position + ringworldCamera, ringworldCamera);
                    }
                    """.replace("CAMERA_Y", cameraY) + source.substring(endOfPosition);
            source = replaceOnce(source, "v_FragDistance = getFragDistance(position);", """
                    v_FragDistance = getFragDistance(position);
                    if (RingWorldLayout.x != 0) {
                        v_FragDistance = 1.02 * max(v_FragDistance, getFragDistance(ringworldFlat));
                    }
                    """);
        } else {
            source = replaceOnce(source, "    }\n#endif\n", """
                        }
                    #endif
                    color = ring_material_coverage(color, v_Color, v_TexCoord,
                            ringworldDistances.x, ringworldDistances.y);
                    """);
            if (explicit) {
                source = replaceOnce(source, "vec4 calculateFinalColor(vec4 color) {", """
                        vec4 calculateFinalColor(vec4 color) {
                            if (RingWorldLayout.x != 0 && ringworldDistances.x >= 0.0) {
                                color = ring_material_atmosphere(color, ringworldDistances.x, u_FogColor.rgb);
                                color = _linearFog(color, v_FragDistance, u_FogColor,
                                        u_EnvironmentFog, vec2(1.0e20, 1.0e21), 1.0);
                        #ifdef OIT_ACCUMULATE
                                color = sampleColorForAccumulation(color);
                        #endif
                                return color;
                            }
                        """);
            } else {
                source = replaceOnce(source, "fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);", """
                        if (RingWorldLayout.x != 0 && ringworldDistances.x >= 0.0) {
                            color = ring_material_atmosphere(color, ringworldDistances.x, u_FogColor.rgb);
                            fragColor = _linearFog(color, v_FragDistance, u_FogColor,
                                    u_EnvironmentFog, vec2(1.0e20, 1.0e21), 1.0);
                        } else {
                            fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);
                        }
                        """);
            }
        }
        // Definitions must precede calculateFinalColor as well as main, while
        // the renderer's own includes retain their normal preprocessing order.
        var imports = java.util.regex.Pattern.compile("(?m)^#(?:import|moj_import|include) .*\\R").matcher(source);
        int end = -1;
        while (imports.find()) end = imports.end();
        if (end < 0) throw new IllegalStateException("Missing Sodium shader imports");
        dev.ringworld.RingWorldMod.LOGGER.info("RingWorld shared terrain contract applied to Sodium shader {}", name);
        return source.substring(0, end) + header + source.substring(end);
    }

    private static String include(String name) {
        String path = "/assets/ringworld/shaders/include/" + name + ".glsl";
        try (var stream = SodiumTerrainShaders.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing RingWorld shader " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8) + "\n";
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read RingWorld shader " + path, e);
        }
    }

    private static String replaceOnce(String source, String target, String replacement) {
        int at = source.indexOf(target);
        if (at < 0 || source.indexOf(target, at + target.length()) >= 0) {
            throw new IllegalStateException("Unsupported Sodium terrain shader contract: " + target);
        }
        return source.substring(0, at) + replacement + source.substring(at + target.length());
    }
}
