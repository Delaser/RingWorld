package dev.ringworld.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Pure periodic-X inputs shared by future raid-centre and wave-spawn adapters. */
public final class RingRaidSupport {
    private RingRaidSupport() { }

    /** Canonical block centre; Y/Z stay in vanilla intrinsic coordinates. */
    public record Center(int x, int y, int z) { }

    /** Inclusive canonical X range for a block or chunk lookup. */
    public record XWindow(int minX, int maxX) {
        public XWindow {
            if (minX > maxX) throw new IllegalArgumentException("window minimum must not exceed maximum");
        }
    }

    /** Squared intrinsic distance whose X component crosses the seam locally. */
    public static double periodicDistanceSquared(
            RingGeometry geometry, double sourceX, double sourceY, double sourceZ,
            double targetX, double targetY, double targetZ) {
        double deltaX = geometry.shortestCircumferenceDelta(sourceX, targetX);
        double deltaY = targetY - sourceY;
        double deltaZ = targetZ - sourceZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    /** Chooses the nearest canonical centre, retaining collection order for an exact tie. */
    public static Optional<Center> nearestActiveCenter(
            RingGeometry geometry, double referenceX, double referenceY, double referenceZ,
            Collection<Center> centers) {
        Center nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Center center : centers) {
            double distance = periodicDistanceSquared(geometry, referenceX, referenceY, referenceZ,
                    center.x(), center.y(), center.z());
            if (distance < nearestDistance) {
                nearest = center;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Converts a possibly nearest-image POI block X back to its one saved canonical centre. */
    public static Center canonicalCenter(RingGeometry geometry, int poiX, int poiY, int poiZ) {
        return new Center(geometry.wrapBlockX(poiX), poiY, poiZ);
    }

    /** Canonicalizes and de-duplicates equivalent POI centre inputs without reordering them. */
    public static List<Center> canonicalDistinctCenters(RingGeometry geometry, Collection<Center> poiCenters) {
        LinkedHashSet<Center> canonical = new LinkedHashSet<>();
        for (Center center : poiCenters) {
            canonical.add(canonicalCenter(geometry, center.x(), center.y(), center.z()));
        }
        return List.copyOf(canonical);
    }

    /**
     * Returns the three presentation query origins which expose canonical POIs
     * on both sides of the joined edge to vanilla's flat range search.
     */
    public static List<Integer> periodicQueryXs(RingGeometry geometry, int originX) {
        int canonical = geometry.wrapBlockX(originX);
        int circumference = geometry.circumferenceBlocks();
        return List.of(canonical, canonical - circumference, canonical + circumference);
    }

    /**
     * Averages unique POIs in the reference's nearest images, matching
     * vanilla's component-wise floor before returning one canonical centre.
     */
    public static Optional<Center> averageCanonicalPoiCenter(
            RingGeometry geometry, double referenceX, Collection<Center> poiCenters) {
        List<Center> canonical = canonicalDistinctCenters(geometry, poiCenters);
        if (canonical.isEmpty()) return Optional.empty();

        double sumX = 0.0;
        long sumY = 0L;
        long sumZ = 0L;
        for (Center center : canonical) {
            sumX += geometry.nearestImageX(center.x(), referenceX);
            sumY += center.y();
            sumZ += center.z();
        }
        int count = canonical.size();
        return Optional.of(canonicalCenter(
                geometry,
                (int) Math.floor(sumX / count),
                Math.toIntExact(Math.floorDiv(sumY, count)),
                Math.toIntExact(Math.floorDiv(sumZ, count))));
    }

    /** Splits an inclusive presentation-block range into one or two canonical storage windows. */
    public static List<XWindow> canonicalBlockWindows(RingGeometry geometry, int minBlockX, int maxBlockX) {
        if (minBlockX > maxBlockX) throw new IllegalArgumentException("window minimum must not exceed maximum");
        long width = (long) maxBlockX - minBlockX + 1L;
        int circumference = geometry.circumferenceBlocks();
        if (width >= circumference) return List.of(new XWindow(0, circumference - 1));

        int start = geometry.wrapBlockX(minBlockX);
        int endExclusive = start + (int) width;
        if (endExclusive <= circumference) return List.of(new XWindow(start, endExclusive - 1));
        return List.of(new XWindow(start, circumference - 1), new XWindow(0, endExclusive - circumference - 1));
    }

    /** Converts canonical block windows into inclusive canonical chunk windows for spawn readiness. */
    public static List<XWindow> canonicalChunkWindows(RingGeometry geometry, int minBlockX, int maxBlockX) {
        List<XWindow> blockWindows = canonicalBlockWindows(geometry, minBlockX, maxBlockX);
        List<XWindow> chunks = new ArrayList<>(blockWindows.size());
        for (XWindow window : blockWindows) {
            chunks.add(new XWindow(Math.floorDiv(window.minX(), 16), Math.floorDiv(window.maxX(), 16)));
        }
        return List.copyOf(chunks);
    }
}
