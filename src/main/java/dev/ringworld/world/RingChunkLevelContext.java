package dev.ringworld.world;

import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/** Scopes periodic graph propagation to one Overworld chunk-manager update. */
public final class RingChunkLevelContext {
    private static final ThreadLocal<RingGeometry> ACTIVE = new ThreadLocal<>();

    private RingChunkLevelContext() { }

    public static boolean run(RingGeometry geometry, BooleanSupplier operation) {
        RingGeometry previous = ACTIVE.get();
        ACTIVE.set(geometry);
        try {
            return operation.getAsBoolean();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    @Nullable
    public static RingGeometry activeGeometry() {
        return ACTIVE.get();
    }
}
