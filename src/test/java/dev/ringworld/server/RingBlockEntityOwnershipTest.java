package dev.ringworld.server;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingBlockEntityOwnershipTest {
    @Test
    void preservesExactLiveOrPendingAliasesWithoutMovingCanonicalEntries() {
        BlockPos canonical = new BlockPos(0, 120, -7);
        BlockPos alias = new BlockPos(16_384, 120, -7);

        assertEquals(alias, RingBlockEntityOwnership.saveOrRemovalPosition(
                alias, canonical, true, false));
        assertEquals(alias, RingBlockEntityOwnership.saveOrRemovalPosition(
                alias, canonical, false, true));
        assertEquals(alias, RingBlockEntityOwnership.saveOrRemovalPosition(
                alias, canonical, true, true));
        assertEquals(canonical, RingBlockEntityOwnership.saveOrRemovalPosition(
                alias, canonical, false, false));
        assertEquals(canonical, RingBlockEntityOwnership.saveOrRemovalPosition(
                canonical, canonical, true, true));
    }
}
