package dev.ringworld.client.render;

import net.minecraft.resources.Identifier;

/** Shared material-shader contract, independent of a particular renderer mod. */
public final class RingShaderContract {
    public static final String UNIFORM = "RingWorld";
    public static final int UNIFORM_BYTES = 12 * 16;
    private RingShaderContract() { }

    /**
     * Both stages exchange private varyings. A pipeline borrowing just one stock stage
     * belongs to its creator and must keep its original shaders and binding layout.
     */
    public static Identifier pairedMaterial(Identifier vertex, Identifier fragment) {
        if (vertex == null || !vertex.equals(fragment)) return null;
        String path = vertex.getPath();
        if (path.startsWith("core/")) path = path.substring(5);
        boolean owned = vertex.getNamespace().equals("ringworld");
        if (owned && path.equals("ring_surface")) return vertex;
        if (!owned && !vertex.getNamespace().equals("minecraft")) return null;
        return switch (path) {
            case "terrain", "rendertype_clouds", "clouds" -> Identifier.fromNamespaceAndPath("ringworld", "core/" + path);
            default -> null;
        };
    }
}
