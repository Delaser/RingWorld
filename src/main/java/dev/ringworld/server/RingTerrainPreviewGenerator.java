package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingTerrainPreviewSampler;
import net.minecraft.server.level.ServerLevel;

/** Builds a fast map-like preview from the real periodic biome sampler without creating chunks. */
final class RingTerrainPreviewGenerator {
    private RingTerrainPreviewGenerator() { }

    static RingTerrainPreview generate(ServerLevel world, long worldHash, RingGeometry geometry,
                                       RingTerrainPreviewStage stage) {
        long started = System.nanoTime();
        RingTerrainPreview preview = RingTerrainPreviewSampler.generate(
                worldHash, geometry, stage,
                world.getChunkSource().getGenerator(), world.getChunkSource().randomState(), world);
        if (preview == null) return null;
        RingWorldMod.LOGGER.info(
                "Generated {} RingWorld terrain preview: {}x{} colour / {}x{} terrain in {} ms",
                stage.logLabel(), preview.columns(), preview.rows(),
                Math.min(preview.columns(), stage.terrainColumns()),
                Math.min(preview.rows(), stage.terrainRows()),
                Math.round((System.nanoTime() - started) / 1_000_000.0));
        return preview;
    }
}
