package dev.ringworld.client.chunk;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientChunkCache;

/** Associates each client chunk manager with its current private map instance. */
public final class RingClientChunkMaps {
    private static final Map<ClientChunkCache, WeakReference<RingClientChunkMapAccess>> MAPS = new WeakHashMap<>();

    private RingClientChunkMaps() { }

    public static synchronized void register(ClientChunkCache manager, RingClientChunkMapAccess map) {
        // Vanilla Storage holds its manager. A strong value would keep the weak key alive.
        MAPS.put(manager, new WeakReference<>(map));
    }

    @Nullable
    public static synchronized RingClientChunkMapAccess get(ClientChunkCache manager) {
        var reference = MAPS.get(manager);
        return reference == null ? null : reference.get();
    }
}
