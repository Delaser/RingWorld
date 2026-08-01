package dev.ringworld.api;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Stable, read-only entry point for mods that want to be ring-world aware. */
public final class RingWorldApi {
    /** Increment only when an existing public method or coordinate meaning breaks. */
    public static final int API_VERSION = 1;

    private RingWorldApi() { }

    public static boolean isRingWorld(ServerLevel world) {
        return world.dimension() == ServerLevel.OVERWORLD;
    }

    @Nullable
    public static RingWorldSettings settings(ServerLevel world) {
        return isRingWorld(world) ? RingWorldSettings.get(world) : null;
    }

    public static RingGeometry geometry(ServerLevel world) {
        RingWorldSettings settings = settings(world);
        if (settings == null) {
            throw new IllegalArgumentException("World is not a RingWorld Overworld");
        }
        return settings.geometry();
    }

    /** Converts any intrinsic/presentation X into the one authoritative storage plane. */
    public static Vec3 canonicalPosition(ServerLevel world, Vec3 intrinsicPosition) {
        RingGeometry geometry = geometry(world);
        return new Vec3(geometry.wrapX(intrinsicPosition.x),
                intrinsicPosition.y, intrinsicPosition.z);
    }

    /** Selects the periodic image of a canonical position nearest one presentation X. */
    public static Vec3 nearestPresentationPosition(
            ServerLevel world, Vec3 canonicalPosition, double referencePresentationX) {
        RingGeometry geometry = geometry(world);
        return new Vec3(
                geometry.nearestImageX(canonicalPosition.x, referencePresentationX),
                canonicalPosition.y, canonicalPosition.z);
    }

    /** Embeds an intrinsic position in the global cylindrical render space. */
    public static Vec3 physicalPosition(ServerLevel world, Vec3 intrinsicPosition) {
        return geometry(world).toPhysical(
                intrinsicPosition.x, intrinsicPosition.y, intrinsicPosition.z);
    }

    /** Embeds position and vanilla yaw/pitch into one immutable physical-ring pose. */
    public static RingPhysicalPose physicalPose(
            ServerLevel world, Vec3 intrinsicPosition,
            float yawDegrees, float pitchDegrees) {
        return RingPhysicalPose.fromIntrinsic(
                geometry(world), intrinsicPosition, yawDegrees, pitchDegrees);
    }
}
