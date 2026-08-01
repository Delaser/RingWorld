package dev.ringworld.api;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Versioned, loader-neutral inventory of high-confidence unsupported combinations. */
public final class RingCompatibilityContract {
    public static final int VERSION = 1;

    private static final List<Conflict> KNOWN_CONFLICTS = List.of(
            new Conflict("sodium", "Sodium", Area.RENDERER,
                    "replaces the chunk renderer and terrain shader path RingWorld bends"),
            new Conflict("iris", "Iris", Area.SHADERS,
                    "owns a shader pipeline that does not implement RingWorld's extended globals"),
            new Conflict("vulkanmod", "VulkanMod", Area.RENDERER,
                    "replaces the OpenGL renderer and RingWorld shader contract"),
            new Conflict("canvas", "Canvas Renderer", Area.RENDERER,
                    "replaces Fabric's vanilla-compatible renderer path"),
            new Conflict("distanthorizons", "Distant Horizons", Area.DISTANT_TERRAIN,
                    "adds an independent flat-world LOD that conflicts with the atlas ring"),
            new Conflict("bobby", "Bobby", Area.CHUNK_CHART,
                    "retains client chunks outside RingWorld's transient presentation chart"),
            new Conflict("imm_ptl_core", "Immersive Portals", Area.TOPOLOGY,
                    "rewrites world views and entity/chunk relationships RingWorld makes periodic"),
            new Conflict("gravity_changer", "Gravity Changer", Area.GRAVITY,
                    "changes vanilla -Y gameplay gravity required by RingWorld's intrinsic coordinates"),
            new Conflict("c2me", "C2ME", Area.CHUNK_PIPELINE,
                    "rewrites chunk and world-generation internals not covered by RingWorld's seam gate"),
            new Conflict("optifabric", "OptiFabric", Area.RENDERER,
                    "replaces terrain and shader internals used by curved rendering"),
            new Conflict("nvidium", "Nvidium", Area.RENDERER,
                    "replaces Sodium's chunk renderer with another unsupported terrain pipeline"));

    private RingCompatibilityContract() { }

    public static List<Conflict> knownConflicts() {
        return KNOWN_CONFLICTS;
    }

    public static List<Conflict> findLoadedConflicts(Collection<String> loadedModIds) {
        Set<String> normalized = loadedModIds.stream()
                .map(id -> id.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return KNOWN_CONFLICTS.stream()
                .filter(conflict -> normalized.contains(conflict.modId()))
                .toList();
    }

    public enum Area {
        RENDERER,
        SHADERS,
        DISTANT_TERRAIN,
        CHUNK_CHART,
        TOPOLOGY,
        GRAVITY,
        CHUNK_PIPELINE
    }

    public record Conflict(String modId, String displayName, Area area, String reason) { }
}
