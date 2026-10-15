package dev.ringworld.world;

import java.time.Instant;

/** A bounded clock correlation for joining independent process measurements. */
public record RingClockAnchor(long nanoTime, long epochSecond, int nanoAdjustment, long uncertaintyNanos) {
    public static RingClockAnchor capture() {
        RingClockAnchor best = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            long before = System.nanoTime();
            Instant wall = Instant.now();
            long after = System.nanoTime();
            long uncertainty = (after - before + 1) / 2;
            if (best == null || uncertainty < best.uncertaintyNanos)
                best = new RingClockAnchor(before + (after - before) / 2,
                        wall.getEpochSecond(), wall.getNano(), uncertainty);
        }
        return best;
    }
}
