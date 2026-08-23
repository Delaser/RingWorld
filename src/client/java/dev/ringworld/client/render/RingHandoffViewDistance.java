package dev.ringworld.client.render;

import dev.ringworld.world.RingHandoffAvailability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Adapts mainline's visual profile to the chunks that 1.21.1 has actually
 * received around the current presentation chart.
 *
 * <p>The configured/effective render distance changes immediately, while the
 * integrated or remote server can take many seconds to fill that window. If
 * the shader uses the requested radius during that interval, its entire live
 * dither lies beyond the last chunk and the Atlas begins as a visible shelf.
 * Keep the mainline profile ratios unchanged, but derive their input from the
 * contiguous circumference radius and ease increases as new chunks arrive.</p>
 */
public final class RingHandoffViewDistance {
    private static ClientLevel observedLevel;
    private static long observedTick = Long.MIN_VALUE;
    private static double smoothedBlocks;

    private RingHandoffViewDistance() { }

    public static double blocks(Minecraft client, int effectiveChunks) {
        double requestedBlocks = effectiveChunks * 16.0;
        ClientLevel level = client.level;
        if (level == null || client.player == null) return requestedBlocks;

        long tick = level.getGameTime();
        if (level != observedLevel) {
            observedLevel = level;
            observedTick = Long.MIN_VALUE;
            smoothedBlocks = 0.0;
        }
        if (tick == observedTick) return smoothedBlocks;
        observedTick = tick;

        double cameraX = client.player.getX();
        int cameraChunkX = Mth.floor(cameraX) >> 4;
        int cameraChunkZ = Mth.floor(client.player.getZ()) >> 4;
        int positive = contiguousChunks(level, cameraChunkX, cameraChunkZ, 1,
                effectiveChunks);
        int negative = contiguousChunks(level, cameraChunkX, cameraChunkZ, -1,
                effectiveChunks);

        // Mainline's live fade ends at 102% of the profile input. Place that
        // endpoint just inside the measured chunk edge instead of beyond it.
        double target = RingHandoffAvailability.targetProfileBlocks(
                effectiveChunks, cameraX, cameraChunkX, positive, negative);
        smoothedBlocks = RingHandoffAvailability.smooth(smoothedBlocks, target);
        return smoothedBlocks;
    }

    private static int contiguousChunks(ClientLevel level, int cameraChunkX,
                                        int cameraChunkZ, int stepX, int limit) {
        int loaded = 0;
        while (loaded < limit && level.getChunkSource().hasChunk(
                cameraChunkX + stepX * (loaded + 1), cameraChunkZ)) {
            loaded++;
        }
        return loaded;
    }
}
