package dev.ringworld.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RingNearbyProjectionTest {
    @Test void givesUnitNearbySpacingAtBothHeightExtremesWithoutInversion() {
        var geometry = new RingGeometry(128, 1024);
        for (double y : new double[]{-64, 64, 321.62, 500}) {
            var mapping = RingNearbyProjection.create(geometry, y, 320, 3);
            var camera = new Vec3(1023.5, y, 0);
            assertTrue(mapping.cameraRadius() >= 96 - 1e-9);
            assertEquals(1, mapping.object(camera, 20, 0, 0).tangentScale(), 1e-9);
            assertEquals(1, mapping.position(camera.add(0, 1, 0), camera).y, 1e-9);
            assertEquals(128, mapping.position(camera.add(0, 0, 128), camera).z, 1e-9);
            assertEquals(1, mapping.position(camera.add(0.5, 0, 0), camera)
                    .distanceTo(mapping.position(camera.add(-0.5, 0, 0), camera)), 1e-5);
        }
    }
    @Test void staysMonotoneAndClosesTheRingAcrossAllDistances() {
        for (int circumference : new int[]{1024, 2048, 16384}) for (int chunks = 1; chunks <= 8; chunks++) {
            var mapping = RingNearbyProjection.create(new RingGeometry(128, circumference), 321.62, 320, chunks);
            assertEquals(Math.PI, mapping.angle(circumference / 2.0), 1e-9);
            assertEquals(Math.PI * 2, mapping.angle(circumference), 1e-9);
            double previous = mapping.angle(-circumference);
            for (double x = -circumference + 1; x <= circumference; x++) {
                assertTrue(mapping.angularSlope(x) > 0);
                assertTrue(mapping.angle(x) > previous);
                previous = mapping.angle(x);
            }
            for (double edge : new double[]{mapping.coreBlocks(), mapping.blendEnd(), circumference / 2.0}) {
                double derivative = (mapping.angle(edge + 1e-4) - mapping.angle(edge - 1e-4)) / 2e-4;
                assertEquals(mapping.angularSlope(edge), derivative, 1e-7);
            }
        }
    }
    @Test void groundBelowHighCameraKeepsOriginalAngularSpacing() {
        var geometry = new RingGeometry(128, 2048);
        var camera = new Vec3(512, 321.62, 0);
        var mapping = RingNearbyProjection.create(geometry, camera.y, 320, 3);
        for (double y : new double[]{-64, 64, camera.y - mapping.blendEnd(), camera.y + mapping.blendEnd()}) {
            for (double x : new double[]{-1024, -100, 0, 20, 100, 1024}) {
                assertEquals(x / geometry.radius(), mapping.angle(x, y), 1e-9);
                assertEquals(1 / geometry.radius(), mapping.angularSlope(x, y), 1e-9);
            }
        }
        // Only the documented small reference-radius safeguard remains below.
        assertTrue(mapping.object(camera, 20, 64 - camera.y, 0).tangentScale() < 1.1);
        assertEquals(1, mapping.object(camera, 20, 0, 0).tangentScale(), 1e-9);
        for (double edge : new double[]{camera.y - mapping.coreBlocks(), camera.y - mapping.blendEnd()}) {
            double slope = (mapping.angle(20, edge + 1e-4) - mapping.angle(20, edge - 1e-4)) / 2e-4;
            assertEquals(0, slope, 1e-7);
        }
    }
    @Test void curvedBoundsContainTerrainIncludingCanonicalSeam() {
        var geometry = new RingGeometry(128, 1024);
        var camera = new Vec3(1020, 321.62, 10);
        var mapping = RingNearbyProjection.create(geometry, camera.y, 320, 3);
        for (double x : new double[]{0, 48, 490, 1008}) {
            var box = new AABB(x, -64, -64, x + 32, 320, 64);
            var envelope = mapping.bounds(box, camera).inflate(1e-7);
            for (int sample = 0; sample <= 128; sample++) for (double y = -64; y <= 320; y += 2) {
                var point = mapping.position(new Vec3(x + sample / 4.0, y, 0), camera);
                assertTrue(envelope.contains(point), "bounds must contain every projected sample");
            }
        }
    }
}
