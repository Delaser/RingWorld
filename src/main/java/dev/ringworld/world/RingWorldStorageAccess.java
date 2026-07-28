package dev.ringworld.world;

import java.nio.file.Path;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Read-only access to Minecraft's authoritative per-dimension storage path.
 *
 * <p>Minecraft 26.1 keeps the {@code LevelStorageAccess} handle protected on
 * the server. Storage owners must use this bridge instead of reconstructing
 * dimension folder names or treating the world root as the Overworld.</p>
 */
public interface RingWorldStorageAccess {
    Path ringworld$getDimensionPath(ResourceKey<Level> dimension);

    static Path dimensionPath(ServerLevel level) {
        return ((RingWorldStorageAccess) level.getServer())
                .ringworld$getDimensionPath(level.dimension());
    }
}
