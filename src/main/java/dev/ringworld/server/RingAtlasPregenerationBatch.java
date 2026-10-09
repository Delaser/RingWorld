package dev.ringworld.server;

import dev.ringworld.world.RingAtlasPregenerationCursor;
import dev.ringworld.world.RingTerrainAtlas;
import java.util.ArrayList;
import java.util.List;

/** Bounded server-thread leases sharing one canonical cursor. No worker world access. */
final class RingAtlasPregenerationBatch<T> {
    static final int MAX_REQUESTS = 8;
    final List<Slot<T>> slots;
    final long totalChunks;
    private int nextCompletedSlot;

    RingAtlasPregenerationBatch(RingTerrainAtlas atlas, int capacity) {
        if (capacity < 1 || capacity > MAX_REQUESTS) throw new IllegalArgumentException("Atlas concurrency must be 1..8");
        var cursor = new RingAtlasPregenerationCursor(atlas.geometry(), atlas);
        totalChunks = cursor.totalChunks();
        var entries = new ArrayList<Slot<T>>(capacity);
        for (int i = 0; i < capacity; i++) entries.add(new Slot<>(cursor));
        slots = List.copyOf(entries);
    }

    int inFlight() { return (int) slots.stream().filter(slot -> slot.request != null).count(); }
    boolean hasWork() { return slots.stream().anyMatch(slot -> slot.request != null || slot.selection.selected() != null); }

    /** One ready slot per tick, fairly rotating; unfinished older loads never block ready ones. */
    Slot<T> nextCompleted() {
        for (int offset = 0; offset < slots.size(); offset++) {
            int index = (nextCompletedSlot + offset) % slots.size();
            Slot<T> slot = slots.get(index);
            if (slot.request != null && !slot.processed && slot.request.isDone()) {
                nextCompletedSlot = (index + 1) % slots.size();
                return slot;
            }
        }
        return null;
    }

    void release(Slot<T> slot, boolean cancel, int attempts) {
        if (slot.request == null) return;
        if (cancel) slot.request.cancelWithReleaseAttempts(attempts);
        else slot.request.close();
        slot.request = null;
        slot.processed = false;
    }

    /** Try every lease even when one release fails; retain failed leases for retry. */
    void cancelAll(int attempts) {
        RuntimeException first = null;
        for (var slot : slots) {
            try { release(slot, true, attempts); }
            catch (RuntimeException failure) {
                if (first == null) first = failure;
                else if (first != failure) first.addSuppressed(failure);
            }
        }
        if (first != null) throw first;
    }

    static final class Slot<T> {
        final RingAtlasPregenerationSelection selection;
        RingAtlasChunkRequest<T> request;
        boolean processed;
        Slot(RingAtlasPregenerationCursor cursor) { selection = new RingAtlasPregenerationSelection(cursor); }
    }
}
