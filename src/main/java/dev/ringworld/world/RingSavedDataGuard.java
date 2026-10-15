package dev.ringworld.world;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Vanilla returns null for both absent and unreadable data; defaults require absence. */
final class RingSavedDataGuard {
    private RingSavedDataGuard() {}

    static <T extends SavedData> T get(ServerLevel world, SavedData.Factory<T> factory, String key) {
        T saved = world.getDataStorage().get(factory, key);
        if (saved == null) requireMissing(path(world, key));
        return saved;
    }

    static Path path(ServerLevel world, String key) {
        return RingWorldStorageAccess.dimensionPath(world).resolve("data").resolve(key + ".dat");
    }

    static void requireMissing(Path file) {
        if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Could not load existing RingWorld saved data: " + file
                    + ". Refusing to replace saved state with defaults.");
        }
    }
}
