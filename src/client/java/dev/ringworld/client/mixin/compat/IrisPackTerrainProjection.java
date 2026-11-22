package dev.ringworld.client.mixin.compat;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.ringworld.client.RingShaderSupport;

import net.irisshaders.iris.pipeline.programs.ShaderCreator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Mixin(value = ShaderCreator.class, remap = false)
abstract class IrisPackTerrainProjection {
    @Unique
    private static final Pattern ringworld$decoded =
            Pattern.compile(
                    "\\bvec4\\s+getVertexPosition\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+(vec4\\s*\\([^;]+\\))\\s*;\\s*\\}");

    @Unique private static boolean ringworld$reportedDecoder;

    @Unique
    private static String ringworld$projection() {
        // A mixed-in class belongs to Iris on a modular loader. Resolve the actual RingWorld asset
        // through Minecraft.
        var path =
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        "ringworld", "shaders/include/projection.glsl");
        try (var in =
                net.minecraft.client.Minecraft.getInstance()
                        .getResourceManager()
                        .getResourceOrThrow(path)
                        .open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not load the RingWorld projection for this shader pack", e);
            return null;
        }
    }

    @ModifyVariable(method = "link", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private static String ringworld$curve(String vertex, @Local(argsOnly = true, ordinal = 0) String name) {
        RingShaderSupport.rendererDetected();
        if ((name.startsWith("sodium_terrain_") || name.startsWith("shadow_sodium_terrain_"))
                && vertex != null) {
            var decode = ringworld$decoded.matcher(vertex);
            if (decode.find() && !vertex.contains("ring_project_position")) {
                String projection = ringworld$projection();
                if (projection == null) return vertex;
                String flat = decode.group(1);
                String decl = "vec4 ringworldProjectLocal(vec4 local);\n";
                // Retain the renderer's decoded position; the common RingWorld GLSL supplies the
                // curve.
                String replacement =
                        "vec4 getVertexPosition() { return ringworldProjectLocal(" + flat + "); }";
                String parameters =
                        "uniform vec4 ringworldPackLayout;\n"
                                + "uniform vec4 ringworldPackDistortion;\n"
                                + "#define RingWorldLayout ivec4(ringworldPackLayout)\n"
                                + "#define RingWorldVertical ringworldPackLayout.zzzz\n"
                                + "#define RingWorldDistortion ringworldPackDistortion\n";
                vertex =
                        vertex.substring(0, decode.start())
                                + decl
                                + replacement
                                + vertex.substring(decode.end())
                                + "\n"
                                + parameters
                                + projection
                                + "\n"
                                + "vec4 ringworldProjectLocal(vec4 local) { if (ringworldPackLayout.x"
                                + " == 0.0) return local; vec3 eye=vec3(0.0,ringworldPackLayout.w,0.0);"
                                + " return vec4(ring_project_position(local.xyz+eye,eye),local.w);"
                                + " }\n";
            } else if (!vertex.contains("ring_project_position") && !ringworld$reportedDecoder) {
                ringworld$reportedDecoder = true;
                com.mojang.logging.LogUtils.getLogger().warn("Unrecognized shader terrain position in {}. Ring curvature could be missing.", name);
            }
        }
        return vertex;
    }
}
