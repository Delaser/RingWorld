package dev.ringworld.world;

import dev.ringworld.RingWorldMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Bootstrap settings used only while creating a world. Once a world has saved
 * RingWorldSettings, its dimensions take precedence permanently.
 */
public record RingWorldConfig(int widthBlocks, int circumferenceBlocks, int wallHeightBlocks,
                              boolean testMode, int testViewDistanceChunks,
                              boolean pregenerateTerrainAtlas,
                              boolean requestOceanMonument) {
    private static final String FILE_NAME = "ringworld.properties";
    private static RingWorldConfig loaded;

    public static synchronized RingWorldConfig load() {
        if (loaded != null) return loaded;
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read " + path, exception);
            }
        } else {
            properties.setProperty("widthBlocks", Integer.toString(RingWorldSettings.DEFAULT_WIDTH));
            properties.setProperty("circumferenceBlocks", Integer.toString(RingWorldSettings.DEFAULT_CIRCUMFERENCE));
            properties.setProperty("wallHeightBlocks", Integer.toString(RingWorldSettings.DEFAULT_WALL_HEIGHT));
            properties.setProperty("testMode", "false");
            properties.setProperty("testViewDistanceChunks", "28");
            properties.setProperty("pregenerateTerrainAtlas", "true");
            properties.setProperty("requestOceanMonument", "false");
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    properties.store(writer, "RingWorld bootstrap settings. Change before first world load only.");
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not create " + path, exception);
            }
        }
        int width = integer(properties, "widthBlocks", RingWorldSettings.DEFAULT_WIDTH);
        int circumference = integer(properties, "circumferenceBlocks", RingWorldSettings.DEFAULT_CIRCUMFERENCE);
        int wallHeight = integer(properties, "wallHeightBlocks", RingWorldSettings.DEFAULT_WALL_HEIGHT);
        boolean testMode = Boolean.parseBoolean(properties.getProperty("testMode", "false"));
        int testViewDistance = integer(properties, "testViewDistanceChunks", 28);
        boolean pregenerateTerrainAtlas = Boolean.parseBoolean(
                properties.getProperty("pregenerateTerrainAtlas", "true"));
        boolean requestOceanMonument = Boolean.parseBoolean(
                properties.getProperty("requestOceanMonument", "false"));
        // Centralise validation so a malformed bootstrap file fails before chunks are generated.
        new RingGeometry(width, circumference);
        if (wallHeight < 32) throw new IllegalArgumentException("wallHeightBlocks must be at least 32");
        if (testViewDistance < 2 || testViewDistance > 32) {
            throw new IllegalArgumentException(
                    "testViewDistanceChunks must be between 2 and 32");
        }
        loaded = new RingWorldConfig(width, circumference, wallHeight, testMode, testViewDistance,
                pregenerateTerrainAtlas, requestOceanMonument);
        RingWorldMod.LOGGER.info("RingWorld bootstrap settings: width={}, circumference={}, wallHeight={}, testMode={}, testViewDistance={}, pregenerateTerrainAtlas={}, requestOceanMonument={}",
                width, circumference, wallHeight, testMode, testViewDistance,
                pregenerateTerrainAtlas, requestOceanMonument);
        return loaded;
    }

    /**
     * Updates only the first-world layout fields. Operational test and atlas
     * switches remain unchanged, and saved worlds continue to ignore this
     * bootstrap state.
     */
    public static synchronized RingWorldConfig saveBootstrapLayout(
            int widthBlocks, int circumferenceBlocks, int wallHeightBlocks, boolean requestOceanMonument) {
        RingGeometry geometry = new RingGeometry(widthBlocks, circumferenceBlocks);
        RingDimensionReport.forVanillaOverworld(geometry, wallHeightBlocks).requireValid();
        RingWorldConfig current = load();
        RingWorldConfig replacement = new RingWorldConfig(
                widthBlocks, circumferenceBlocks, wallHeightBlocks,
                current.testMode(), current.testViewDistanceChunks(),
                current.pregenerateTerrainAtlas(), requestOceanMonument);
        Properties properties = new Properties();
        properties.setProperty("widthBlocks", Integer.toString(widthBlocks));
        properties.setProperty("circumferenceBlocks", Integer.toString(circumferenceBlocks));
        properties.setProperty("wallHeightBlocks", Integer.toString(wallHeightBlocks));
        properties.setProperty("testMode", Boolean.toString(current.testMode()));
        properties.setProperty("testViewDistanceChunks",
                Integer.toString(current.testViewDistanceChunks()));
        properties.setProperty("pregenerateTerrainAtlas",
                Boolean.toString(current.pregenerateTerrainAtlas()));
        properties.setProperty("requestOceanMonument", Boolean.toString(requestOceanMonument));
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer,
                        "RingWorld bootstrap settings. Saved world settings remain immutable.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update " + path, exception);
        }
        loaded = replacement;
        RingWorldMod.LOGGER.info("Updated first-world RingWorld layout: {}x{}, wallHeight={}, requestOceanMonument={}",
                circumferenceBlocks, widthBlocks, wallHeightBlocks, requestOceanMonument);
        return replacement;
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
