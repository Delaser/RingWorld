package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingPreviewRequestGateTest {
    @Test
    void advancingTheGateRejectsAClosingPreviewsOldWorker() {
        RingPreviewRequestGate<String> gate = new RingPreviewRequestGate<>();
        long previewRequest = gate.begin();
        long worldCreationRequest = gate.begin();

        gate.complete(previewRequest, "stale preview");

        assertNull(gate.poll());
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
