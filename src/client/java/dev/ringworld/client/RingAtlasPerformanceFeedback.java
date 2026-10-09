package dev.ringworld.client;

import dev.ringworld.server.RingAtlasPregenerationService;
import net.minecraft.client.Minecraft;

/** Owner-local FPS mailbox for integrated generation. Dedicated servers use tick pressure. */
public final class RingAtlasPerformanceFeedback {
    private static Object lastServer;
    private static long lastReport;
    private RingAtlasPerformanceFeedback() { }

    public static void tick(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server == null || client.level == null || client.isPaused() || !client.isWindowActive()) {
            lastServer = null;
            return;
        }
        long now = System.nanoTime();
        if (server == lastServer && now - lastReport < 1_000_000_000L) return;
        lastServer = server;
        lastReport = now;
        RingAtlasPregenerationService.reportLocalFrameRate(server, client.getFps(),
                client.options.framerateLimit().get());
    }
}
