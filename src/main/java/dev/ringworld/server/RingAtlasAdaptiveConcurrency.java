package dev.ringworld.server;

/** One-second observations with fast backoff and slow recovery. No Minecraft dependencies. */
final class RingAtlasAdaptiveConcurrency {
    static final int MAX_REQUESTS = 4;
    private int limit = MAX_REQUESTS;
    private int badSamples;
    private int healthySamples;
    private boolean sampled;
    private long lastSample;

    int limit() { return limit; }

    int observe(long now, double tickMs, double fps, double frameTarget) {
        if (sampled && now - lastSample < 1_000_000_000L) return limit;
        sampled = true;
        lastSample = now;
        boolean framesKnown = Double.isFinite(fps) && fps >= 0
                && Double.isFinite(frameTarget) && frameTarget > 0;
        boolean ticksKnown = Double.isFinite(tickMs) && tickMs >= 0;
        boolean severe = (ticksKnown && tickMs >= 100)
                || (framesKnown && fps < frameTarget * .4);
        boolean bad = severe || (ticksKnown && tickMs > 40)
                || (framesKnown && fps < frameTarget * .8);
        boolean healthy = ticksKnown && tickMs < 25
                && (!framesKnown || fps >= frameTarget * .95);
        if (bad) {
            healthySamples = 0;
            if (severe || ++badSamples >= 2) {
                limit = severe ? 1 : Math.max(1, limit / 2);
                badSamples = 0;
            }
        } else {
            badSamples = 0;
            if (healthy && ++healthySamples >= 10) {
                limit = Math.min(MAX_REQUESTS, limit * 2);
                healthySamples = 0;
            } else if (!healthy) healthySamples = 0;
        }
        return limit;
    }
}
