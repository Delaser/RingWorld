package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.mixin.BlockEntityPositionAccessor;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

/** Lossless reconciliation after all directly loaded block entities are known. */
public final class RingBlockEntityOwnership {
    private RingBlockEntityOwnership() { }

    /**
     * Keeps an exact legacy alias addressable while either its live owner or
     * its still-packed NBT exists. Canonicalizing a pending alias during save
     * would overwrite or duplicate the canonical payload before reconciliation.
     */
    public static BlockPos saveOrRemovalPosition(BlockPos requested, BlockPos canonical,
                                                 boolean exactLive, boolean exactPending) {
        return !canonical.equals(requested) && (exactLive || exactPending)
                ? requested
                : canonical;
    }

    public static void reconcileLoadedAliases(LevelChunk chunk, RingGeometry geometry) {
        for (BlockPos alias : List.copyOf(chunk.getBlockEntities().keySet())) {
            int canonicalX = geometry.wrapBlockX(alias.getX());
            if (canonicalX == alias.getX()) continue;
            BlockPos canonical = new BlockPos(canonicalX, alias.getY(), alias.getZ());
            BlockEntity aliasEntity = chunk.getBlockEntities().get(alias);
            if (aliasEntity == null) continue;
            BlockEntity canonicalOwner = chunk.getBlockEntities().get(canonical);
            boolean canonicalPending = chunk.getBlockEntityNbt(canonical) != null;
            if (canonicalOwner != null || canonicalPending) {
                RingWorldMod.LOGGER.warn(
                        "Preserving conflicting saved alias block entity {} at {} because canonical {} "
                                + "also has saved data; back up the world and recover both inventories manually",
                        aliasEntity.getType(), alias, canonical);
                continue;
            }
            chunk.removeBlockEntity(alias);
            chunk.getBlockEntities().remove(alias, aliasEntity);
            ((BlockEntityPositionAccessor) aliasEntity).ringworld$setWorldPosition(canonical);
            chunk.addAndRegisterBlockEntity(aliasEntity);
            RingWorldMod.LOGGER.info("Repaired lone saved alias block entity {} from {} to canonical {}",
                    aliasEntity.getType(), alias, canonical);
        }
    }
}
