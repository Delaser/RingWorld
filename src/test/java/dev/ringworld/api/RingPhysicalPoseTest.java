package dev.ringworld.api;

import dev.ringworld.world.RingGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingPhysicalPoseTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void cardinalPosesExposeStablePhysicalBasisAndVanillaViewDirection() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingPhysicalPose zero = RingPhysicalPose.fromIntrinsic(
                geometry, new Vec3(0.0, 64.0, 0.0), 0.0F, 0.0F);

        assertVector(new Vec3(0.0, 0.0, 1.0), zero.circumferenceTangent());
        assertVector(new Vec3(0.0, -1.0, 0.0), zero.localUp());
        assertVector(new Vec3(1.0, 0.0, 0.0), zero.widthDirection());
        assertVector(new Vec3(1.0, 0.0, 0.0), zero.viewDirection());

        RingPhysicalPose quarter = RingPhysicalPose.fromIntrinsic(
                geometry, new Vec3(4_096.0, 64.0, 0.0), 90.0F, 0.0F);
        assertVector(new Vec3(0.0, -1.0, 0.0), quarter.circumferenceTangent());
        assertVector(new Vec3(0.0, 0.0, -1.0), quarter.localUp());
        assertVector(new Vec3(0.0, 1.0, 0.0), quarter.viewDirection());
    }

    @Test
    void pitchUsesTheRenderedLocalUpAxis() {
        RingPhysicalPose pose = RingPhysicalPose.fromIntrinsic(
                new RingGeometry(256, 16_384),
                new Vec3(0.0, 64.0, 0.0), 0.0F, -90.0F);

        assertVector(pose.localUp(), pose.viewDirection());
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
