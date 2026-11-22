package dev.ringworld.client.render;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingDistortionTuning;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import java.lang.ref.WeakReference;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/** Extracted with the sky state. Only the executing sky pass installs it on the render thread. */
public record RingSkyView(WeakReference<ClientLevel> levelRef, RingGeometry geometry, Vec3 eye, FogType fluid,
                          RingSkyProfile profile, RingSkyCycle.SunVisual sun, float starTilt, boolean primary) {
    public interface Holder {
        RingSkyView ringworld$view();
        void ringworld$view(RingSkyView view);
    }

    private static RingSkyView active;
    private static volatile WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);

    // A retained render state must not keep a disconnected world and its chunks alive.
    public ClientLevel world() { return levelRef.get(); }

    public static RingSkyView capture(ClientLevel world, Camera camera, float tickProgress) {
        RingGeometry geometry = ClientRingState.geometry(world);
        if (geometry == null) return null;
        Vec3 eye = camera.position();
        Vec3 direction = RingDistortionTuning.centerDirection(geometry, eye);
        Minecraft client = Minecraft.getInstance();
        WeakReference<ClientLevel> level = lastLevel;
        if (level.get() != world) {
            level = new WeakReference<>(world);
            lastLevel = level;
        }
        return new RingSkyView(level, geometry, eye, camera.getFluidInCamera(),
                ClientRingState.skyProfile(), RingSkyCycle.sunVisual(world.getOverworldClockTime() + tickProgress),
                (float)Math.atan2(direction.z, direction.y),
                world == client.level && camera == RingMinecraftClientAccess.camera(client));
    }

    public static RingSkyView current() { return active; }

    public static Runnable bind(RingSkyView view, Runnable draw) {
        return () -> {
            RingSkyView previous = active;
            active = view;
            try {
                draw.run();
            } finally {
                active = previous;
            }
        };
    }
}
