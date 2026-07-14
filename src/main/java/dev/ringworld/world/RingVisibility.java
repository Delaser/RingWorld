package dev.ringworld.world;

/**
 * Geometry for the atmospheric, distant representation of the inhabited
 * surface. Nearby blocks remain authoritative; this helper only determines
 * where a low-detail continuation can fade in behind loaded chunks.
 */
public final class RingVisibility {
    public static final double SKY_RADIUS = 90.0;
    public static final double HANDOFF_START_FRACTION = 0.62;
    public static final double HANDOFF_END_FRACTION = 0.98;
    public static final double HANDOFF_FOG_END_FRACTION = 1.55;
    public static final double HANDOFF_FOG_PLATEAU_FRACTION = 0.08;
    public static final double OPPOSITE_WIDTH_SCALE = 0.50;

    private RingVisibility() { }

    /** Scale that fits the complete ring ribbon inside the celestial sky shell. */
    public static double skyScale(RingGeometry geometry) {
        double diameter = geometry.radius() * 2.0;
        double maximumDistance = Math.hypot(diameter, geometry.widthBlocks());
        return SKY_RADIUS / maximumDistance;
    }

    /**
     * Surface distance at which the proxy starts. It deliberately overlaps
     * the loaded terrain radius so chunks and their fog hide its transparent
     * beginning.
     */
    public static double handoffStartDistance(RingGeometry geometry, double renderDistanceBlocks) {
        return Math.min(renderDistanceBlocks * HANDOFF_START_FRACTION,
                geometry.circumferenceBlocks() * 0.20);
    }

    /**
     * Surface distance at which the proxy has become fully opaque. This lies
     * just inside the nominal chunk radius. Real terrain is still drawn over
     * the proxy, while the last few fogged chunks hide the LOD transition and
     * the first unloaded chunk already has a complete continuation behind it.
     */
    public static double handoffEndDistance(RingGeometry geometry, double renderDistanceBlocks) {
        double start = handoffStartDistance(geometry, renderDistanceBlocks);
        double desired = Math.max(start + 16.0, renderDistanceBlocks * HANDOFF_END_FRACTION);
        return Math.min(desired, geometry.circumferenceBlocks() * 0.45);
    }

    public static double handoffStartAngle(RingGeometry geometry, double renderDistanceBlocks) {
        return handoffStartDistance(geometry, renderDistanceBlocks) / geometry.radius();
    }

    /**
     * Smooth alpha for a point at {@code deltaAngle} around the ring. Both
     * apparent bases use the shortest surface distance back to the camera.
     */
    public static double proxyAlpha(RingGeometry geometry, double deltaAngle,
                                    double renderDistanceBlocks) {
        return proxyAlpha(geometry, deltaAngle, 0.0, renderDistanceBlocks);
    }

    /**
     * Two-axis proxy alpha matching Minecraft's circular horizontal chunk
     * radius. {@code widthDelta} is the vertex's offset across the band from
     * the camera, so the live/proxy boundary does not become a straight line.
     */
    public static double proxyAlpha(RingGeometry geometry, double deltaAngle,
                                    double widthDelta, double renderDistanceBlocks) {
        double distance = intrinsicSurfaceDistance(geometry, deltaAngle, widthDelta);
        double start = handoffStartDistance(geometry, renderDistanceBlocks);
        double end = handoffEndDistance(geometry, renderDistanceBlocks);
        if (distance <= start) return 0.0;
        if (distance >= end || end <= start) return 1.0;
        double linear = (distance - start) / (end - start);
        return linear * linear * (3.0 - 2.0 * linear);
    }

    /**
     * Amount of procedural terrain colour visible through atmospheric haze.
     * At the real chunk edge this remains zero, so the proxy begins at the
     * exact live sky/fog colour; detail appears only after the loaded terrain
     * has already ended and becomes fully readable farther around the Arch.
     */
    public static double proxyTerrainDetail(RingGeometry geometry, double deltaAngle,
                                            double renderDistanceBlocks) {
        return proxyTerrainDetail(geometry, deltaAngle, 0.0, renderDistanceBlocks);
    }

