package dev.ringworld.experiment;

import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;

import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.Set;

public final class RingCompareFixture {
    public static final boolean ENABLED = Boolean.getBoolean("patch.ringCompare");
    private static int state, ticks, n;
    private static net.minecraft.world.phys.Vec3 shore;

    private static net.minecraft.world.phys.Vec3 shoreCamera() {
        if (shore != null) return shore;
        var atlas = dev.ringworld.client.ClientRingState.terrainAtlas();
        // Locate one repeatable west-facing stretch of water along the middle of this saved ring.
        for (int dx = 12; dx < atlas.geometry().circumferenceBlocks(); dx += 2) {
            double x = atlas.geometry().wrapX(-dx);
            var sample = atlas.sample(x, 0);
            if (sample.waterCoverage() > .95 && atlas.sample(x - 16, 0).waterCoverage() > .95) {
                shore = new net.minecraft.world.phys.Vec3(x, sample.height() + 7, .5);
                System.out.println("SHORE_CAMERA feet=" + shore);
                return shore;
            }
        }
        throw new IllegalStateException("The comparison save has no water transect");
    }

    private static boolean measuring, pending;
    private static long last;
    private static final long[] frameTimes = new long[180];

    public static void frame() {
        if (!measuring) return;
        long now = System.nanoTime();
        if (last != 0 && n < frameTimes.length) frameTimes[n++] = now - last;
        last = now;
    }

    public static boolean hold(Minecraft mc) {
        if (!ENABLED) return false;
        if (!FullRingScene.ENABLED) AtlasPatch.visible = false;
        if (state >= (Boolean.getBoolean("patch.shore") ? 4 : 3)) {
            if (++ticks == 30) {
                System.out.println("FULL_SCENE_DONE");
                mc.stop();
            }
            return true;
        }
        var water = state == 3 ? shoreCamera() : null;
        double x = water == null ? 2 : water.x, y = water == null ? 172 : water.y;
        double z =
                switch (state) {
                    case 0 -> .5;
                    case 1 -> 585;
                    case 2 -> 885;
                    default -> water.z;
                };
        float yaw = state == 0 || state == 3 ? 90 : 180,
                pitch =
                        switch (state) {
                            case 0 -> -18;
                            case 1 -> -23;
                            case 2 -> -14;
                            default -> 15;
                        };
        // A client crossing the seam can use the adjacent chart image of the requested X.
        boolean arrived =
                Math.abs(mc.player.getZ() - z) <= 2
                        && (water == null
                                || Math.abs(
                                                dev.ringworld.client.ClientRingState.geometry()
                                                        .shortestCircumferenceDelta(
                                                                mc.player.getX(), x))
                                        <= 2);
        if (!pending && !arrived) {
            pending = true;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(
                    () -> {
                        var pl = server.getPlayerList().getPlayer(uuid);
                        pl.teleportTo(server.overworld(), x, y, z, Set.of(), yaw, pitch, false);
                    });
            return true;
        }
        if (!arrived) return true;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        if (!RingSurfaceTextureRenderer.captureReady() || ++ticks < 75) return true;
        measuring = true;
        if (n < frameTimes.length) return true;
        measuring = false;
        long sum = 0;
        for (long v : frameTimes) sum += v;
        Arrays.sort(frameTimes);
        String scene =
                switch (state) {
                    case 0 -> "interior";
                    case 1 -> "exterior";
                    case 2 -> "whole";
                    default -> "shore";
                };
        System.out.printf(
                java.util.Locale.ROOT,
                "FULL_RING_TIME pose=%s frames=%d mean=%.3f p95=%.3f eye=%s%n",
                scene,
                n,
                sum / 1e6 / n,
                frameTimes[(int) (n * .95)] / 1e6,
                RingMinecraftClientAccess.camera(mc).position());
        RingMinecraftClientAccess.grabScreenshot(
                mc.gameDirectory,
                "ring-" + scene + ".png",
                RingMinecraftClientAccess.mainRenderTarget(mc),
                1,
                msg -> System.out.println("FULL_RING_SCREEN " + msg.getString()));
        state++;
        n = ticks = 0;
        last = 0;
        pending = false;
        return true;
    }
}
