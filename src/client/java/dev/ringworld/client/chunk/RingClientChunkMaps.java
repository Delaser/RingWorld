package dev.ringworld.client.chunk;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientChunkCache;

/** Associates each client chunk manager with its current private map instance. */
public final class RingClientChunkMaps {
    // Vanilla storage retains its owner, directly and through loaded chunks.
    // A strong value would therefore keep this WeakHashMap's key alive forever.
    // The live manager already owns its current storage; this registry owns neither.
    private static final Map<ClientChunkCache, WeakReference<RingClientChunkMapAccess>> MAPS = new WeakHashMap<>();

    private RingClientChunkMaps() { }

    public static synchronized void register(ClientChunkCache manager, RingClientChunkMapAccess map) {
        MAPS.put(manager, new WeakReference<>(map));
    }

    @Nullable
    public static synchronized RingClientChunkMapAccess get(ClientChunkCache manager) {
        WeakReference<RingClientChunkMapAccess> reference = MAPS.get(manager);
        RingClientChunkMapAccess map = reference == null ? null : reference.get();
        if (reference != null && map == null) MAPS.remove(manager);
        return map;
    }
}