    public static double proxyTerrainDetail(RingGeometry geometry, double deltaAngle,
                                            double widthDelta, double renderDistanceBlocks) {
        double distance = intrinsicSurfaceDistance(geometry, deltaAngle, widthDelta);
        double start = renderDistanceBlocks;
        double end = Math.min(renderDistanceBlocks * 1.80,
                geometry.circumferenceBlocks() * 0.45);
        if (distance <= start) return 0.0;
        if (distance >= end || end <= start) return 1.0;
        double linear = (distance - start) / (end - start);
        return linear * linear * (3.0 - 2.0 * linear);
    }

    /**
     * Atmospheric veil centred on the nominal chunk edge. It begins while
     * real terrain is still authoritative, reaches full fog exactly where
     * loaded geometry runs out, and then clears slowly enough for distant
     * proxy terrain to emerge without exposing the LOD join.
     */
    public static double handoffFog(RingGeometry geometry, double deltaAngle,
                                    double renderDistanceBlocks) {
        return handoffFog(geometry, deltaAngle, 0.0, renderDistanceBlocks);
    }

    public static double handoffFog(RingGeometry geometry, double deltaAngle,
                                    double widthDelta, double renderDistanceBlocks) {
        double distance = intrinsicSurfaceDistance(geometry, deltaAngle, widthDelta);
        double start = handoffStartDistance(geometry, renderDistanceBlocks);
        double peak = Math.min(renderDistanceBlocks,
                geometry.circumferenceBlocks() * 0.45);
        double plateauHalfWidth = Math.max(16.0,
                renderDistanceBlocks * HANDOFF_FOG_PLATEAU_FRACTION);
        double plateauStart = Math.max(start, peak - plateauHalfWidth);
        double plateauEnd = peak + plateauHalfWidth;
        double end = Math.min(Math.max(peak + 16.0,
                        renderDistanceBlocks * HANDOFF_FOG_END_FRACTION),
                geometry.circumferenceBlocks() * 0.49);
        if (distance <= start || distance >= end || peak <= start) return 0.0;
        if (distance < plateauStart) {
            return smoothstep((distance - start) / (plateauStart - start));
        }
        if (distance <= plateauEnd) return 1.0;
        if (end <= plateauEnd) return 0.0;
        return 1.0 - smoothstep((distance - plateauEnd) / (end - plateauEnd));
    }

    private static double smoothstep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double shortestSurfaceDistance(RingGeometry geometry, double deltaAngle) {
        double angle = deltaAngle - Math.floor(deltaAngle / (Math.PI * 2.0)) * Math.PI * 2.0;
        if (angle < 0.0) angle += Math.PI * 2.0;
        return Math.min(angle, Math.PI * 2.0 - angle) * geometry.radius();
    }

    private static double intrinsicSurfaceDistance(RingGeometry geometry, double deltaAngle,
                                                   double widthDelta) {
        return Math.hypot(shortestSurfaceDistance(geometry, deltaAngle), widthDelta);
    }

    /** Angular width of the opposite surface as seen across the ring centre. */
    public static double oppositeAngularWidth(RingGeometry geometry, double cameraZ) {
        double distance = geometry.radius() * 2.0;
        double lower = Math.atan2(geometry.minWidthZ() - cameraZ, distance);
        double upper = Math.atan2(geometry.maxWidthZ() + 1.0 - cameraZ, distance);
        return upper - lower;
    }

    /**
     * Art-directed width taper for the sky proxy. Nearby handoff geometry is
     * full width while the opposite surface is half width, exaggerating the
     * apparent radius without altering any playable blocks or collisions.
     */
    public static double distantWidthScale(double deltaAngle) {
        double fromCamera = Math.sin(deltaAngle * 0.5);
        // Keep the apparent width almost unchanged through both chunk/LOD
        // joins. Most of the art-directed narrowing now occurs around the far
        // side, where it cannot pull the proxy edge away from live terrain.
        return 1.0 - (1.0 - OPPOSITE_WIDTH_SCALE) * Math.pow(fromCamera, 8.0);
    }
}
