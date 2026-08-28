package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingBlockEntityLoadContextTest {
    @Test
    void exposesGeometryOnlyInsideTheActiveCallback() {
        RingGeometry geometry = new RingGeometry(416, 2_048);

        assertNull(RingBlockEntityLoadContext.activeGeometryOrNull());
        RingBlockEntityLoadContext.withGeometry(geometry, () ->
                assertSame(geometry, RingBlockEntityLoadContext.activeGeometryOrNull()));
        assertNull(RingBlockEntityLoadContext.activeGeometryOrNull());
    }

    @Test
    void nestedCallbacksRestoreTheOuterGeometry() {
        RingGeometry outer = new RingGeometry(416, 2_048);
        RingGeometry inner = new RingGeometry(256, 16_384);

        RingBlockEntityLoadContext.withGeometry(outer, () -> {
            assertSame(outer, RingBlockEntityLoadContext.activeGeometryOrNull());
            RingBlockEntityLoadContext.withGeometry(inner, () ->
                    assertSame(inner, RingBlockEntityLoadContext.activeGeometryOrNull()));
            assertSame(outer, RingBlockEntityLoadContext.activeGeometryOrNull());
        });
        assertNull(RingBlockEntityLoadContext.activeGeometryOrNull());
    }

    @Test
    void exceptionalExitClearsAndRestoresContexts() {
        RingGeometry outer = new RingGeometry(416, 2_048);
        RingGeometry inner = new RingGeometry(256, 16_384);

        RingBlockEntityLoadContext.withGeometry(outer, () -> {
            assertThrows(IllegalStateException.class, () ->
                    RingBlockEntityLoadContext.withGeometry(inner, () -> {
                        assertSame(inner, RingBlockEntityLoadContext.activeGeometryOrNull());
                        throw new IllegalStateException("fixture");
                    }));
            assertSame(outer, RingBlockEntityLoadContext.activeGeometryOrNull());
        });

        assertThrows(IllegalStateException.class, () ->
                RingBlockEntityLoadContext.withGeometry(outer, () -> {
                    throw new IllegalStateException("fixture");
                }));
        assertNull(RingBlockEntityLoadContext.activeGeometryOrNull());
    }

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
