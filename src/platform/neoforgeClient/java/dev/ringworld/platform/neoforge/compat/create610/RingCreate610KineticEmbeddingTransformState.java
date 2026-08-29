package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Mutable matrices retained by one exact Flywheel child embedding identity. */
final class RingCreate610KineticEmbeddingTransformState {
    enum Result { IDENTITY, CURVED, MALFORMED }

    private final Matrix4f pose = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();
    private boolean curved;

    Result update(
            BlockPos anchor, Vec3 camera, Vec3i renderOrigin, RingGeometry geometry) {
        if (geometry == null) {
            identity();
            return Result.IDENTITY;
        }
        if (!RingCreate610FlywheelTransform.setCurvedBlockEmbedding(
                pose, normal, anchor, camera, renderOrigin, geometry)) {
            identity();
            return Result.MALFORMED;
        }
        curved = true;
        return Result.CURVED;
    }

    void identity() {
        pose.identity();
        normal.identity();
        curved = false;
    }

    Matrix4f pose() { return pose; }
    Matrix3f normal() { return normal; }
    boolean curved() { return curved; }
}
