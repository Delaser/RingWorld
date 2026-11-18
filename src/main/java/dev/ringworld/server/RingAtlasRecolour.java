package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Optional maintenance pass. Only the owner tick applies colours or advances the cursor. */
final class RingAtlasRecolour {
    private final RingGeometry geometry;
    private final LevelHeightAccessor height;
    private final PalettedContainerFactory palettes;
    private final int total;
    private int scanned, changed, skipped, errors;
    private CompletableFuture<ProtoChunk> pending;
    private ChunkPos pendingPos;
    private boolean loadedSinceRead;

    RingAtlasRecolour(ServerLevel world, RingGeometry geometry) {
        this.geometry = geometry;
        this.total = Math.multiplyExact(geometry.circumferenceChunks(), geometry.widthChunks());
        this.height = LevelHeightAccessor.create(world.getMinY(), world.getHeight());
        this.palettes = world.palettedContainerFactory();
    }

    boolean complete() { return scanned == total; }

    String status() {
        return "recolour " + (complete() ? "complete" : "running") + ", " + scanned + "/" + total
                + " chunks checked, " + changed + " changed, " + skipped + " skipped, " + errors + " failed";
    }

    void onLoad(ChunkPos pos) {
        if (pos.equals(pendingPos)) loadedSinceRead = true;
    }

    void tick(ServerLevel world, Predicate<ChunkAccess> apply) {
        if (complete() || (pending != null && !pending.isDone())) return;
        ChunkPos pos = new ChunkPos(scanned % geometry.circumferenceChunks(),
                geometry.minChunkZ() + scanned / geometry.circumferenceChunks());
        try {
            ChunkAccess chunk = world.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null && pending == null) {
                pendingPos = pos;
                loadedSinceRead = false;
                pending = world.getChunkSource().chunkMap.read(pos).thenApplyAsync(
                        tag -> tag.map(nbt -> decode(pos, nbt)).orElse(null));
                return;
            }
            // A live chunk wins. Never replay an older disk snapshot over a visit or edit.
            if (chunk == null && !loadedSinceRead) chunk = pending.join();
            if (chunk == null) skipped++;
            else if (apply.test(chunk)) changed++;
        } catch (RuntimeException ex) {
            if (errors++ == 0) RingWorldMod.LOGGER.warn("Could not recolour Atlas chunk {}", pos, ex);
        }
        scanned++;
        pending = null;
        pendingPos = null;
        if (complete()) RingWorldMod.LOGGER.info("RingWorld Atlas {}", status());
    }

    /** Detached sections only. No chunk tickets, level registration, POI, block entities or lighting. */
    private ProtoChunk decode(ChunkPos pos, CompoundTag nbt) {
        if (nbt.getIntOr("DataVersion", -1) != SharedConstants.getCurrentVersion().dataVersion().version()
                || nbt.getIntOr("xPos", Integer.MIN_VALUE) != pos.x()
                || nbt.getIntOr("zPos", Integer.MIN_VALUE) != pos.z()
                || SerializableChunkData.getChunkStatusFromTag(nbt) != ChunkStatus.FULL) return null;
        var data = SerializableChunkData.parse(height, palettes, nbt);
        if (data == null) return null;
        var heights = data.heightmaps().get(Heightmap.Types.WORLD_SURFACE);
        if (heights == null) return null;
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, palettes, null);
        for (var section : data.sectionData()) {
            int index = height.getSectionIndexFromSectionY(section.y());
            if (index >= 0 && index < height.getSectionsCount() && section.chunkSection() != null)
                chunk.getSections()[index] = section.chunkSection();
        }
        chunk.setHeightmap(Heightmap.Types.WORLD_SURFACE, heights);
        chunk.setPersistedStatus(ChunkStatus.FULL);
        return chunk;
    }
}
