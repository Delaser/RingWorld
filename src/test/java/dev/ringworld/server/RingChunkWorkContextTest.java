package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingChunkWorkContextTest {
    @Test void scheduledSaveScopeDoesNotLeakIntoExplicitOrShutdownSaves() {
        assertFalse(RingChunkWorkContext.periodicSave());
        assertThrows(IllegalArgumentException.class, () -> RingChunkWorkContext.periodicSave(() -> {
            assertTrue(RingChunkWorkContext.periodicSave());
            assertTrue(RingChunkWorkContext.periodicSave(() -> RingChunkWorkContext.periodicSave()));
            throw new IllegalArgumentException();
        }));
        assertFalse(RingChunkWorkContext.periodicSave());
    }
}
