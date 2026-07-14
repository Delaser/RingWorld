package dev.ringworld.client.chunk;

import net.minecraft.client.world.ClientChunkManager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/** Associates each client chunk manager with its current private map instance. */
public final class RingClientChunkMaps {
    private static final Map<ClientChunkManager, RingClientChunkMapAccess> MAPS = new WeakHashMap<>();

    private RingClientChunkMaps() { }

    public static synchronized void register(ClientChunkManager manager, RingClientChunkMapAccess map) {
        MAPS.put(manager, map);
    }

    @Nullable
    public static synchronized RingClientChunkMapAccess get(ClientChunkManager manager) {
        return MAPS.get(manager);
    }
}
