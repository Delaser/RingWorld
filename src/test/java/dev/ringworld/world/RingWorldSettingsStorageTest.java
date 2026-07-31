package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingWorldSettingsStorageTest {
    @Test
    void resolvesNamespacedDimensionAndLegacyRootPaths(@TempDir Path directory) {
        Path dimension = directory.resolve("dimensions/minecraft/overworld");

        assertEquals(
                dimension.resolve("data/ringworld/settings.dat"),
                RingWorldSettings.settingsPath(dimension));
        assertEquals(
                directory.resolve("data/ringworld_settings.dat"),
                RingWorldSettings.legacySettingsPath(directory));
    }

    @Test
    void atomicallyCopiesLegacySettingsOnlyWhenCurrentStateIsAbsent(
            @TempDir Path directory) throws Exception {
        Path legacy = directory.resolve("legacy.dat");
        Path current = directory.resolve("dimension/data/ringworld/settings.dat");
        Files.write(legacy, new byte[] {1, 2, 3});
        Files.createDirectories(current.getParent());
        Files.write(current.resolveSibling("settings.dat.tmp"), new byte[] {9});

        assertTrue(RingWorldSettings.copyAtomicallyIfAbsent(legacy, current));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(current));
        assertFalse(Files.exists(current.resolveSibling("settings.dat.tmp")));

        Files.write(legacy, new byte[] {4, 5, 6});
        assertFalse(RingWorldSettings.copyAtomicallyIfAbsent(legacy, current));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(current));
    }
}
