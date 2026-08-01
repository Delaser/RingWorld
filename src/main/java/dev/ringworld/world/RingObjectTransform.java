package dev.ringworld.world;

import net.minecraft.world.phys.Vec3;

/**
 * Shared rigid pose for objects whose vertices are submitted outside the
 * curved chunk-terrain shader.
 *
 * <p>The anchor is embedded exactly on the cylinder, then its ordinary model
 * vertices are rotated into the anchor's local tangent frame. This keeps
 * block entities, entities, overlays, and outlines seated on the authoritative
 * terrain without changing their gameplay coordinates.</p>
 */
public record RingObjectTransform(Vec3 cameraLocalPosition, double tangentAngleRadians) {
    public static RingObjectTransform fromCameraRelative(
            RingGeometry geometry, Vec3 cameraIntrinsicPosition,
            double relativeX, double relativeY, double relativeZ) {
        Vec3 anchor = cameraIntrinsicPosition.add(relativeX, relativeY, relativeZ);
        return new RingObjectTransform(
                geometry.toCameraLocal(anchor, cameraIntrinsicPosition),
                geometry.tangentFrameAngle(cameraIntrinsicPosition.x, anchor.x));
    }
}
