package dev.ringworld.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RingObjectTransformTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void embedsAnchorAndTangentFrameFromTheSameIntrinsicPoint() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        Vec3 camera = new Vec3(12.25, 80.0, -3.5);
        Vec3 relative = new Vec3(73.5, 4.25, 9.0);

        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, camera, relative.x, relative.y, relative.z);
        Vec3 anchor = camera.add(relative);

        assertVecEquals(geometry.toCameraLocal(anchor, camera), transform.cameraLocalPosition());
        assertEquals(
                geometry.tangentFrameAngle(camera.x, anchor.x),
                transform.tangentAngleRadians(), EPSILON);
    }

    @Test
    void choosesTheContinuousTangentFrameAcrossTheSeam() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        Vec3 camera = new Vec3(2_047.75, 64.0, 0.0);

        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, camera, 0.5, 0.0, 0.0);

        assertEquals(Math.PI * 2.0 * 0.5 / 2_048.0,
                transform.tangentAngleRadians(), EPSILON);
        assertVecEquals(
                geometry.toCameraLocal(new Vec3(2_048.25, 64.0, 0.0), camera),
                transform.cameraLocalPosition());
    }

    @Test
    void tangentScaleMatchesCurvedTerrainAtDepthAndAboveSurface() {
        for (int circumference : new int[]{2_048, 16_384}) {
            RingGeometry geometry = new RingGeometry(256, circumference);
            Vec3 camera = new Vec3(12.25, 80.0, 0.0);
            for (double anchorY : new double[]{-60.0, 64.0, 100.0}) {
                double relativeX = 73.5;
                RingObjectTransform pose = RingObjectTransform.fromCameraRelative(
                        geometry, camera, relativeX, anchorY - camera.y, 0.0);
                assertEquals(geometry.physicalRadiusAt(anchorY) / geometry.radius(),
                        pose.tangentScale(), EPSILON);

                double step = 1.0e-4;
                Vec3 anchor = camera.add(relativeX, anchorY - camera.y, 0.0);
                Vec3 before = geometry.toCameraLocal(anchor.add(-step, 0.0, 0.0), camera);
                Vec3 after = geometry.toCameraLocal(anchor.add(step, 0.0, 0.0), camera);
                assertEquals(pose.tangentScale() * Math.cos(pose.tangentAngleRadians()),
                        (after.x - before.x) / (2.0 * step), 1.0e-6);
                assertEquals(pose.tangentScale() * Math.sin(pose.tangentAngleRadians()),
                        (after.y - before.y) / (2.0 * step), 1.0e-6);
            }
        }
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
