package dev.ringworld.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Loader-neutral presentation and curved-culling operations for compact boxes. */
public final class RingPresentationBounds {
    private RingPresentationBounds() { }

    /**
     * Shifts one whole intrinsic box by the integer lap that places its anchor
     * nearest the supplied presentation-chart reference. Min/max corners are
     * never wrapped independently.
     */
    static AABB nearestImageBoxOrNull(AABB box, double anchorX,
                                      double referenceChartX, RingGeometry geometry) {
        if (geometry == null || !isCompactFiniteBox(box, geometry)
                || !Double.isFinite(anchorX) || !Double.isFinite(referenceChartX)) return null;
        double imageAnchor = geometry.nearestImageX(anchorX, referenceChartX);
        double shiftX = imageAnchor - anchorX;
        if (!Double.isFinite(imageAnchor) || !Double.isFinite(shiftX)) return null;
        AABB shifted = shiftX == 0.0 ? box : box.move(shiftX, 0.0, 0.0);
        return isFiniteBox(shifted) ? shifted : null;
    }

    /**
     * Curves one independently unwrapped box and converts the result from
     * camera-local coordinates to a renderer's render-origin-local space.
     * Returns {@code null} when any input or intermediate result cannot be
     * represented safely; culling callers must treat that sentinel as visible.
     */
    public static AABB toRenderOriginLocalCurvedBoundsOrNull(
            AABB box, double anchorX, Vec3 cameraPosition,
            Vec3 renderOrigin, RingGeometry geometry) {
        if (!isFiniteVector(cameraPosition) || !isFiniteVector(renderOrigin)) return null;
        AABB image = nearestImageBoxOrNull(box, anchorX, cameraPosition.x, geometry);
        if (image == null) return null;
        AABB cameraLocal = geometry.toCameraLocalBounds(image, cameraPosition);
        if (!isFiniteBox(cameraLocal)) return null;
        double offsetX = cameraPosition.x - renderOrigin.x;
        double offsetY = cameraPosition.y - renderOrigin.y;
        double offsetZ = cameraPosition.z - renderOrigin.z;
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)
                || !Double.isFinite(offsetZ)) return null;
        AABB renderOriginLocal = cameraLocal.move(offsetX, offsetY, offsetZ);
        return isFiniteBox(renderOriginLocal) ? renderOriginLocal : null;
    }

    private static boolean isCompactFiniteBox(AABB box, RingGeometry geometry) {
        return isFiniteBox(box)
                && Double.isFinite(box.getXsize())
                && box.getXsize() < geometry.circumferenceBlocks() * 0.5;
    }

    private static boolean isFiniteBox(AABB box) {
        return box != null
                && Double.isFinite(box.minX) && Double.isFinite(box.minY)
                && Double.isFinite(box.minZ) && Double.isFinite(box.maxX)
                && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }

    private static boolean isFiniteVector(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
