package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingTerrainPreviewHudTest {
    @Test
    void reportsEachStageBeforeAndDuringReplacement() {
        assertStates(RingTerrainPreviewHud.entries(-1),
                RingTerrainPreviewHud.State.GENERATING,
                RingTerrainPreviewHud.State.WAITING,
                RingTerrainPreviewHud.State.WAITING,
                RingTerrainPreviewHud.State.WAITING);

        assertStates(RingTerrainPreviewHud.entries(0),
                RingTerrainPreviewHud.State.ACTIVE,
                RingTerrainPreviewHud.State.GENERATING,
                RingTerrainPreviewHud.State.WAITING,
                RingTerrainPreviewHud.State.WAITING);

        assertStates(RingTerrainPreviewHud.entries(1),
                RingTerrainPreviewHud.State.READY,
                RingTerrainPreviewHud.State.ACTIVE,
                RingTerrainPreviewHud.State.GENERATING,
                RingTerrainPreviewHud.State.WAITING);

        assertStates(RingTerrainPreviewHud.entries(2),
                RingTerrainPreviewHud.State.READY,
                RingTerrainPreviewHud.State.READY,
                RingTerrainPreviewHud.State.ACTIVE,
                RingTerrainPreviewHud.State.GENERATING);

        assertStates(RingTerrainPreviewHud.entries(3),
                RingTerrainPreviewHud.State.READY,
                RingTerrainPreviewHud.State.READY,
                RingTerrainPreviewHud.State.READY,
                RingTerrainPreviewHud.State.ACTIVE);
    }

    @Test
    void labelsExposeTextureResolution() {
        assertEquals("Preview Very high 2048x32: waiting",
                RingTerrainPreviewHud.entries(-1).get(2).label());
    }

    private static void assertStates(List<RingTerrainPreviewHud.Entry> entries,
                                     RingTerrainPreviewHud.State... expected) {
        assertEquals(List.of(expected), entries.stream().map(RingTerrainPreviewHud.Entry::state).toList());
    }
}
