package dev.ringworld.platform.neoforge.compat.create610;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RingCreate610OwnedStateTableTest {
    @Test
    void initialTransformFailureDeletesProvisionalChild() {
        RingCreate610OwnedStateTable<Object, Object> table =
                new RingCreate610OwnedStateTable<>(ignored -> { });
        IllegalStateException primary = new IllegalStateException("transform");

        assertSame(primary, assertThrows(IllegalStateException.class,
                () -> table.createInitialized(Object::new, ignored -> { throw primary; })));
        assertEquals(new RingCreate610OwnedStateTable.Counters(0, 1, 1, 0),
                table.counters());
    }

    @Test
    void cleanupFailureIsSuppressedBehindPrimaryFailure() {
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        RingCreate610OwnedStateTable<Object, Object> table =
                new RingCreate610OwnedStateTable<>(ignored -> { throw cleanup; });
        IllegalStateException primary = new IllegalStateException("native");

        assertSame(primary, assertThrows(IllegalStateException.class,
                () -> table.createInitialized(Object::new, ignored -> { throw primary; })));
        assertEquals(1, primary.getSuppressed().length);
        assertSame(cleanup, primary.getSuppressed()[0]);
        assertEquals(new RingCreate610OwnedStateTable.Counters(0, 1, 0, 1),
                table.counters());
    }

    @Test
    void releaseAllAttemptsEveryDeleteAndClearsOwnershipFirst() {
        Map<Object, RuntimeException> failures = new IdentityHashMap<>();
        RingCreate610OwnedStateTable<Object, Object> table =
                new RingCreate610OwnedStateTable<>(value -> {
                    RuntimeException failure = failures.get(value);
                    if (failure != null) throw failure;
                });
        Object first = table.createInitialized(Object::new, ignored -> { });
        Object second = table.createInitialized(Object::new, ignored -> { });
        table.register(new Object(), first);
        table.register(new Object(), second);
        failures.put(first, new IllegalStateException("first"));
        failures.put(second, new IllegalArgumentException("second"));

        RuntimeException aggregate = assertThrows(RuntimeException.class, table::releaseAll);
        assertEquals(1, aggregate.getSuppressed().length);
        assertEquals(new RingCreate610OwnedStateTable.Counters(0, 2, 0, 2),
                table.counters());
    }

    @Test
    void staleReleaseRemovesOwnerBeforeThrowingDelete() {
        RingCreate610OwnedStateTable<Object, Object> table =
                new RingCreate610OwnedStateTable<>(ignored -> {
                    throw new IllegalStateException("delete");
                });
        Object owner = new Object();
        Object value = table.createInitialized(Object::new, ignored -> { });
        table.register(owner, value);

        assertThrows(IllegalStateException.class, () -> table.release(owner));
        assertEquals(new RingCreate610OwnedStateTable.Counters(0, 1, 0, 1),
                table.counters());
    }

    @Test
    void replacementFailureLeavesOnlyNewOwner() {
        Object oldValue = new Object();
        Object newValue = new Object();
        RingCreate610OwnedStateTable<Object, Object> table =
                new RingCreate610OwnedStateTable<>(value -> {
                    if (value == oldValue) throw new IllegalStateException("old delete");
                });
        Object owner = new Object();
        table.register(owner, table.createInitialized(() -> oldValue, ignored -> { }));
        Object replacement = table.createInitialized(() -> newValue, ignored -> { });

        assertThrows(IllegalStateException.class, () -> table.register(owner, replacement));
        assertSame(newValue, table.get(owner));
        assertEquals(new RingCreate610OwnedStateTable.Counters(1, 2, 0, 1),
                table.counters());
    }
}
