package dev.ringworld.experiment.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.ringworld.experiment.NativeAtlasTarget;

import net.irisshaders.iris.pipeline.programs.PartialShader;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Mixin(value = ShaderCreator.class, remap = false)
abstract class PackTerrainProjection {
    @Unique
    private static final Pattern trial$decoded =
            Pattern.compile(
                    "\\bvec4\\s+getVertexPosition\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+(vec4\\s*\\([^;]+\\))\\s*;\\s*\\}");

    @Unique
    private static String trial$include(String file) {
        // A mixed-in class belongs to Iris on a modular loader. Resolve the actual RingWorld asset
        // through Minecraft.
        var path =
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        "ringworld", "shaders/include/" + file + ".glsl");
        try (var in =
                net.minecraft.client.Minecraft.getInstance()
                        .getResourceManager()
                        .getResourceOrThrow(path)
                        .open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @WrapMethod(method = "link")
    private static PartialShader trial$curve(
            String name,
            String vertex,
            String geometry,
            String tessControl,
            String tessEval,
            String fragment,
            VertexFormat format,
            boolean fallback,
            Operation<PartialShader> original) {
        if (NativeAtlasTarget.ENABLED
                && (name.startsWith("sodium_terrain_") || name.startsWith("shadow_sodium_terrain_"))
                && vertex != null) {
            var decode = trial$decoded.matcher(vertex);
            if (decode.find() && !vertex.contains("ring_project_position")) {
                String flat = decode.group(1);
                String decl = "vec4 ringworldProjectLocal(vec4 local);\n";
                // Retain the renderer's decoded position; the common RingWorld GLSL supplies the
                // curve.
                String replacement =
                        "vec4 getVertexPosition() { return ringworldProjectLocal(" + flat + "); }";
                String parameters =
                        "uniform vec4 ringTrialLayout;\n"
                                + "uniform vec4 ringTrialDistortion;\n"
                                + "#define RingWorldLayout ivec4(ringTrialLayout)\n"
                                + "#define RingWorldVertical ringTrialLayout.zzzz\n"
                                + "#define RingWorldDistortion ringTrialDistortion\n";
                vertex =
                        vertex.substring(0, decode.start())
                                + decl
                                + replacement
                                + vertex.substring(decode.end())
                                + "\n"
                                + parameters
                                + trial$include("projection")
                                + "\n"
                                + "vec4 ringworldProjectLocal(vec4 local) { if (ringTrialLayout.x"
                                + " == 0.0) return local; vec3 eye=vec3(0.0,ringTrialLayout.w,0.0);"
                                + " return vec4(ring_project_position(local.xyz+eye,eye),local.w);"
                                + " }\n";
                System.out.println("PACK_CHUNK_PROJECTION " + name + " shared decoded vertex");
            } else {
                System.out.println("PACK_CHUNK_SKIP unrecognized decoded position in " + name);
            }
        }
        return original.call(
                name, vertex, geometry, tessControl, tessEval, fragment, format, fallback);
    }
}
