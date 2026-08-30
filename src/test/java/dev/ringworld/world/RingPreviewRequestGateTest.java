package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingPreviewRequestGateTest {
    @Test
    void closingPreviewThenImmediatelyStartingWorldCreationRejectsTheOldWorker() {
        RingPreviewRequestGate<String> gate = new RingPreviewRequestGate<>();
        long previewRequest = gate.begin();

        // Closing the preview advances the gate without waiting for its
        // cancelled worker. The real creation path can start immediately.
        long worldCreationRequest = gate.begin();
        gate.complete(previewRequest, "stale preview");

        assertNull(gate.poll(), "a cancelled preview must not publish after close");
        assertTrue(gate.isCurrent(worldCreationRequest));
        gate.complete(worldCreationRequest, "new world");
        assertEquals("new world", gate.poll());
    }

    @Test
    void lateOlderWorkerCannotReplaceTheNewestPreview() {
        RingPreviewRequestGate<String> gate = new RingPreviewRequestGate<>();
        long first = gate.begin();
        long second = gate.begin();

        gate.complete(second, "second preview");
        gate.complete(first, "first preview");

        assertEquals("second preview", gate.poll());
    }
}
