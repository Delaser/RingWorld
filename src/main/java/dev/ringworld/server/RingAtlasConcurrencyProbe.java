package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.AtlasPregenerationHandle;
import dev.ringworld.world.AtlasPregenerationOptions;
import dev.ringworld.world.AtlasPregenerationState;
import net.minecraft.server.level.ServerLevel;
import java.util.IdentityHashMap;
import java.util.Map;

/** Opt-in disposable headless fixture only; never enabled in normal play. */
final class RingAtlasConcurrencyProbe {
    private static final Map<ServerLevel, RingAtlasConcurrencyProbe> RUNS = new IdentityHashMap<>();
    private int stage;
    private int ticks;
    private int pausedAt;
    private int emptyAt;
    private int peak;

    static AtlasPregenerationHandle tick(ServerLevel world, AtlasPregenerationHandle handle) {
        if (!Boolean.getBoolean("ringworld.testAtlasConcurrency")) return handle;
        var probe = RUNS.computeIfAbsent(world, ignored -> new RingAtlasConcurrencyProbe());
        return probe.advance(world, handle);
    }
    static boolean running(ServerLevel world) {
        var probe = RUNS.get(world);
        return probe != null && probe.stage != 4;
    }
    static void clear(ServerLevel world) { RUNS.remove(world); }

    private AtlasPregenerationHandle advance(ServerLevel world, AtlasPregenerationHandle handle) {
        if (stage == 4) return handle;
        if (++ticks > 2400) throw new IllegalStateException("Atlas concurrency fixture watchdog, stage=" + stage);
        int inFlight = RingAtlasPregenerationService.inFlightChunks(world);
        peak = Math.max(peak, inFlight);
        if (stage == 0 && inFlight >= 2) {
            handle.pause();
            require(handle.progress().state() == AtlasPregenerationState.PAUSED, "pause did not apply");
            pausedAt = ticks;
            stage = 1;
        } else if (stage == 1) {
            require(handle.progress().state() == AtlasPregenerationState.PAUSED, "paused job changed state");
            if (inFlight == 0 && emptyAt == 0) emptyAt = ticks;
            if (emptyAt != 0) require(inFlight == 0, "paused job scheduled new requests");
            if (emptyAt != 0 && ticks - emptyAt >= 20) {
                require(ticks > pausedAt, "pause barrier was not observed");
                handle.resume();
                stage = 2;
            }
        } else if (stage == 2 && inFlight >= 2) {
            if (Boolean.getBoolean("ringworld.testAtlasRateCommand"))
                require(RingAtlasPregenerationService.setChunkGenerationRate(world, 2), "rate override failed");
            handle.cancel();
            stage = 3;
        } else if (stage == 3) {
            // Cancellation now waits for the captured checkpoint on the persistence worker.
            if (handle.progress().state() == AtlasPregenerationState.SAVING) {
                require(inFlight == 0, "cancelling job leaked loading tickets");
                return handle;
            }
            require(handle.progress().state() == AtlasPregenerationState.CANCELLED, "cancel did not apply");
            require(inFlight == 0, "cancel leaked loading tickets");
            require(handle.completion().toCompletableFuture().isCompletedExceptionally(), "cancel future did not terminate");
            handle = RingAtlasPregenerationService.pregenerate(world,
                    AtlasPregenerationOptions.headlessPrewarmDefaults(), progress -> {});
            if (Boolean.getBoolean("ringworld.testAtlasRateCommand")) {
                require(RingAtlasPregenerationService.maxInFlightChunks(world) == 2,
                        "replacement job lost session rate override");
                require(RingAtlasPregenerationService.chunkGenerationRate(world).startsWith("fixed"),
                        "replacement job lost fixed mode");
                require(RingAtlasPregenerationService.setChunkGenerationRate(world, 0), "auto restore failed");
                require(RingAtlasPregenerationService.maxInFlightChunks(world) == 4, "auto restore did not reset target");
                RingWorldMod.LOGGER.info("[atlas-rate-test] PASS session override across cancel/restart");
            }
            stage = 4;
            RingWorldMod.LOGGER.info("[atlas-concurrency-test] PASS pause/drain/resume/cancel/restart peakRequests={}", peak);
        }
        return handle;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
