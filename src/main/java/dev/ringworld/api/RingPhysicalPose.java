package dev.ringworld.api;

import dev.ringworld.world.RingGeometry;
import net.minecraft.world.phys.Vec3;

/**
 * Read-only physical-ring pose derived from an intrinsic Minecraft pose.
 * Basis vectors use physical ring-space axes and remain orthonormal.
 */
public record RingPhysicalPose(
        Vec3 position,
        Vec3 circumferenceTangent,
        Vec3 localUp,
        Vec3 widthDirection,
        Vec3 viewDirection) {

    public static RingPhysicalPose fromIntrinsic(
            RingGeometry geometry, Vec3 intrinsicPosition,
            float yawDegrees, float pitchDegrees) {
        double angle = geometry.angleAt(intrinsicPosition.x);
        Vec3 tangent = new Vec3(0.0, -Math.sin(angle), Math.cos(angle));
        Vec3 localUp = new Vec3(0.0, -Math.cos(angle), -Math.sin(angle));
        Vec3 width = new Vec3(1.0, 0.0, 0.0);

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        double intrinsicX = -Math.sin(yaw) * horizontal;
        double intrinsicY = -Math.sin(pitch);
        double intrinsicZ = Math.cos(yaw) * horizontal;
        Vec3 view = tangent.scale(intrinsicX)
                .add(localUp.scale(intrinsicY))
                .add(width.scale(intrinsicZ))
                .normalize();
        return new RingPhysicalPose(
                geometry.toPhysical(intrinsicPosition.x, intrinsicPosition.y, intrinsicPosition.z),
                tangent, localUp, width, view);
    }
}
