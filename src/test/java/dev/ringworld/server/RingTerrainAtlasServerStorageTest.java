package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingTerrainAtlasServerStorageTest {
    @Test
    void resolvesNamespacedDimensionAndExactLegacyAtlasPaths(@TempDir Path worldRoot) {
        Path dimension = worldRoot.resolve("dimensions/minecraft/overworld");

        assertEquals(
                dimension.resolve("data/ringworld/terrain-atlas.rwat.gz"),
                RingTerrainAtlasServer.cachePath(dimension));
        assertEquals(
                worldRoot.resolve("data/ringworld-terrain-atlas.rwat.gz"),
                RingTerrainAtlasServer.legacyCachePath(worldRoot));
    }
}
