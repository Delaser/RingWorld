package dev.ringworld.client.render;

import dev.ringworld.client.RingDistortionTuning;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNearbyProjection;
import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable view shared by renderers and their asynchronous visibility workers. */
public record RingRenderView(RingGeometry geometry, Vec3 camera,
                             RingNearbyProjection nearbyCorrection) {
    public RingRenderView {
        Objects.requireNonNull(geometry);
        Objects.requireNonNull(camera);
    }

    /** Capture on the client thread; subsequent queries never read live client state. */
    public static RingRenderView capture(RingGeometry geometry, Vec3 camera) {
        return new RingRenderView(geometry, camera, RingDistortionTuning.enabled()
                ? RingDistortionTuning.projection(geometry, camera) : null);
    }

    public Vec3 position(Vec3 canonical) {
        return nearbyCorrection == null ? geometry.toCameraLocal(canonical, camera)
                : nearbyCorrection.position(canonical, camera);
    }

    public AABB bounds(AABB canonical) {
        return nearbyCorrection == null ? geometry.toCameraLocalBounds(canonical, camera)
                : nearbyCorrection.bounds(canonical, camera);
    }
}
