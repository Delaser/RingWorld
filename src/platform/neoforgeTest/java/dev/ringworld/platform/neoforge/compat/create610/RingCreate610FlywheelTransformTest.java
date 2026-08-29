package dev.ringworld.platform.neoforge.compat.create610;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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

    @Test
    void blockEmbeddingComposesAroundBlockOriginInBothPresentationCharts() {
        RingGeometry geometry = new RingGeometry(128, 2048);
        assertBlockEmbeddingAlgebra(
                geometry, new BlockPos(2, 124, 12),
                new Vec3(2046.25, 121.75, 9.5), new Vec3i(2032, 112, 0));
        assertBlockEmbeddingAlgebra(
                geometry, new BlockPos(2050, 124, 12),
                new Vec3(2053.75, 121.75, 9.5), new Vec3i(2048, 112, 0));
    }

    @Test
    void blockEmbeddingSurvivesRenderOriginRecreation() {
        RingGeometry geometry = new RingGeometry(128, 2048);
        BlockPos anchor = new BlockPos(2050, 124, 12);
        Vec3 camera = new Vec3(2053.75, 121.75, 9.5);
        Vector4f local = new Vector4f(0.375F, -0.625F, 1.25F, 1F);

        Vector4f first = embeddedPoint(
                geometry, anchor, camera, new Vec3i(2048, 112, 0), local);
        Vector4f recreated = embeddedPoint(
                geometry, anchor, camera, new Vec3i(2016, 96, -16), local);
        assertEquals(first.x + 32F, recreated.x, 1.0e-4F);
        assertEquals(first.y + 16F, recreated.y, 1.0e-4F);
        assertEquals(first.z + 16F, recreated.z, 1.0e-4F);
    }

    @Test
    void malformedBlockEmbeddingClearsPriorCurvature() {
        RingGeometry geometry = new RingGeometry(128, 2048);
        Matrix4f pose = new Matrix4f().translate(7, 8, 9);
        Matrix3f normal = new Matrix3f().rotateZ(0.5F);
        assertFalse(RingCreate610FlywheelTransform.setCurvedBlockEmbedding(
                pose, normal, new BlockPos(2, 124, 12),
                new Vec3(Double.NaN, 121.75, 9.5), new Vec3i(0, 0, 0), geometry));
        assertMatrixEquals(new Matrix4f(), pose);
        assertMatrixEquals(new Matrix3f(), normal);
    }

    private static void assertBlockEmbeddingAlgebra(
            RingGeometry geometry, BlockPos anchor, Vec3 camera, Vec3i renderOrigin) {
        Matrix4f embedding = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        assertTrue(RingCreate610FlywheelTransform.setCurvedBlockEmbedding(
                embedding, normal, anchor, camera, renderOrigin, geometry));

        Matrix4f nativeLocal = new Matrix4f()
                .translate(0.375F, -0.625F, 1.25F)
                .rotateY(0.73F);
        Matrix4f actual = new Matrix4f(embedding)
                .translate(anchor.getX() - renderOrigin.getX(),
                        anchor.getY() - renderOrigin.getY(),
                        anchor.getZ() - renderOrigin.getZ())
                .mul(nativeLocal);

        RingObjectTransform ring = RingObjectTransform.fromCameraRelative(
                geometry, camera,
                anchor.getX() - camera.x, anchor.getY() - camera.y,
                anchor.getZ() - camera.z);
        Vec3 curved = ring.cameraLocalPosition().add(
                camera.x - renderOrigin.getX(), camera.y - renderOrigin.getY(),
                camera.z - renderOrigin.getZ());
        Matrix4f expected = new Matrix4f()
                .translate((float) curved.x, (float) curved.y, (float) curved.z)
                .rotateZ((float) ring.tangentAngleRadians())
                .mul(nativeLocal);
        assertMatrixEquals(expected, actual, 1.0e-4F);
    }

    private static Vector4f embeddedPoint(
            RingGeometry geometry, BlockPos anchor, Vec3 camera,
            Vec3i renderOrigin, Vector4f local) {
        Matrix4f embedding = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        assertTrue(RingCreate610FlywheelTransform.setCurvedBlockEmbedding(
                embedding, normal, anchor, camera, renderOrigin, geometry));
        return embedding.translate(
                        anchor.getX() - renderOrigin.getX(),
                        anchor.getY() - renderOrigin.getY(),
                        anchor.getZ() - renderOrigin.getZ())
                .transform(new Vector4f(local));
    }

    private static void applyNontrivialCreateLocalTransform(PoseStack matrices) {
        matrices.translate(0.375, -0.625, 1.25);
        matrices.mulPose(Axis.YP.rotation(0.73F));
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        assertMatrixEquals(expected, actual, 1.0e-6F);
    }

    private static void assertMatrixEquals(
            Matrix4f expected, Matrix4f actual, float tolerance) {
        float[] expectedValues = expected.get(new float[16]);
        float[] actualValues = actual.get(new float[16]);
        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals(expectedValues[i], actualValues[i], tolerance,
                    "matrix component " + i);
        }
    }

    private static void assertMatrixEquals(Matrix3f expected, Matrix3f actual) {
        float[] expectedValues = expected.get(new float[9]);
        float[] actualValues = actual.get(new float[9]);
        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals(expectedValues[i], actualValues[i], 1.0e-6F,
                    "matrix component " + i);
        }
    }
}
