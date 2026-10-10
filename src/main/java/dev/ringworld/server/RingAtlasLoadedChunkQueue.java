package dev.ringworld.server;

import java.util.LinkedHashSet;
import java.util.Set;

/** Coalesced owner-thread capture requests; retains coordinates, never live chunks. */
final class RingAtlasLoadedChunkQueue {
    private final Set<Long> pending = new LinkedHashSet<>();

    void enqueue(int x, int z) { pending.add(key(x, z)); }
    boolean contains(int x, int z) { return pending.contains(key(x, z)); }
    void remove(int x, int z) { pending.remove(key(x, z)); }
    int size() { return pending.size(); }

    Position poll() {
        var iterator = pending.iterator();
        if (!iterator.hasNext()) return null;
        long key = iterator.next();
        iterator.remove();
        return new Position((int)key, (int)(key >>> 32));
    }

    private static long key(int x, int z) { return (x & 0xffffffffL) | ((long)z << 32); }
    record Position(int x, int z) { }
}
