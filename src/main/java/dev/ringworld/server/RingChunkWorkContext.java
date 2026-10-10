package dev.ringworld.server;

import java.util.function.BooleanSupplier;

/** Scope scheduled saving without changing explicit command or shutdown semantics. */
public final class RingChunkWorkContext {
    private static final ThreadLocal<Boolean> PERIODIC_SAVE = new ThreadLocal<>();
    private RingChunkWorkContext() { }

    public static boolean periodicSave() { return Boolean.TRUE.equals(PERIODIC_SAVE.get()); }
    public static boolean periodicSave(BooleanSupplier action) {
        Boolean previous = PERIODIC_SAVE.get();
        PERIODIC_SAVE.set(true);
        try { return action.getAsBoolean(); }
        finally { if (previous == null) PERIODIC_SAVE.remove(); else PERIODIC_SAVE.set(previous); }
    }
}
