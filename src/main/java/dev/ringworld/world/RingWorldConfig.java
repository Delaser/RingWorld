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
                              boolean testMode, boolean pregenerateTerrainAtlas) {
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
            properties.setProperty("pregenerateTerrainAtlas", "true");
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
        boolean pregenerateTerrainAtlas = Boolean.parseBoolean(
                properties.getProperty("pregenerateTerrainAtlas", "true"));
        // Centralise validation so a malformed bootstrap file fails before chunks are generated.
        new RingGeometry(width, circumference);
        if (wallHeight < 32) throw new IllegalArgumentException("wallHeightBlocks must be at least 32");
        loaded = new RingWorldConfig(width, circumference, wallHeight, testMode,
                pregenerateTerrainAtlas);
        RingWorldMod.LOGGER.info("RingWorld bootstrap settings: width={}, circumference={}, wallHeight={}, testMode={}, pregenerateTerrainAtlas={}",
                width, circumference, wallHeight, testMode, pregenerateTerrainAtlas);
        return loaded;
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
