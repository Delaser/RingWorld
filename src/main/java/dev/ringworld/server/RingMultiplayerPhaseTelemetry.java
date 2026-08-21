package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Compact, loader-neutral diagnostics for cold phases of the disposable
 * multiplayer fixture.
 *
 * <p>This deliberately observes only state already owned by the server tick.
 * It neither loads chunks nor changes entity, Atlas, or player scheduling.
 * The elapsed wall time makes a later watchdog sample comparable with the
 * immediately preceding fixture phase.</p>
 */
final class RingMultiplayerPhaseTelemetry {
    private long previousNanos = Long.MIN_VALUE;

    void record(String phase, ServerLevel world) {
        long now = System.nanoTime();
        long elapsedNanos = previousNanos == Long.MIN_VALUE ? 0L
                : Math.max(0L, now - previousNanos);
        previousNanos = now;

        int entities = 0;
        int items = 0;
        int fallingBlocks = 0;
        for (Entity entity : world.getAllEntities()) {
            entities++;
            if (entity instanceof ItemEntity) items++;
            if (entity instanceof FallingBlockEntity) fallingBlocks++;
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        RingWorldMod.LOGGER.info(
                "[multiplayer-cold] phase={} elapsedMs={} gameTime={} dimension={} entities={} "
                        + "items={} fallingBlocks={} players={} loadedChunks={} pendingChunkTasks={} "
                        + "blockTicks={} fluidTicks={} usedMiB={} committedMiB={} atlas={}",
                phase, elapsedNanos / 1_000_000.0, world.getGameTime(),
                world.dimension(), entities, items, fallingBlocks,
                world.players().size(), world.getChunkSource().getLoadedChunksCount(),
                world.getChunkSource().getPendingTasksCount(), world.getBlockTicks().count(),
                world.getFluidTicks().count(), usedMemory / (1_024L * 1_024L),
                runtime.totalMemory() / (1_024L * 1_024L),
                RingAtlasPregenerationService.status(world));
    }
}
