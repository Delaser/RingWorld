package dev.ringworld.server;

import java.util.function.BooleanSupplier;

/** Narrow owner-thread scopes; explicit saves and blocking chunk requests bypass budgets. */
public final class RingChunkWorkContext {
    private static final ThreadLocal<Boolean> PERIODIC_SAVE = new ThreadLocal<>();
    private static final ThreadLocal<RingChunkWorkBudget> GRAPH = new ThreadLocal<>();
    private RingChunkWorkContext() { }

    public static boolean periodicSave() { return Boolean.TRUE.equals(PERIODIC_SAVE.get()); }
    public static boolean periodicSave(BooleanSupplier action) {
        Boolean previous = PERIODIC_SAVE.get();
        PERIODIC_SAVE.set(true);
        try { return action.getAsBoolean(); }
        finally { if (previous == null) PERIODIC_SAVE.remove(); else PERIODIC_SAVE.set(previous); }
    }
    public static RingChunkWorkBudget graphBudget() { return GRAPH.get(); }
    public static boolean graph(RingChunkWorkBudget budget, BooleanSupplier action) {
        RingChunkWorkBudget previous = GRAPH.get();
        if (budget == null) GRAPH.remove(); else GRAPH.set(budget);
        try { return action.getAsBoolean(); }
        finally { if (previous == null) GRAPH.remove(); else GRAPH.set(previous); }
    }
}
