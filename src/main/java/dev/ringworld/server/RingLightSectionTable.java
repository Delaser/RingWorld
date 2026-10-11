package dev.ringworld.server;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;

/** Single-writer lighting lookup with independently owned published snapshots.
 * Publish copied tables and never mutate a published instance.
 * Values retain vanilla DataLayer copy-on-write ownership; only the lookup is partitioned.
 */
public final class RingLightSectionTable<V> {
    private static final int PARTITIONS = 256;
    private final Long2ObjectOpenHashMap<V>[] partitions;
    private final boolean[] shared = new boolean[PARTITIONS];

    @SuppressWarnings("unchecked")
    public RingLightSectionTable() {
        partitions = (Long2ObjectOpenHashMap<V>[]) new Long2ObjectOpenHashMap<?>[PARTITIONS];
    }

    private RingLightSectionTable(Long2ObjectOpenHashMap<V>[] partitions) {
        this.partitions = partitions;
        Arrays.fill(shared, true);
    }

    private static int partition(long section) {
        // SectionPos packs Y into its low 20 bits: keep one column's layers together.
        return (int) HashCommon.mix(section >>> 20) & (PARTITIONS - 1);
    }

    public V get(long key) {
        var map = partitions[partition(key)];
        return map == null ? null : map.get(key);
    }

    public boolean containsKey(long key) {
        var map = partitions[partition(key)];
        return map != null && map.containsKey(key);
    }

    public V put(long key, V value) {
        return writable(partition(key)).put(key, value);
    }

    public V remove(long key) {
        int index = partition(key);
        if (partitions[index] == null || !partitions[index].containsKey(key)) return null;
        return writable(index).remove(key);
    }

    private Long2ObjectOpenHashMap<V> writable(int index) {
        var map = partitions[index];
        if (map == null) map = new Long2ObjectOpenHashMap<>();
        else if (shared[index]) map = map.clone();
        partitions[index] = map;
        shared[index] = false;
        return map;
    }

    public RingLightSectionTable<V> copy() {
        Arrays.fill(shared, true);
        return new RingLightSectionTable<>(partitions.clone());
    }
}
