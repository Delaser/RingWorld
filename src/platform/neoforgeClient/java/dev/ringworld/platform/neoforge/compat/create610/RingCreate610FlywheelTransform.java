package dev.ringworld.platform.neoforge.compat.create610;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Create-free matrix composition used by the exact Flywheel adapter. */
public final class RingCreate610FlywheelTransform {
    private RingCreate610FlywheelTransform() { }

    public static void applyCurvedEmbedding(
            PoseStack matrices, Vec3 renderOriginRelativeAnchor,
            Vec3 cameraPosition, Vec3i renderOrigin, RingGeometry geometry) {
        Vec3 anchor = renderOriginRelativeAnchor.add(
                renderOrigin.getX(), renderOrigin.getY(), renderOrigin.getZ());
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, cameraPosition, anchor.x - cameraPosition.x,
                anchor.y - cameraPosition.y, anchor.z - cameraPosition.z);
        Vec3 renderLocal = transform.cameraLocalPosition().add(
                cameraPosition.x - renderOrigin.getX(),
                cameraPosition.y - renderOrigin.getY(),
                cameraPosition.z - renderOrigin.getZ());
        matrices.translate(renderLocal.x, renderLocal.y, renderLocal.z);
        matrices.mulPose(Axis.ZP.rotation((float) transform.tangentAngleRadians()));
    }

    /**
     * Sets the child-environment delta that moves a native Flywheel block-origin
     * transform from flat render-origin-local space into RingWorld's tangent frame.
     */
    public static boolean setCurvedBlockEmbedding(
            Matrix4f pose, Matrix3f normal, BlockPos anchor,
            Vec3 cameraPosition, Vec3i renderOrigin, RingGeometry geometry) {
        if (pose == null || normal == null || anchor == null || cameraPosition == null
                || renderOrigin == null || geometry == null
                || !finite(cameraPosition.x) || !finite(cameraPosition.y)
                || !finite(cameraPosition.z)) {
            identity(pose, normal);
            return false;
        }

        double flatX = (double) anchor.getX() - renderOrigin.getX();
        double flatY = (double) anchor.getY() - renderOrigin.getY();
        double flatZ = (double) anchor.getZ() - renderOrigin.getZ();
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, cameraPosition,
                anchor.getX() - cameraPosition.x,
                anchor.getY() - cameraPosition.y,
                anchor.getZ() - cameraPosition.z);
        Vec3 curved = transform.cameraLocalPosition().add(
                cameraPosition.x - renderOrigin.getX(),
                cameraPosition.y - renderOrigin.getY(),
                cameraPosition.z - renderOrigin.getZ());
        double angle = transform.tangentAngleRadians();
        if (!finite(flatX) || !finite(flatY) || !finite(flatZ)
                || !finite(curved.x) || !finite(curved.y) || !finite(curved.z)
                || !finite(angle)) {
            identity(pose, normal);
            return false;
        }

        pose.identity()
                .translate((float) curved.x, (float) curved.y, (float) curved.z)
                .rotateZ((float) angle)
                .translate((float) -flatX, (float) -flatY, (float) -flatZ);
        normal.identity().rotateZ((float) angle);
        if (!finite(pose) || !finite(normal)) {
            identity(pose, normal);
            return false;
        }
        return true;
    }

    private static void identity(Matrix4f pose, Matrix3f normal) {
        if (pose != null) pose.identity();
        if (normal != null) normal.identity();
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static boolean finite(Matrix4f matrix) {
        float[] values = matrix.get(new float[16]);
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }

    private static boolean finite(Matrix3f matrix) {
        float[] values = matrix.get(new float[9]);
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
}
