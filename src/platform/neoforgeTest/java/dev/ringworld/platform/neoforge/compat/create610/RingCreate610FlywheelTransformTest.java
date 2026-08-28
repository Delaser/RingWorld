package dev.ringworld.platform.neoforge.compat.create610;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class RingCreate610FlywheelTransformTest {
    @Test
    void curvedEmbeddingPrecedesUntouchedCreateLocalTransforms() {
        RingGeometry geometry = new RingGeometry(128, 2048);
        Vec3 camera = new Vec3(2046.25, 121.75, 9.5);
        Vec3i renderOrigin = new Vec3i(2032, 112, 0);
        Vec3 anchor = new Vec3(2049.5, 124.0, 12.25);
        Vec3 relativeToOrigin = anchor.subtract(
                renderOrigin.getX(), renderOrigin.getY(), renderOrigin.getZ());

        PoseStack actual = new PoseStack();
        RingCreate610FlywheelTransform.applyCurvedEmbedding(
                actual, relativeToOrigin, camera, renderOrigin, geometry);
        applyNontrivialCreateLocalTransform(actual);

        RingObjectTransform ring = RingObjectTransform.fromCameraRelative(
                geometry, camera, anchor.x - camera.x,
                anchor.y - camera.y, anchor.z - camera.z);
        Vec3 expectedPosition = ring.cameraLocalPosition().add(
                camera.x - renderOrigin.getX(), camera.y - renderOrigin.getY(),
                camera.z - renderOrigin.getZ());
        PoseStack expected = new PoseStack();
        expected.translate(expectedPosition.x, expectedPosition.y, expectedPosition.z);
        expected.mulPose(Axis.ZP.rotation((float) ring.tangentAngleRadians()));
        applyNontrivialCreateLocalTransform(expected);

        assertMatrixEquals(expected.last().pose(), actual.last().pose());
    }

    private static void applyNontrivialCreateLocalTransform(PoseStack matrices) {
        matrices.translate(0.375, -0.625, 1.25);
        matrices.mulPose(Axis.YP.rotation(0.73F));
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        float[] expectedValues = expected.get(new float[16]);
        float[] actualValues = actual.get(new float[16]);
        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals(expectedValues[i], actualValues[i], 1.0e-6F,
                    "matrix component " + i);
        }
    }
}
