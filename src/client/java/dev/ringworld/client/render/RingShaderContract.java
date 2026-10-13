package dev.ringworld.client.render;

import net.minecraft.resources.Identifier;

/** Shared material-shader contract, independent of a particular renderer mod. */
public final class RingShaderContract {
    public static final String UNIFORM = "RingWorld";
    public static final int UNIFORM_BYTES = 11 * 16;
    private RingShaderContract() { }

    public static boolean usesRingUniforms(Identifier shader) {
        if (shader == null) return false;
        String path = shader.getPath();
        if (path.startsWith("core/")) path = path.substring(5);
        return shader.getNamespace().equals("ringworld") && path.equals("ring_surface")
                || shader.getNamespace().equals("minecraft")
                && (path.equals("terrain") || path.equals("rendertype_clouds") || path.equals("clouds"));
    }
}
