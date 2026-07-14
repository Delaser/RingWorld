package dev.ringworld.world;

import java.util.function.Supplier;

/** Marks generation-region arrays created for the ring Overworld. */
public final class RingRegionContext {
    private static final ThreadLocal<Integer> CIRCUMFERENCE_CHUNKS = new ThreadLocal<>();

    private RingRegionContext() { }

    public static <T> T run(int circumferenceChunks, Supplier<T> operation) {
        Integer previous = CIRCUMFERENCE_CHUNKS.get();
        CIRCUMFERENCE_CHUNKS.set(circumferenceChunks);
        try {
            return operation.get();
        } finally {
            if (previous == null) CIRCUMFERENCE_CHUNKS.remove();
            else CIRCUMFERENCE_CHUNKS.set(previous);
        }
    }

    public static int activeCircumferenceChunks() {
        return CIRCUMFERENCE_CHUNKS.get() == null ? 0 : CIRCUMFERENCE_CHUNKS.get();
    }
}
