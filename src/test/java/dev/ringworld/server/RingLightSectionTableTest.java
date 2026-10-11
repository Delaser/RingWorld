package dev.ringworld.server;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RingLightSectionTableTest {
    @Test void snapshotsStayIndependentAcrossChangesAndRemovals() {
        var table = new RingLightSectionTable<String>();
        long a = 1L << 42, b = 2L << 42;
        table.put(a, "old"); table.put(b, "retained"); table.put(0, "zero");
        var first = table.copy();
        table.put(a, "new"); table.remove(b);
        var second = table.copy();
        table.remove(a); table.remove(0);
        assertEquals("old", first.get(a)); assertEquals("retained", first.get(b));
        assertEquals("new", second.get(a)); assertNull(second.get(b));
        assertEquals("zero", first.get(0)); assertEquals("zero", second.get(0));
        assertNull(table.get(a)); assertFalse(table.containsKey(0));
        first.put(a, "branch"); first.remove(0);
        assertEquals("new", second.get(a)); assertEquals("zero", second.get(0));
    }

    @Test void randomizedSnapshotsMatchIndependentVanillaMaps() {
        var table = new RingLightSectionTable<Integer>();
        var expected = new Long2ObjectOpenHashMap<Integer>();
        var copies = new ArrayList<RingLightSectionTable<Integer>>();
        var goldens = new ArrayList<Long2ObjectOpenHashMap<Integer>>();
        var random = new Random(279);
        for (int i = 0; i < 12000; i++) {
            long key = ((long) random.nextInt(512) << 42) | ((long) random.nextInt(32) << 20) | random.nextInt(32);
            if (random.nextBoolean()) assertEquals(expected.put(key, Integer.valueOf(i)), table.put(key, i));
            else assertEquals(expected.remove(key), table.remove(key));
            assertEquals(expected.get(key), table.get(key));
            assertEquals(expected.containsKey(key), table.containsKey(key));
            if (i % 1000 == 0) { copies.add(table.copy()); goldens.add(expected.clone()); }
        }
        for (int i = 0; i < copies.size(); i++) {
            var snapshot = copies.get(i);
            goldens.get(i).long2ObjectEntrySet().forEach(e -> assertEquals(e.getValue(), snapshot.get(e.getLongKey())));
        }
    }
}
