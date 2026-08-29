package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.world.RingGeometry;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingCreate610FixtureProjectionTest {
    private static final int WIDTH = 1_280;
    private static final int HEIGHT = 720;

    @Test
    void centerAimProjectsTargetCenterToViewportCenter() {
        RingGeometry geometry = new RingGeometry(416, 2_048);
        var aim = RingCreate610FixtureProjection.aim(
                geometry, new Vec3(100.0, 80.0, 20.0),
                List.of(new Vec3(100.0, 80.0, 40.0)),
                0.0, WIDTH, HEIGHT, 70.0);

        assertEquals(0.0, aim.yaw(), 1.0e-4);
        assertEquals(0.0, aim.pitch(), 1.0e-4);
        assertEquals(WIDTH / 2.0, aim.projection().centerX(), 1.0e-6);
        assertEquals(HEIGHT / 2.0, aim.projection().centerY(), 1.0e-6);
        assertTrue(aim.projection().centerInViewport());
    }

    @Test
    void yawOffsetsMoveProjectionToOppositeViewportSides() {
        List<Vec3> points = List.of(new Vec3(0.0, 0.0, 20.0));
        Vec3 center = points.getFirst();
        var left = RingCreate610FixtureProjection.projectCameraLocal(
                points, center, 20.0F, 0.0F, WIDTH, HEIGHT, 70.0);
        var right = RingCreate610FixtureProjection.projectCameraLocal(
                points, center, -20.0F, 0.0F, WIDTH, HEIGHT, 70.0);

        assertTrue(left.centerX() < WIDTH / 2.0);
        assertTrue(right.centerX() > WIDTH / 2.0);
    }

    @Test
    void rejectsPointsBehindCamera() {
        List<Vec3> behind = List.of(new Vec3(0.0, 0.0, -10.0));
        var projection = RingCreate610FixtureProjection.projectCameraLocal(
                behind, behind.getFirst(), 0.0F, 0.0F, WIDTH, HEIGHT, 70.0);

        assertEquals(0, projection.projectedPoints());
        assertFalse(projection.centerInViewport());
        assertFalse(projection.intersectsViewport(WIDTH, HEIGHT));
    }

    @Test
    void viewportIntersectionAcceptsPartialBoundsAndRejectsWhollyOutsideBounds() {
        List<Vec3> partial = List.of(new Vec3(0.0, 0.0, 10.0), new Vec3(20.0, 0.0, 10.0));
        var partialProjection = RingCreate610FixtureProjection.projectCameraLocal(
                partial, new Vec3(10.0, 0.0, 10.0), 0.0F, 0.0F, WIDTH, HEIGHT, 70.0);
        List<Vec3> outside = List.of(new Vec3(20.0, 0.0, 10.0), new Vec3(30.0, 0.0, 10.0));
        var outsideProjection = RingCreate610FixtureProjection.projectCameraLocal(
                outside, new Vec3(25.0, 0.0, 10.0), 0.0F, 0.0F, WIDTH, HEIGHT, 70.0);

        assertTrue(partialProjection.intersectsViewport(WIDTH, HEIGHT));
        assertFalse(outsideProjection.intersectsViewport(WIDTH, HEIGHT));
    }
}
