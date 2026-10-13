package dev.ringworld.client.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;

/** Format adapter only: all projection, water and handoff math stays in shared GLSL. */
public final class SodiumTerrainShaders {
    private SodiumTerrainShaders() { }

    public static String adapt(Identifier name, String source) {
        if (!name.getNamespace().equals("sodium")) return source;
        String path = name.getPath();
        if (path.startsWith("shaders/")) path = path.substring("shaders/".length());
        boolean vertex = path.equals("blocks/block_layer_opaque.vsh") || path.equals("core/block_layer_opaque.vsh");
        boolean fragment = path.equals("blocks/block_layer_opaque.fsh") || path.equals("core/block_layer_opaque.fsh");
        if (!vertex && !fragment) return source;
        if (source.contains("// RingWorld terrain adapter")) return source;
        boolean engine = source.contains("#moj_import") || source.contains("#include");
        boolean explicit = source.contains("layout(location = 0)");
        String direction = vertex ? "out" : "in";
        String header = "\n// RingWorld terrain adapter\n" + include("render_uniforms")
                + (vertex ? include("projection") : include("handoff") + include("material"))
                + (explicit ? "layout(location = 4) " : "") + direction + " vec2 ringworldDistances;\n";
        if (vertex) {
            header += engine
                    ? (explicit ? "#include" : "#moj_import") + " <minecraft:globals.glsl>\n"
                    : "uniform float RingWorldCameraY;\n";
            String cameraY = engine ? "float(CameraBlockPos.y) - CameraOffset.y" : "RingWorldCameraY";
            source = replaceOnce(source, "vec3 position = _vert_position + translation;", """
                    vec3 position = _vert_position + translation;
                    vec3 ringworldFlat = position;
                    ringworldDistances = vec2(-1.0);
                    if (RingWorldLayout.x != 0) {
                        vec3 ringworldCamera = vec3(0.0, CAMERA_Y, 0.0);
                        ringworldDistances = vec2(length(position.xz), length(position));
                        position = ring_project_position(position + ringworldCamera, ringworldCamera);
                    }
                    """.replace("CAMERA_Y", cameraY));
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
