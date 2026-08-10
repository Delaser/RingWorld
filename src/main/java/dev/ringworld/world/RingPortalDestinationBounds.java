package dev.ringworld.world;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Pure destination bounds for portal lookup and creation in the finite Overworld band. */
public final class RingPortalDestinationBounds {
    /** Vanilla {@code PortalForcer#createPortal} searches this far from its anchor. */
    public static final int CREATION_SEARCH_RADIUS = 16;
    /** Covers the widest vanilla portal frame/foundation offset from a portal block. */
    public static final int FRAME_CLEARANCE = 3;

    private RingPortalDestinationBounds() { }

    /** Lowest Z at which a portal block and its complete frame remain clear of the low rim. */
    public static int safePortalMinZ(RingGeometry geometry) {
        return geometry.minWidthZ() + RingGenerationBoundary.RIM_THICKNESS + FRAME_CLEARANCE;
    }

    /** Highest Z at which a portal block and its complete frame remain clear of the high rim. */
    public static int safePortalMaxZ(RingGeometry geometry) {
        return geometry.maxWidthZ() - RingGenerationBoundary.RIM_THICKNESS - FRAME_CLEARANCE;
    }

    /** Lowest creation/search anchor whose entire 16-block creation sweep stays safe. */
    public static int safeAnchorMinZ(RingGeometry geometry) {
        return safePortalMinZ(geometry) + CREATION_SEARCH_RADIUS;
    }

    /** Highest creation/search anchor whose entire 16-block creation sweep stays safe. */
    public static int safeAnchorMaxZ(RingGeometry geometry) {
        return safePortalMaxZ(geometry) - CREATION_SEARCH_RADIUS;
    }

    /**
     * Normalizes an already dimension-scaled Overworld target. X is periodic;
     * Y is unchanged; finite Z is clamped far enough inward for vanilla's
     * complete portal-creation search envelope.
     */
    public static BlockPos normalizeSearchAnchor(RingGeometry geometry, BlockPos target) {
        int minZ = safeAnchorMinZ(geometry);
        int maxZ = safeAnchorMaxZ(geometry);
        if (minZ > maxZ) {
            throw new IllegalArgumentException("ring width cannot contain a safe Nether portal search envelope");
        }
        int z = Math.max(minZ, Math.min(maxZ, target.getZ()));
        return new BlockPos(geometry.wrapBlockX(target.getX()), target.getY(), z);
    }

    /** True when a discovered portal block and its frame remain inside the playable interior. */
    public static boolean isSafePortalBlock(RingGeometry geometry, BlockPos portalBlock) {
        return portalBlock.getZ() >= safePortalMinZ(geometry)
                && portalBlock.getZ() <= safePortalMaxZ(geometry);
    }

    /** Flat POI queries centred on all three local X images expose either side of the seam. */
    public static List<BlockPos> periodicQueryAnchors(RingGeometry geometry, BlockPos normalizedAnchor) {
        int x = geometry.wrapBlockX(normalizedAnchor.getX());
        int circumference = geometry.circumferenceBlocks();
        return List.of(
                new BlockPos(x, normalizedAnchor.getY(), normalizedAnchor.getZ()),
                new BlockPos(x - circumference, normalizedAnchor.getY(), normalizedAnchor.getZ()),
                new BlockPos(x + circumference, normalizedAnchor.getY(), normalizedAnchor.getZ()));
    }

    /** Periodic-X equivalent of vanilla's squared distance used to select a portal POI. */
    public static double periodicDistanceSquared(
            RingGeometry geometry, BlockPos anchor, BlockPos candidate) {
        double dx = geometry.shortestCircumferenceDelta(anchor.getX(), candidate.getX());
        double dy = candidate.getY() - anchor.getY();
        double dz = candidate.getZ() - anchor.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
