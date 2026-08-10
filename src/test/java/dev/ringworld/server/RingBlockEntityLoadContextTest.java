package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingBlockEntityLoadContextTest {
    @Test
    void restoresOnlyPeriodicAliasesOwnedByTheCanonicalChunk() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        assertEquals(new BlockPos(16_388, 120, 4), restore(
                geometry, 0, 16_388, 120, 4, new BlockPos(4, 120, 4)));
        assertEquals(new BlockPos(-1, 120, 4), restore(
                geometry, 0, -1, 120, 4, new BlockPos(16_383, 120, 4)));
        assertEquals(new BlockPos(4, 120, 4), restore(
                geometry, 0, 4, 120, 4, new BlockPos(4, 120, 4)));
        assertEquals(new BlockPos(4, 120, 20), restore(
                geometry, 0, 16_388, 120, 20, new BlockPos(4, 120, 20)));
    }

    private static BlockPos restore(RingGeometry geometry, int ownerChunkZ,
                                    int x, int y, int z, BlockPos vanilla) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return RingBlockEntityLoadContext.restoreSavedAlias(geometry, ownerChunkZ, tag, vanilla);
    }
}
