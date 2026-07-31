package dev.ringworld.client.chunk;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientChunkCache;

/** Associates each client chunk manager with its current private map instance. */
public final class RingClientChunkMaps {
    private static final Map<ClientChunkCache, RingClientChunkMapAccess> MAPS = new WeakHashMap<>();

    private RingClientChunkMaps() { }

    public static synchronized void register(ClientChunkCache manager, RingClientChunkMapAccess map) {
        MAPS.put(manager, map);
    }

    @Nullable
    public static synchronized RingClientChunkMapAccess get(ClientChunkCache manager) {
        return MAPS.get(manager);
    }
}
