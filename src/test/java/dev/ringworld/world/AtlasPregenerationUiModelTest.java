package dev.ringworld.world;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure coverage for status invariants, UI actions, and permission policy. */
class AtlasPregenerationUiModelTest {
    @Test
    void stateAwareActionsAndDurableChunkCountArePresented() {
        AtlasPregenerationStatus running = status(AtlasPregenerationState.RUNNING, 7, true);
        AtlasPregenerationView view = AtlasPregenerationView.from(running);

        assertEquals("7 / 16 canonical chunks", view.chunks());
        assertTrue(view.actions().contains(AtlasPregenerationAction.PAUSE));
        assertTrue(view.actions().contains(AtlasPregenerationAction.CANCEL));
        assertFalse(view.actions().contains(AtlasPregenerationAction.START));

        AtlasPregenerationView complete = AtlasPregenerationView.from(status(AtlasPregenerationState.COMPLETE, 16, true));
        assertTrue(complete.actions().isEmpty());
        AtlasPregenerationView readonly = AtlasPregenerationView.from(status(AtlasPregenerationState.IDLE, 0, false));
        assertTrue(readonly.actions().isEmpty());
    }

    @Test
    void statusRejectsMismatchedTotalsAndPermissionPolicyIsExplicit() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationStatus(1L, 1024, 256,
                5, 8, 16, 3, new AtlasPregenerationProgress(AtlasPregenerationState.RUNNING,
                1, 15, 4, 64, 1.0, Duration.ofSeconds(1), Optional.empty(), Optional.empty()), true, Optional.empty()));
        assertTrue(AtlasPregenerationAccess.canControl(true, false));
        assertTrue(AtlasPregenerationAccess.canControl(false, true));
        assertFalse(AtlasPregenerationAccess.canControl(false, false));
    }

    @Test
    void wireValuesAreStableAndIndependentOfEnumOrdering() {
        assertEquals(1, AtlasPregenerationAction.START.wireValue());
        assertEquals(4, AtlasPregenerationAction.CANCEL.wireValue());
        assertEquals(5, AtlasPregenerationState.COMPLETE.wireValue());
        assertEquals(AtlasPregenerationAction.RESUME, AtlasPregenerationAction.fromWireValue(3));
        assertEquals(AtlasPregenerationState.PAUSED, AtlasPregenerationState.fromWireValue(3));
    }

    private static AtlasPregenerationStatus status(AtlasPregenerationState state, long completedChunks,
                                                    boolean canControl) {
        return new AtlasPregenerationStatus(0xCAFE, 1024, 256, 5, 8, 16, completedChunks,
                new AtlasPregenerationProgress(state, 7, 16, 32, 64, 3.5,
                        Duration.ofSeconds(9), Optional.of(Duration.ofSeconds(9)), Optional.empty()),
                canControl, Optional.empty());
    }
}
