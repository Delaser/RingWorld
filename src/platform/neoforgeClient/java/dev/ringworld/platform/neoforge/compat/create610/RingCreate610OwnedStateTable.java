package dev.ringworld.platform.neoforge.compat.create610;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Identity-keyed ownership and exception aggregation without Flywheel types. */
final class RingCreate610OwnedStateTable<K, V> {
    private final IdentityHashMap<K, V> values = new IdentityHashMap<>();
    private final Consumer<V> deleter;
    private long created;
    private long deleted;
    private long failedDeletes;

    RingCreate610OwnedStateTable(Consumer<V> deleter) {
        this.deleter = deleter;
    }

    V createInitialized(Supplier<V> factory, Consumer<V> initializer) {
        V value = factory.get();
        created++;
        try {
            initializer.accept(value);
            return value;
        } catch (RuntimeException | Error primary) {
            discardSuppressing(value, primary);
            throw primary;
        }
    }

    void discard(V value) {
        if (value != null) delete(value);
    }

    void discardSuppressing(V value, Throwable primary) {
        if (value == null) return;
        try {
            delete(value);
        } catch (RuntimeException | Error cleanupFailure) {
            suppress(primary, cleanupFailure);
        }
    }

    void register(K owner, V replacement) {
        V previous = values.put(owner, replacement);
        if (previous != null && previous != replacement) delete(previous);
    }

    void release(K owner) {
        V previous = values.remove(owner);
        if (previous != null) delete(previous);
    }

    void releaseSuppressing(K owner, Throwable primary) {
        V previous = values.remove(owner);
        if (previous != null) discardSuppressing(previous, primary);
    }

    void releaseAll() {
        List<V> removed = new ArrayList<>(values.values());
        values.clear();
        Throwable aggregate = null;
        for (V value : removed) {
            try {
                delete(value);
            } catch (RuntimeException | Error failure) {
                if (aggregate == null) aggregate = failure;
                else suppress(aggregate, failure);
            }
        }
        if (aggregate != null) throwUnchecked(aggregate);
    }

    void releaseAllSuppressing(Throwable primary) {
        try {
            releaseAll();
        } catch (RuntimeException | Error cleanupFailure) {
            suppress(primary, cleanupFailure);
        }
    }

    V get(K owner) {
        return values.get(owner);
    }

    List<Map.Entry<K, V>> entriesSnapshot() {
        return values.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    Counters counters() {
        return new Counters(values.size(), created, deleted, failedDeletes);
    }

    private void delete(V value) {
        try {
            deleter.accept(value);
            deleted++;
        } catch (RuntimeException | Error failure) {
            failedDeletes++;
            throw failure;
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw (Error) failure;
    }

    private static void suppress(Throwable primary, Throwable cleanupFailure) {
        if (primary != cleanupFailure) primary.addSuppressed(cleanupFailure);
    }

    record Counters(int owned, long created, long deleted, long failedDeletes) { }
}
