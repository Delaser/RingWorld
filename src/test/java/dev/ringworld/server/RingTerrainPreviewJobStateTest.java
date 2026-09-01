package dev.ringworld.server;

import dev.ringworld.world.RingTerrainPreviewStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingTerrainPreviewJobStateTest {
    @Test
    void rejectsWrongWorldLateStagesAndPublicationAfterCancellation() {
        long worldHash = 0x1234L;
        RingTerrainPreviewJobState state = new RingTerrainPreviewJobState(worldHash);

        assertFalse(state.publish(worldHash + 1, RingTerrainPreviewStage.CURRENT,
                new byte[] {9}));
        assertTrue(state.publish(worldHash, RingTerrainPreviewStage.CURRENT,
                new byte[] {1}));
        assertTrue(state.publish(worldHash, RingTerrainPreviewStage.VERY_HIGH,
                new byte[] {2}));
        assertFalse(state.publish(worldHash, RingTerrainPreviewStage.HIGH,
                new byte[] {3}));
        assertEquals(RingTerrainPreviewStage.VERY_HIGH, state.latest().stage());
        assertArrayEquals(new byte[] {2}, state.latest().data());

        state.cancel();
        assertTrue(state.cancelled());
        assertFalse(state.publish(worldHash, RingTerrainPreviewStage.ULTRA,
                new byte[] {4}));
    }

    @Test
    void snapshotsCannotMutateRetainedPayloadBytes() {
        RingTerrainPreviewJobState state = new RingTerrainPreviewJobState(1L);
        byte[] source = {1, 2};
        assertTrue(state.publish(1L, RingTerrainPreviewStage.CURRENT, source));
        source[0] = 9;
        byte[] snapshot = state.latest().data();
        snapshot[1] = 9;

        assertArrayEquals(new byte[] {1, 2}, state.latest().data());
    }
}
