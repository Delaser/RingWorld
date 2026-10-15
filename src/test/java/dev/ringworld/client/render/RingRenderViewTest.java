package dev.ringworld.client.render;

import dev.ringworld.client.RingDistortionTuning;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNearbyProjection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingRenderViewTest {
    @Test void publishedViewDoesNotChangeWhenLiveCorrectionSettingsChange() {
        var geometry = new RingGeometry(128, 2048);
        var camera = new Vec3(2044.25, 300, 0.5);
        var point = new Vec3(2070.5, 64, 12);
        var box = new AABB(2064, -64, -16, 2080, 320, 16);
        var view = new RingRenderView(geometry, camera,
                RingNearbyProjection.create(geometry, camera.y, 320, 3));
        var position = view.position(point);
        var bounds = view.bounds(box);
        boolean enabled = RingDistortionTuning.enabled();
        int distance = RingDistortionTuning.chunks();
        try {
            RingDistortionTuning.select(!enabled);
            RingDistortionTuning.distance(distance == 1 ? 8 : 1);
            assertEquals(position, view.position(point));
            assertEquals(bounds, view.bounds(box));
        } finally {
            RingDistortionTuning.select(enabled);
            RingDistortionTuning.distance(distance);
        }
    }

    @Test void ordinaryCylinderViewAlsoOwnsItsCanonicalCamera() {
        var geometry = new RingGeometry(256, 16384);
        var camera = new Vec3(16382.25, 120, -106.5);
        var view = new RingRenderView(geometry, camera, null);
        var point = new Vec3(16390.5, 90, -127);
        assertEquals(geometry.toCameraLocal(point, camera), view.position(point));
        assertEquals(camera, view.camera());
    }
}
