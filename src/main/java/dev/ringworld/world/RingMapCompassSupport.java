package dev.ringworld.world;

/**
 * Loader-neutral nearest-image rules for vanilla maps and compass needles.
 *
 * <p>Map pixels and decorations are relative to a saved map centre, while a
 * compass target is relative to its holder. Neither relationship may use a
 * flat X subtraction across the RingWorld seam.</p>
 */
public final class RingMapCompassSupport {
    private RingMapCompassSupport() { }

    /** Returns the image of a canonical map sample closest to the map centre. */
    public static double nearestMapImageX(RingGeometry geometry, double canonicalX, int mapCenterX) {
        return geometry.nearestImageX(canonicalX, mapCenterX);
    }

    /** Converts a map sampling image chunk into the one canonical stored chunk. */
    public static int canonicalMapSampleChunkX(RingGeometry geometry, int imageChunkX) {
        return RingChunkCoordinates.wrapChunkX(imageChunkX, geometry);
    }

    /** Returns the banner block image used by vanilla's in-map toggle gate. */
    public static int nearestMapBannerBlockX(RingGeometry geometry, int canonicalX, int mapCenterX) {
        return new RingTopology(geometry).imageBlockNear(canonicalX, mapCenterX);
    }

    /** Computes the decoration delta from its map centre through the nearest image. */
    public static float nearestMapDecorationDeltaX(
            RingGeometry geometry, int mapCenterX, int scale, double canonicalX) {
        return (float) ((nearestMapImageX(geometry, canonicalX, mapCenterX) - mapCenterX) / scale);
    }

    /** Returns the image of a canonical compass target closest to its holder. */
    public static double nearestCompassTargetX(RingGeometry geometry, double canonicalTargetX, double holderX) {
        return geometry.nearestImageX(canonicalTargetX, holderX);
    }

    /** Mirrors vanilla's exact-target rejection using shortest periodic X distance. */
    public static boolean isCompassTargetDistinct(
            RingGeometry geometry,
            double targetX, double targetY, double targetZ,
            double holderX, double holderY, double holderZ) {
        double dx = geometry.shortestCircumferenceDelta(holderX, targetX);
        double dy = targetY - holderY;
        double dz = targetZ - holderZ;
        return dx * dx + dy * dy + dz * dz >= 1.0E-5F;
    }
}
