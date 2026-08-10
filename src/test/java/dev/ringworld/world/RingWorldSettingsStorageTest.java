package dev.ringworld.world;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void persistedLegacyGeometryRetainsTheStructural1024BlockCircumference() {
        RingWorldSettings legacy = new RingWorldSettings(
                128, RingWorldSettings.MIN_CIRCUMFERENCE, 42L, 160, 1);

        assertEquals(128, legacy.widthBlocks());
        assertEquals(RingWorldSettings.MIN_CIRCUMFERENCE, legacy.circumferenceBlocks());
        assertEquals(1, legacy.formatVersion());
        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, legacy.terrainNoiseMapping());
    }

    @Test
    void formatUpgradePreservesLegacyTerrainNoiseWhileFreshSettingsUseAnnular() {
        RingWorldSettings alpha = new RingWorldSettings(
                256, 16_384, 42L, 160, 64, 2);
        RingWorldSettings upgraded = RingWorldSettings.upgradeToCurrentFormat(alpha);
        RingWorldSettings fresh = new RingWorldSettings(
                256, 16_384, 42L, 160, RingWorldSettings.FORMAT_VERSION);

        assertEquals(RingWorldSettings.FORMAT_VERSION, upgraded.formatVersion());
        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, upgraded.terrainNoiseMapping());
        assertEquals(RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2, fresh.terrainNoiseMapping());
        assertFalse(upgraded.layoutFingerprint() == fresh.layoutFingerprint());
    }

    @Test
    void absentMappingInFormatTwoDecodesAsLegacyAndCurrentEncodingNamesAnnular() {
        JsonObject alphaJson = new JsonObject();
        alphaJson.addProperty("width", 256);
        alphaJson.addProperty("circumference", 16_384);
        alphaJson.addProperty("seed", 42L);
        alphaJson.addProperty("wallHeight", 160);
        alphaJson.addProperty("surfaceReferenceY", 64);
        alphaJson.addProperty("format", 2);

        RingWorldSettings alpha = RingWorldSettings.codecForTests()
                .parse(JsonOps.INSTANCE, alphaJson).getOrThrow();
        RingWorldSettings upgraded = RingWorldSettings.upgradeToCurrentFormat(alpha);
        JsonObject upgradedJson = RingWorldSettings.codecForTests()
                .encodeStart(JsonOps.INSTANCE, upgraded).getOrThrow().getAsJsonObject();
        RingWorldSettings reopened = RingWorldSettings.codecForTests()
                .parse(JsonOps.INSTANCE, upgradedJson).getOrThrow();
        RingWorldSettings current = new RingWorldSettings(
                256, 16_384, 42L, 160, RingWorldSettings.FORMAT_VERSION);
        JsonObject currentJson = RingWorldSettings.codecForTests()
                .encodeStart(JsonOps.INSTANCE, current).getOrThrow().getAsJsonObject();

        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, alpha.terrainNoiseMapping());
        assertEquals(RingWorldSettings.FORMAT_VERSION, reopened.formatVersion());
        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, reopened.terrainNoiseMapping());
        assertEquals(RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2,
                currentJson.get("terrainNoiseMapping").getAsInt());
        assertEquals(RingWorldSettings.FORMAT_VERSION, currentJson.get("format").getAsInt());
    }

    @Test
    void preFormatThreeSettingsCannotClaimAnnularTerrain() {
        assertThrows(IllegalArgumentException.class, () -> new RingWorldSettings(
                256, 16_384, 42L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, 2));
        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, new RingWorldSettings(
                256, 16_384, 42L, 160, 64,
                RingTerrainNoiseMapping.LEGACY_AXIAL, 3).terrainNoiseMapping());
    }
}
