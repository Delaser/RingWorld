package dev.ringworld.api;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/** Stable, read-only entry point for mods that want to be ring-world aware. */
public final class RingWorldApi {
    private RingWorldApi() { }

    public static boolean isRingWorld(ServerWorld world) {
        return world.getRegistryKey() == ServerWorld.OVERWORLD;
    }

    @Nullable
    public static RingWorldSettings settings(ServerWorld world) {
        return isRingWorld(world) ? RingWorldSettings.get(world) : null;
    }

    public static RingGeometry geometry(ServerWorld world) {
        RingWorldSettings settings = settings(world);
        if (settings == null) {
            throw new IllegalArgumentException("World is not a RingWorld Overworld");
        }
        return settings.geometry();
    }
}
