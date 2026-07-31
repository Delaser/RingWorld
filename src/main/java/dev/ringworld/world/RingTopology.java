package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;

/**
 * Authoritative topology operations for the periodic circumference axis.
 *
 * <p>Canonical coordinates select the single stored copy of world data.
 * Chart coordinates are transient continuous client/query coordinates. Live
 * server entities remain canonical; a chart coordinate exists only to select
 * a nearby periodic image without a visual discontinuity.</p>
 */
public final class RingTopology {
    private final RingGeometry geometry;

    public RingTopology(RingGeometry geometry) {
        this.geometry = geometry;
    }

    public RingGeometry geometry() {
        return geometry;
    }

    public double canonicalX(double chartX) {
        return geometry.wrapX(chartX);
    }

    public int canonicalBlockX(int chartBlockX) {
        return geometry.wrapBlockX(chartBlockX);
    }

    public int canonicalChunkX(int chartChunkX) {
        return RingChunkCoordinates.wrapChunkX(chartChunkX, geometry);
    }

    public int canonicalSectionX(int chartSectionX) {
        return canonicalChunkX(chartSectionX);
    }

    public double imageNear(double canonicalOrChartX, double observerChartX) {
        return geometry.nearestImageX(canonicalOrChartX, observerChartX);
    }

    public int imageBlockNear(int canonicalBlockX, double observerChartX) {
        double image = imageNear(canonicalBlockX, observerChartX);
        if (image < Integer.MIN_VALUE || image > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("ring block image exceeds vanilla coordinate range");
        }
        return (int) Math.floor(image);
    }

    public int imageChunkNear(int canonicalChunkX, int observerChartChunkX) {
        return RingChunkCoordinates.nearestImageChunkX(canonicalChunkX, observerChartChunkX, geometry);
    }

    public double deltaX(double fromChartX, double toCanonicalOrChartX) {
        return geometry.shortestCircumferenceDelta(fromChartX, toCanonicalOrChartX);
    }

    public double squaredHorizontalDistance(double fromX, double fromZ, double toX, double toZ) {
        double dx = deltaX(fromX, toX);
        double dz = toZ - fromZ;
        return dx * dx + dz * dz;
    }

    /**
     * Splits a chart-space query box into canonical storage windows. The
     * offset translates positions returned from a window back into the
     * caller's chart. Boxes at least one circumference wide cover storage
     * once, preventing duplicate entity results.
     */
    public List<QueryWindow> canonicalWindows(AABB chartBox) {
        double circumference = geometry.circumferenceBlocks();
        if (chartBox.getXsize() >= circumference) {
            return List.of(new QueryWindow(
                    new AABB(0.0, chartBox.minY, chartBox.minZ,
                            circumference, chartBox.maxY, chartBox.maxZ),
                    Math.floor(chartBox.minX / circumference) * circumference));
        }

        long firstImage = floorImageIndex(chartBox.minX, circumference);
        long lastImage = floorImageIndex(Math.nextDown(chartBox.maxX), circumference);
        List<QueryWindow> result = new ArrayList<>((int) (lastImage - firstImage + 1));
        for (long image = firstImage; image <= lastImage; image++) {
            double offset = image * circumference;
            double minX = Math.max(chartBox.minX, offset) - offset;
            double maxX = Math.min(chartBox.maxX, offset + circumference) - offset;
            result.add(new QueryWindow(
                    new AABB(minX, chartBox.minY, chartBox.minZ,
                            maxX, chartBox.maxY, chartBox.maxZ),
                    offset));
        }
        return List.copyOf(result);
    }

    /** Projects a canonical entity box into the image nearest an observer. */
    public AABB projectBoxNear(AABB canonicalBox, double observerChartX) {
        canonicalBox = canonicalBox(canonicalBox);
        double centerX = (canonicalBox.minX + canonicalBox.maxX) * 0.5;
        double projectedCenter = imageNear(centerX, observerChartX);
        return canonicalBox.move(projectedCenter - centerX, 0.0, 0.0);
    }

    /** Moves a small chart-space box onto the single canonical storage plane. */
    public AABB canonicalBox(AABB chartBox) {
        if (chartBox.getXsize() >= geometry.circumferenceBlocks()) return chartBox;
        double centerX = (chartBox.minX + chartBox.maxX) * 0.5;
        double canonicalCenter = canonicalX(centerX);
        return chartBox.move(canonicalCenter - centerX, 0.0, 0.0);
    }

    private static long floorImageIndex(double x, double circumference) {
        return (long) Math.floor(x / circumference);
    }

    public record QueryWindow(AABB canonicalBox, double chartOffset) {
        public AABB toChart(AABB box) {
            return box.move(chartOffset, 0.0, 0.0);
        }
    }
}
