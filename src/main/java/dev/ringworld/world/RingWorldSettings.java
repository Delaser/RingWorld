package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ringworld.RingWorldMod;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Immutable ring dimensions stored alongside a world once it is created.
 */
public final class RingWorldSettings extends SavedData {

    public static final ResourceLocation STORAGE_ID = ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "settings");

    /**
     * 1.21.1 SavedDataStorage uses a string key rather than the newer
     * namespaced SavedDataType system.
     */
    public static final String STORAGE_KEY = RingWorldMod.MOD_ID + "_settings";

    public static final String LEGACY_STORAGE_KEY = RingWorldMod.MOD_ID + "_settings";

    public static final int FORMAT_VERSION = 3;

    public static final int DEFAULT_WIDTH = 256;
    public static final int DEFAULT_CIRCUMFERENCE = 16_384;

    /**
     * 160 blocks from minimum build height:
     * a visible top near Y=96 in vanilla terrain.
     */
    public static final int DEFAULT_WALL_HEIGHT = 160;

    /**
     * Structural geometry/storage minimum;
     * persisted worlds may use this width.
     */
    public static final int MIN_WIDTH = 128;

    /**
     * Bootstrap layouts below this circumference
     * are not offered for new worlds.
     */
    public static final int MIN_NEW_WORLD_CIRCUMFERENCE = 2_048;

    /**
     * Structural loading minimum retained for
     * persisted legacy layouts.
     */
    public static final int MIN_CIRCUMFERENCE = 1_024;

    /*
     * Keep the codec because the project uses it in tests and because
     * it still provides a useful representation of the settings format.
     *
     * Actual 1.21.1 SavedData persistence is handled by load()/save().
     */
    private static final Codec<RingWorldSettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("width").forGetter(RingWorldSettings::widthBlocks),
                    Codec.INT.fieldOf("circumference").forGetter(RingWorldSettings::circumferenceBlocks),
                    Codec.LONG.fieldOf("seed").forGetter(RingWorldSettings::generatorSeed),
                    Codec.INT.fieldOf("wallHeight").forGetter(RingWorldSettings::wallHeightBlocks),
                    Codec.INT.optionalFieldOf("surfaceReferenceY", (int) RingGeometry.SURFACE_Y).forGetter(RingWorldSettings::surfaceReferenceY),
                    Codec.INT.optionalFieldOf("terrainNoiseMapping", RingTerrainNoiseMapping.LEGACY_AXIAL).forGetter(RingWorldSettings::terrainNoiseMapping),
                    Codec.INT.fieldOf("format").forGetter(RingWorldSettings::formatVersion)

            ).apply(instance, RingWorldSettings::new));

    /**
     * 1.21.1 replacement for the newer SavedDataType system.
     */
    private static final SavedData.Factory<RingWorldSettings> FACTORY =
            new SavedData.Factory<>(RingWorldSettings::new, RingWorldSettings::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int widthBlocks;
    private final int circumferenceBlocks;
    private final long generatorSeed;
    private final int wallHeightBlocks;
    private final int surfaceReferenceY;
    private final int terrainNoiseMapping;
    private final int formatVersion;

    public RingWorldSettings() {
        this(RingWorldConfig.load().widthBlocks(), RingWorldConfig.load().circumferenceBlocks(), 0L, RingWorldConfig.load().wallHeightBlocks(), (int) RingGeometry.SURFACE_Y, RingTerrainNoiseMapping.CURRENT, FORMAT_VERSION);
        // This constructor is used only when no saved state exists yet.
        RingWorldConfig.validateNewWorldLayout(widthBlocks, circumferenceBlocks, wallHeightBlocks);
        setDirty();
    }

    public RingWorldSettings(int widthBlocks, int circumferenceBlocks, long generatorSeed, int wallHeightBlocks, int formatVersion) {
        this(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks, (int) RingGeometry.SURFACE_Y, RingTerrainNoiseMapping.forSettingsFormat(formatVersion), formatVersion);
    }

    public RingWorldSettings(int widthBlocks, int circumferenceBlocks, long generatorSeed, int wallHeightBlocks, int surfaceReferenceY, int formatVersion) {
        this(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks, surfaceReferenceY, RingTerrainNoiseMapping.forSettingsFormat(formatVersion), formatVersion);
    }

    public RingWorldSettings(int widthBlocks, int circumferenceBlocks, long generatorSeed, int wallHeightBlocks, int surfaceReferenceY, int terrainNoiseMapping, int formatVersion) {
        new RingGeometry(widthBlocks, circumferenceBlocks);

        if (wallHeightBlocks < 32) {
            throw new IllegalArgumentException("wall height must be at least 32 blocks");
        }

        if (surfaceReferenceY != (int) RingGeometry.SURFACE_Y) {
            throw new IllegalArgumentException("RingWorld settings require surfaceReferenceY=" + (int) RingGeometry.SURFACE_Y);
        }

        if (formatVersion < 1 || formatVersion > FORMAT_VERSION) {throw new IllegalArgumentException("unsupported RingWorld settings format " + formatVersion);
        }

        int supportedMapping = RingTerrainNoiseMapping.requireSupported(terrainNoiseMapping);

        if (formatVersion < 3 && supportedMapping != RingTerrainNoiseMapping.LEGACY_AXIAL) {
            throw new IllegalArgumentException("RingWorld settings before format 3 require " + "legacy terrain-noise mapping");
        }

        this.widthBlocks = widthBlocks;
        this.circumferenceBlocks = circumferenceBlocks;
        this.generatorSeed = generatorSeed;
        this.wallHeightBlocks = wallHeightBlocks;
        this.surfaceReferenceY = surfaceReferenceY;
        this.terrainNoiseMapping = supportedMapping;
        this.formatVersion = formatVersion;
    }

    /**
     * 1.21.1 SavedData deserializer.
     */
    private static RingWorldSettings load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        int width = tag.getInt("width");
        int circumference = tag.getInt("circumference");
        long seed = tag.getLong("seed");
        int wallHeight = tag.getInt("wallHeight");

        int surfaceReferenceY =
                tag.contains("surfaceReferenceY")
                        ? tag.getInt("surfaceReferenceY")
                        : (int) RingGeometry.SURFACE_Y;

        int format =
                tag.contains("format")
                        ? tag.getInt("format")
                        : 1;

        int terrainNoiseMapping =
                tag.contains("terrainNoiseMapping")
                        ? tag.getInt("terrainNoiseMapping")
                        : RingTerrainNoiseMapping.LEGACY_AXIAL;

        return new RingWorldSettings(
                width,
                circumference,
                seed,
                wallHeight,
                surfaceReferenceY,
                terrainNoiseMapping,
                format
        );
    }

    /**
     * 1.21.1 SavedData serializer.
     */
    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        tag.putInt("width", widthBlocks);
        tag.putInt("circumference", circumferenceBlocks);
        tag.putLong("seed", generatorSeed);
        tag.putInt("wallHeight", wallHeightBlocks);
        tag.putInt("surfaceReferenceY", surfaceReferenceY);
        tag.putInt("terrainNoiseMapping", terrainNoiseMapping);
        tag.putInt("format", formatVersion);

        return tag;
    }

    public static RingWorldSettings get(ServerLevel world) {
        DimensionDataStorage manager = world.getDataStorage();

        RingWorldSettings saved =
                manager.get(FACTORY, STORAGE_KEY);

        if (saved != null) {
            if (saved.formatVersion() == FORMAT_VERSION) {
                return saved;
            }

            RingWorldSettings upgraded =
                    upgradeToCurrentFormat(saved);

            upgraded.setDirty();

            manager.set(
                    STORAGE_KEY,
                    upgraded
            );

            RingWorldMod.LOGGER.info(
                    "Migrated RingWorld settings format {} to {} for {}x{} world",
                    saved.formatVersion(),
                    FORMAT_VERSION,
                    saved.circumferenceBlocks(),
                    saved.widthBlocks()
            );

            return upgraded;
        }

        if (hasExistingOverworldRegions(world)) {
            throw new IllegalStateException("This Overworld already contains region files but has " + "no RingWorld settings. Existing flat worlds " + "cannot be converted in place."
            );
        }

        RingWorldConfig config = RingWorldConfig.load();

        RingWorldConfig.validateNewWorldLayout(config.widthBlocks(), config.circumferenceBlocks(), config.wallHeightBlocks());

        RingDimensionReport report =
                RingDimensionReport.forVanillaOverworld(
                        new RingGeometry(config.widthBlocks(), config.circumferenceBlocks()),
                        config.wallHeightBlocks()
                );

        RingWorldSettings created = new RingWorldSettings(
                        config.widthBlocks(),
                        config.circumferenceBlocks(),
                        world.getSeed(),
                        config.wallHeightBlocks(),
                        (int) RingGeometry.SURFACE_Y,
                        RingTerrainNoiseMapping.CURRENT, FORMAT_VERSION
                );

        created.setDirty();

        manager.set(STORAGE_KEY, created);

        boolean monumentRequest = RingWorldConfig.effectiveOceanMonumentRequest(report.geometry(), config.requestOceanMonument());

        if (config.requestOceanMonument() && !monumentRequest) {
            RingWorldMod.LOGGER.warn("Disabled ocean-monument request: width={} " + "cannot fit its required margins", config.widthBlocks());
        }

        RingStructurePolicy.createForNewWorld(manager, monumentRequest);

        RingWorldMod.LOGGER.info(
                "Created RingWorld layout: {}x{} blocks " + "({}x{} chunks), radius={}, centreY={}, " + "wallTopY={}, cloudBaseY={}, atlasCells={}, " + "terrainNoiseMapping={}",
                created.circumferenceBlocks(),
                created.widthBlocks(),
                report.geometry().circumferenceChunks(),
                report.geometry().widthChunks(),
                String.format(java.util.Locale.ROOT, "%.2f", report.geometry().radius()),
                String.format(java.util.Locale.ROOT, "%.2f", report.geometry().physicalCenterY()),
                report.wallTopYExclusive(), report.cloudBaseY(), report.atlasCellCount(), created.terrainNoiseMapping()
        );

        report.warnings().forEach(warning -> RingWorldMod.LOGGER.warn("RingWorld layout warning: {}", warning)
        );

        return created;
    }

    static RingWorldSettings upgradeToCurrentFormat(RingWorldSettings saved) {
        if (saved.formatVersion() == FORMAT_VERSION) {
            return saved;
        }
        return new RingWorldSettings(saved.widthBlocks(), saved.circumferenceBlocks(), saved.generatorSeed(), saved.wallHeightBlocks(), saved.surfaceReferenceY(), saved.terrainNoiseMapping(), FORMAT_VERSION);
    }

    static Codec<RingWorldSettings> codecForTests() {
        return CODEC;
    }

    public int widthBlocks() {
        return widthBlocks;
    }

    public int circumferenceBlocks() {
        return circumferenceBlocks;
    }

    public long generatorSeed() {
        return generatorSeed;
    }

    public int wallHeightBlocks() {
        return wallHeightBlocks;
    }

    public int surfaceReferenceY() {
        return surfaceReferenceY;
    }

    public int terrainNoiseMapping() {
        return terrainNoiseMapping;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public long layoutFingerprint() {
        return RingLayoutFingerprint.compute(this);
    }

    public RingGeometry geometry() {
        return new RingGeometry(widthBlocks, circumferenceBlocks);
    }

    private static boolean hasExistingOverworldRegions(
            ServerLevel world
    ) {
        Path regionDirectory = RingWorldStorageAccess.dimensionPath(world).resolve("region");
        if (!Files.isDirectory(regionDirectory)) {
            return false;
        }

        try (var files = Files.list(regionDirectory)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".mca"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect existing Overworld region directory " + regionDirectory, exception);
        }
    }

    /*
     * The newer version stores settings under:
     *
     *     data/ringworld/settings.dat
     *
     * 1.21.1 SavedDataStorage instead uses the flat STORAGE_KEY:
     *
     *     data/ringworld_settings.dat
     *
     * Therefore the newer migration method is intentionally not used here.
     */

    static Path settingsPath(Path dimensionPath) {
        return dimensionPath.resolve("data").resolve(STORAGE_KEY + ".dat");
    }

    static Path legacySettingsPath(Path worldRoot) {
        return worldRoot.resolve("data").resolve(LEGACY_STORAGE_KEY + ".dat");
    }

    static boolean copyAtomicallyIfAbsent(Path source, Path destination) throws IOException {
        if (Files.exists(destination)) {
            return false;
        }

        Files.createDirectories(destination.getParent());

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");

        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);

        if (Files.exists(destination)) {Files.deleteIfExists(temporary);
            return false;
        }

        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            try {
                Files.move(temporary, destination);
            } catch (FileAlreadyExistsException race) {
                Files.deleteIfExists(temporary);
                return false;
            }
        } catch (FileAlreadyExistsException race) {
            Files.deleteIfExists(temporary);
            return false;
        }

        return true;
    }
}