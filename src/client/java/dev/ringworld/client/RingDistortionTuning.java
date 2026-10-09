package dev.ringworld.client;

import dev.ringworld.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Session-local nearby normalisation; default off, with no resource reload. */
public final class RingDistortionTuning {
    private static boolean enabled;
    private static int chunks = 3;
    private RingDistortionTuning() { }
    public static boolean enabled() { return enabled; }
    public static int chunks() { return chunks; }
    public static String select(boolean value) { enabled = value; return summary(); }
    public static String distance(int value) {
        if (value < 1 || value > 8) throw new IllegalArgumentException("distance must be 1–8 chunks");
        chunks = value; return summary();
    }
    public static void clearSession() { enabled = false; chunks = 3; }
    public static String summary() {
        return "Nearby block normalisation: " + (enabled ? "on" : "off")
                + "; correction " + chunks + " chunks in each direction (this client only)";
    }
    public static RingNearbyProjection projection(RingGeometry geometry, Vec3 camera) {
        Minecraft client = Minecraft.getInstance();
        double top = client.level == null ? 320.0 : client.level.getMinY() + client.level.getHeight();
        return RingNearbyProjection.create(geometry, camera.y, top, chunks);
    }
    public static Vec3 centerDirection(RingGeometry geometry, Vec3 camera) {
        return enabled ? new Vec3(0, projection(geometry, camera).cameraRadius(), -camera.z).normalize()
                : geometry.directionToRingCenter(camera);
    }
    public static RingObjectTransform object(RingGeometry geometry, Vec3 camera, double x, double y, double z) {
        return enabled ? projection(geometry, camera).object(camera, x, y, z)
                : RingObjectTransform.fromCameraRelative(geometry, camera, x, y, z);
    }
    public static AABB bounds(RingGeometry geometry, AABB box, Vec3 camera) {
        return enabled ? projection(geometry, camera).bounds(box, camera)
                : geometry.toCameraLocalBounds(box, camera);
    }
}
