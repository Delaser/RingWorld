package dev.ringworld.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RingNearbyProjectionTest {
    @Test void givesUnitNearbySpacingAtBothHeightExtremesWithoutInversion() {
        var geometry = new RingGeometry(128, 1024);
        for (double y : new double[]{-64, 64, 319}) {
            var mapping = RingNearbyProjection.create(geometry, y, 320, 3);
            var camera = new Vec3(1023.5, y, 0);
            assertTrue(mapping.radiusAt(320) >= 96 - 1e-9);
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
    @Test void verticalCameraMovementOnlyTranslatesTheScene() {
        var geometry = new RingGeometry(128, 2048);
        var baselineCamera = new Vec3(512, 64, 10);
        var baseline = RingNearbyProjection.create(geometry, baselineCamera.y, 320, 3);
        var box = new AABB(500, -64, -64, 700, 320, 64);
        var baselineBounds = baseline.bounds(box, baselineCamera);
        for (double cameraY : new double[]{-100, 64, 321.62, 500}) {
            var camera = new Vec3(baselineCamera.x, cameraY, baselineCamera.z);
            var mapping = RingNearbyProjection.create(geometry, cameraY, 320, 3);
            assertEquals(baseline.referenceRadius(), mapping.referenceRadius());
            for (double y : new double[]{-64, 64, 319}) {
                for (double x : new double[]{-1024, -192, -96, -48, 0, 20, 48, 96, 192, 512, 1024}) {
                    var point = new Vec3(camera.x + x, y, 30);
                    var expected = baseline.position(point, baselineCamera);
                    var actual = mapping.position(point, camera);
                    assertEquals(expected.x, actual.x, 1e-9);
                    assertEquals(expected.y - (cameraY - baselineCamera.y), actual.y, 1e-9);
                    assertEquals(expected.z, actual.z, 1e-9);
                }
                assertEquals(1, mapping.object(camera, 20, y - cameraY, 0).tangentScale(), 1e-9);
            }
            var bounds = mapping.bounds(box, camera);
            assertEquals(baselineBounds.minX, bounds.minX, 1e-9);
            assertEquals(baselineBounds.maxX, bounds.maxX, 1e-9);
            assertEquals(baselineBounds.minY - (cameraY - baselineCamera.y), bounds.minY, 1e-9);
            assertEquals(baselineBounds.maxY - (cameraY - baselineCamera.y), bounds.maxY, 1e-9);
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
