package dev.ringworld.world;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void labelsExposeTextureResolutionAndRejectInvalidSessionStage() {
        assertEquals("Very high 2048x32: waiting",
                RingTerrainPreviewHud.entries(-1).get(2).label());
        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainPreviewHud.entries(-2));
        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainPreviewHud.entries(4));
    }

    @Test
    void compactGridStaysInsideNarrowAndWideScreens() {
        for (int width : new int[] {320, 854, 1920}) {
            RingTerrainPreviewHud.Grid grid = RingTerrainPreviewHud.grid(width);
            assertTrue(grid.leftX() >= 0);
            assertTrue(grid.rightX() + grid.columnWidth() <= width);
            assertTrue(grid.columnWidth() <= 150);
            assertEquals(grid.leftX(), grid.xFor(0));
            assertEquals(grid.rightX(), grid.xFor(1));
            assertEquals(grid.leftX(), grid.xFor(2));
            assertEquals(grid.rightX(), grid.xFor(3));
        }
        assertThrows(IllegalArgumentException.class, () -> RingTerrainPreviewHud.grid(20));
        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainPreviewHud.grid(320).xFor(4));
    }

    private static void assertStates(List<RingTerrainPreviewHud.Entry> entries,
                                     RingTerrainPreviewHud.State... expected) {
        assertEquals(List.of(expected),
                entries.stream().map(RingTerrainPreviewHud.Entry::state).toList());
    }
}
