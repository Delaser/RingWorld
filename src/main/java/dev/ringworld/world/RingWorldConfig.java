package dev.ringworld.world;

import dev.ringworld.RingWorldMod;
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
                              RingWallStyle wallStyle,
                              RingSkyProfile skyProfile,
                              boolean testMode, int testViewDistanceChunks,
                              boolean pregenerateTerrainAtlas,
                              boolean requestOceanMonument) {
    private static final String FILE_NAME = "ringworld.properties";
    private static Path configDirectory = Path.of("config");
    private static RingWorldConfig loaded;

    /** Platform bootstrap must provide its authoritative configuration directory before loading. */
    public static synchronized void configureDirectory(Path directory) {
        if (directory == null) throw new IllegalArgumentException("config directory is required");
        Path normalized = directory.toAbsolutePath().normalize();
        if (loaded != null && !configDirectory.toAbsolutePath().normalize().equals(normalized)) {
            throw new IllegalStateException("RingWorld config was already loaded from " + configDirectory);
        }
        configDirectory = normalized;
    }

    private static Path configPath() {
        return configDirectory.resolve(FILE_NAME);
    }

    public static synchronized RingWorldConfig load() {
        if (loaded != null) return loaded;
        Path path = configPath();
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
            properties.setProperty("wallPreset", RingWallStyle.Preset.WEATHERED_FORTIFICATION.name());
            properties.setProperty("skyBackdrop", RingSkyProfile.Backdrop.ATMOSPHERE.name());
            properties.setProperty("sunStyle", RingSkyProfile.LightSource.SMALL.name());
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
        RingWallStyle wallStyle = wallStyle(properties);
        RingSkyProfile skyProfile = skyProfile(properties);
        boolean testMode = Boolean.parseBoolean(properties.getProperty("testMode", "false"));
        int testViewDistance = integer(properties, "testViewDistanceChunks", 28);
        boolean pregenerateTerrainAtlas = Boolean.parseBoolean(
                properties.getProperty("pregenerateTerrainAtlas", "true"));
        boolean requestOceanMonument = Boolean.parseBoolean(
                properties.getProperty("requestOceanMonument", "false"));
        // Saved worlds ignore this bootstrap layout, so retain structural parsing
        // support for old configuration while first-world creation applies the
        // stricter new-world admission below.
        new RingGeometry(width, circumference);
        if (wallHeight < 32) throw new IllegalArgumentException("wallHeightBlocks must be at least 32");
        if (testViewDistance < 2 || testViewDistance > 32) {
            throw new IllegalArgumentException(
                    "testViewDistanceChunks must be between 2 and 32");
        }
        loaded = new RingWorldConfig(width, circumference, wallHeight, wallStyle, skyProfile,
                testMode, testViewDistance,
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
        return saveBootstrapLayout(widthBlocks, circumferenceBlocks, wallHeightBlocks,
                load().wallStyle(), requestOceanMonument);
    }

    public static synchronized RingWorldConfig saveBootstrapLayout(
            int widthBlocks, int circumferenceBlocks, int wallHeightBlocks,
            RingWallStyle wallStyle, boolean requestOceanMonument) {
        return saveBootstrapLayout(widthBlocks, circumferenceBlocks, wallHeightBlocks,
                wallStyle, load().skyProfile(), requestOceanMonument);
    }

    public static synchronized RingWorldConfig saveBootstrapLayout(
            int widthBlocks, int circumferenceBlocks, int wallHeightBlocks,
            RingWallStyle wallStyle, RingSkyProfile skyProfile,
            boolean requestOceanMonument) {
        if (wallStyle == null) throw new IllegalArgumentException("wall style is required");
        if (skyProfile == null) throw new IllegalArgumentException("sky profile is required");
        validateNewWorldLayout(widthBlocks, circumferenceBlocks, wallHeightBlocks,
                wallStyle.thicknessBlocks());
        boolean effectiveMonumentRequest = effectiveOceanMonumentRequest(
                new RingGeometry(widthBlocks, circumferenceBlocks), requestOceanMonument);
        RingWorldConfig current = load();
        RingWorldConfig replacement = new RingWorldConfig(
                widthBlocks, circumferenceBlocks, wallHeightBlocks, wallStyle, skyProfile,
                current.testMode(), current.testViewDistanceChunks(),
                current.pregenerateTerrainAtlas(), effectiveMonumentRequest);
        Properties properties = new Properties();
        properties.setProperty("widthBlocks", Integer.toString(widthBlocks));
        properties.setProperty("circumferenceBlocks", Integer.toString(circumferenceBlocks));
        properties.setProperty("wallHeightBlocks", Integer.toString(wallHeightBlocks));
        properties.setProperty("wallPreset", RingWallStyle.Preset.matching(wallStyle).name());
        properties.setProperty("wallThicknessBlocks", Integer.toString(wallStyle.thicknessBlocks()));
        properties.setProperty("wallPalette", Integer.toString(wallStyle.palette().id()));
        properties.setProperty("wallPattern", Integer.toString(wallStyle.pattern().id()));
        properties.setProperty("wallDecayPercent", Integer.toString(wallStyle.decayPercent()));
        properties.setProperty("wallStyleFormat", Integer.toString(wallStyle.formatVersion()));
        properties.setProperty("skyBackdrop", skyProfile.backdrop().name());
        properties.setProperty("sunStyle", skyProfile.lightSource().name());
        properties.setProperty("testMode", Boolean.toString(current.testMode()));
        properties.setProperty("testViewDistanceChunks",
                Integer.toString(current.testViewDistanceChunks()));
        properties.setProperty("pregenerateTerrainAtlas",
                Boolean.toString(current.pregenerateTerrainAtlas()));
        properties.setProperty("requestOceanMonument", Boolean.toString(effectiveMonumentRequest));
        Path path = configPath();
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
        if (requestOceanMonument && !effectiveMonumentRequest) {
            RingWorldMod.LOGGER.warn(
                    "Ignored ocean-monument request: width={} cannot fit its required margins",
                    widthBlocks);
        }
        RingWorldMod.LOGGER.info("Updated first-world RingWorld layout: {}x{}, wallHeight={}, requestOceanMonument={}",
                circumferenceBlocks, widthBlocks, wallHeightBlocks, effectiveMonumentRequest);
        return replacement;
    }

    /** Bootstrap-only admission; saved legacy settings retain 1,024-block structural support. */
    static void validateNewWorldLayout(int widthBlocks, int circumferenceBlocks,
                                       int wallHeightBlocks) {
        validateNewWorldLayout(widthBlocks, circumferenceBlocks, wallHeightBlocks,
                RingWallStyle.DEFAULT.thicknessBlocks());
    }

    static void validateNewWorldLayout(int widthBlocks, int circumferenceBlocks,
                                       int wallHeightBlocks, int rimThicknessBlocks) {
        RingGeometry geometry = new RingGeometry(widthBlocks, circumferenceBlocks);
        if (circumferenceBlocks < RingWorldSettings.MIN_NEW_WORLD_CIRCUMFERENCE) {
            throw new IllegalArgumentException("circumferenceBlocks must be at least "
                    + RingWorldSettings.MIN_NEW_WORLD_CIRCUMFERENCE + " for a new RingWorld");
        }
        RingDimensionReport.evaluate(geometry, wallHeightBlocks,
                RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y,
                RingDimensionReport.VANILLA_OVERWORLD_TOP_Y_EXCLUSIVE,
                rimThicknessBlocks, RingTerrainAtlas.SAMPLE_STEP_BLOCKS).requireValid();
    }

    /** Authoritative new-world request gate shared by UI and server ownership paths. */
    static boolean effectiveOceanMonumentRequest(
            RingGeometry geometry, boolean requested) {
        return requested && RingMonumentPlacement.hasCandidateSpace(geometry);
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static RingWallStyle wallStyle(Properties properties) {
        if (properties.containsKey("wallThicknessBlocks")) {
            int format = integer(properties, "wallStyleFormat", RingWallStyle.FORMAT_VERSION);
            return new RingWallStyle(
                    integer(properties, "wallThicknessBlocks", RingWallStyle.DEFAULT.thicknessBlocks()),
                    RingWallStyle.Palette.fromId(integer(properties, "wallPalette",
                            RingWallStyle.DEFAULT.palette().id())),
                    RingWallStyle.Pattern.fromId(integer(properties, "wallPattern",
                            RingWallStyle.DEFAULT.pattern().id())),
                    integer(properties, "wallDecayPercent", RingWallStyle.DEFAULT.decayPercent()),
                    format);
        }
        String presetName = properties.getProperty(
                "wallPreset", RingWallStyle.Preset.WEATHERED_FORTIFICATION.name());
        try {
            return RingWallStyle.Preset.valueOf(presetName.trim()).style();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("wallPreset must name a supported rim preset", exception);
        }
    }

    private static RingSkyProfile skyProfile(Properties properties) {
        if (properties.containsKey("skyBackdrop") || properties.containsKey("sunStyle")) {
            try {
                RingSkyProfile.Backdrop backdrop = RingSkyProfile.Backdrop.valueOf(
                        properties.getProperty("skyBackdrop", "ATMOSPHERE").trim().toUpperCase(
                                java.util.Locale.ROOT));
                RingSkyProfile.LightSource source = RingSkyProfile.LightSource.valueOf(
                        properties.getProperty("sunStyle", "SMALL").trim().toUpperCase(
                                java.util.Locale.ROOT));
                return new RingSkyProfile(backdrop, source, RingSkyProfile.FORMAT_VERSION);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "skyBackdrop must be ATMOSPHERE, NIGHT, or VOID and sunStyle must be SMALL, LARGE, or NONE",
                        exception);
            }
        }
        return legacySkyProfile(properties.getProperty("skyPreset", "MINECRAFT_ATMOSPHERE"));
    }

    /** Reads the five combined development presets written before sky and sun became independent. */
    private static RingSkyProfile legacySkyProfile(String presetName) {
        try {
            return switch (presetName.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "MINECRAFT_ATMOSPHERE" -> new RingSkyProfile(
                        RingSkyProfile.Backdrop.ATMOSPHERE,
                        RingSkyProfile.LightSource.SMALL, RingSkyProfile.FORMAT_VERSION);
                case "SPACE_HABITAT" -> new RingSkyProfile(
                        RingSkyProfile.Backdrop.NIGHT,
                        RingSkyProfile.LightSource.SMALL, RingSkyProfile.FORMAT_VERSION);
                case "DISTANT_STAR" -> new RingSkyProfile(
                        RingSkyProfile.Backdrop.NIGHT,
                        RingSkyProfile.LightSource.LARGE, RingSkyProfile.FORMAT_VERSION);
                case "NIGHT_HABITAT" -> new RingSkyProfile(
                        RingSkyProfile.Backdrop.NIGHT,
                        RingSkyProfile.LightSource.NONE, RingSkyProfile.FORMAT_VERSION);
                case "MINIMAL_VOID" -> new RingSkyProfile(
                        RingSkyProfile.Backdrop.VOID,
                        RingSkyProfile.LightSource.NONE, RingSkyProfile.FORMAT_VERSION);
                default -> throw new IllegalArgumentException("unknown legacy sky preset");
            };
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("skyPreset must name a supported sky preset", exception);
        }
    }
}
