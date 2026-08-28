package dev.ringworld.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RingPresentationBoundsTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(416, 2_048);

    @Test
    void shiftsTheWholeBoxIntoEitherNearbyPresentationChart() {
        AABB canonicalSeamBox = new AABB(-2.0, 60.0, -3.0, 2.0, 72.0, 3.0);

        AABB highChart = RingPresentationBounds.nearestImageBoxOrNull(
                canonicalSeamBox, 0.0, 2_047.5, GEOMETRY);
        AABB lowChart = RingPresentationBounds.nearestImageBoxOrNull(
                canonicalSeamBox, 0.0, -0.5, GEOMETRY);

        assertNotNull(highChart);
        assertEquals(2_046.0, highChart.minX);
        assertEquals(2_050.0, highChart.maxX);
        assertSame(canonicalSeamBox, lowChart);
        assertEquals(4.0, highChart.getXsize());
        assertEquals(4.0, lowChart.getXsize());
    }

    @Test
    void convertsCameraLocalBoundsToASeparateRenderOrigin() {
        AABB box = new AABB(-1.5, 62.0, 4.0, 1.5, 70.0, 9.0);
        Vec3 camera = new Vec3(2_047.5, 65.0, 6.0);
        Vec3 renderOrigin = new Vec3(2_032.0, 48.0, 0.0);
        AABB image = RingPresentationBounds.nearestImageBoxOrNull(
                box, 0.0, camera.x, GEOMETRY);
        assertNotNull(image);
        AABB expected = GEOMETRY.toCameraLocalBounds(image, camera).move(
                camera.x - renderOrigin.x,
                camera.y - renderOrigin.y,
                camera.z - renderOrigin.z);

        assertBoxEquals(expected, RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0, camera, renderOrigin, GEOMETRY));
    }

    @Test
    void bothChartsProduceTheSameLocalCurvedEnvelope() {
        AABB box = new AABB(-2.0, 60.0, -2.0, 2.0, 72.0, 2.0);
        AABB high = RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0,
                new Vec3(2_047.5, 65.0, 0.0), new Vec3(2_032.0, 48.0, 0.0), GEOMETRY);
        AABB low = RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0,
                new Vec3(-0.5, 65.0, 0.0), new Vec3(-16.0, 48.0, 0.0), GEOMETRY);

        assertNotNull(high);
        assertNotNull(low);
        assertBoxEquals(high, low);
    }

    @Test
    void currentAndPreviousBoxesAreCurvedBeforeTheirUnion() {
        Vec3 camera = new Vec3(2_047.5, 65.0, 0.0);
        Vec3 renderOrigin = new Vec3(2_032.0, 48.0, 0.0);
        AABB currentRaw = new AABB(-2.0, 60.0, -2.0, 2.0, 72.0, 2.0);
        AABB previousRaw = new AABB(2_043.0, 59.0, -3.0, 2_047.0, 73.0, 3.0);
        AABB current = RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                currentRaw,
                0.0, camera, renderOrigin, GEOMETRY);
        AABB previous = RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                previousRaw,
                2_045.0, camera, renderOrigin, GEOMETRY);

        assertNotNull(current);
        assertNotNull(previous);
        AABB union = current.minmax(previous);
        assertNotNull(union);
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                currentRaw.minmax(previousRaw), 0.0,
                camera, renderOrigin, GEOMETRY));
    }

    @Test
    void invalidInputsAndIntermediateOverflowFailOpen() {
        AABB box = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        Vec3 camera = new Vec3(0.0, 64.0, 0.0);
        Vec3 renderOrigin = Vec3.ZERO;

        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                new AABB(0.0, 0.0, 0.0, 1_024.0, 1.0, 1.0),
                0.0, camera, renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                new AABB(Double.NaN, 0.0, 0.0, 1.0, 1.0, 1.0),
                0.0, camera, renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                null, 0.0, camera, renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, Double.NaN, camera, renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0, new Vec3(0.0, Double.NaN, 0.0), renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0, new Vec3(0.0, 64.0, Double.POSITIVE_INFINITY), renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0, camera, new Vec3(0.0, Double.NEGATIVE_INFINITY, 0.0), GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                new AABB(Double.MAX_VALUE, 0.0, 0.0,
                        Double.MAX_VALUE, 1.0, 1.0),
                Double.MAX_VALUE, new Vec3(-Double.MAX_VALUE, 64.0, 0.0),
                renderOrigin, GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0, new Vec3(Double.MAX_VALUE, 64.0, 0.0),
                new Vec3(-Double.MAX_VALUE, 0.0, 0.0), GEOMETRY));
        assertNull(RingPresentationBounds.toRenderOriginLocalCurvedBoundsOrNull(
                box, 0.0, camera, renderOrigin, null));
    }

    private static void assertBoxEquals(AABB expected, AABB actual) {
        assertEquals(expected.minX, actual.minX, 1.0e-9);
        assertEquals(expected.minY, actual.minY, 1.0e-9);
        assertEquals(expected.minZ, actual.minZ, 1.0e-9);
        assertEquals(expected.maxX, actual.maxX, 1.0e-9);
        assertEquals(expected.maxY, actual.maxY, 1.0e-9);
        assertEquals(expected.maxZ, actual.maxZ, 1.0e-9);
    }
}
