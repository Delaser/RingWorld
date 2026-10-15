package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import net.minecraft.server.level.ServerLevel;
import java.util.IdentityHashMap;
import java.util.Map;

/** Opt-in headless runtime regression using synthetic local FPS, never production telemetry. */
final class RingAtlasAutoScaleProbe {
    private static final Map<ServerLevel, RingAtlasAutoScaleProbe> RUNS = new IdentityHashMap<>();
    private int stage;
    private int ticks;
    private long started = System.nanoTime();

    static void tick(ServerLevel world) {
        if (!Boolean.getBoolean("ringworld.testAtlasAutoScale")) return;
        RUNS.computeIfAbsent(world, ignored -> new RingAtlasAutoScaleProbe()).advance(world);
    }
    static boolean running(ServerLevel world) {
        var probe = RUNS.get(world);
        return Boolean.getBoolean("ringworld.testAtlasAutoScale") && (probe == null || probe.stage != 5);
    }
    static void clear(ServerLevel world) { RUNS.remove(world); }

    private void advance(ServerLevel world) {
        if (stage == 5) return;
        if (++ticks > 2400 || System.nanoTime() - started > 120_000_000_000L)
            throw new IllegalStateException("Atlas auto-scaling fixture watchdog, stage=" + stage);
        int limit = RingAtlasPregenerationService.maxInFlightChunks(world);
        require(RingAtlasCaptureBudget.allows(limit - 1, limit, 0), "capture target does not follow auto requests");
        require(!RingAtlasCaptureBudget.allows(limit, limit, 0), "capture exceeded auto target");
        if (stage == 0) {
            require(limit == 4, "auto did not start at four");
            stage = 1;
        }
        // Exercise the same mailbox as the integrated owner, without a game window.
        RingAtlasPregenerationService.reportLocalFrameRate(world.getServer(), stage < 3 ? 40 : 60, 60);
        if (stage == 1 && limit == 2) stage = 2;
        else if (stage == 2 && limit == 1) stage = 3;
        else if (stage == 3 && limit == 2) stage = 4;
        else if (stage == 4 && limit == 4) {
            stage = 5;
            RingWorldMod.LOGGER.info("[atlas-auto-test] PASS FPS pressure 4->2->1 and recovery 1->2->4");
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
