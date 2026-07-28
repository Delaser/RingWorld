package dev.ringworld.world;

import dev.ringworld.RingWorldMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable ring dimensions stored alongside a world once it is created. */
public final class RingWorldSettings extends PersistentState {
    public static final String STORAGE_KEY = RingWorldMod.MOD_ID + "_settings";
    public static final int FORMAT_VERSION = 2;
    public static final int DEFAULT_WIDTH = 4_096;
    public static final int DEFAULT_CIRCUMFERENCE = 15_552;
    /** 160 blocks from minimum build height: a visible top near Y=96 in vanilla terrain. */
    public static final int DEFAULT_WALL_HEIGHT = 160;
    public static final int MIN_WIDTH = 256;
    public static final int MIN_CIRCUMFERENCE = 1_024;
    private static final Codec<RingWorldSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("width").forGetter(RingWorldSettings::widthBlocks),
            Codec.INT.fieldOf("circumference").forGetter(RingWorldSettings::circumferenceBlocks),
            Codec.LONG.fieldOf("seed").forGetter(RingWorldSettings::generatorSeed),
            Codec.INT.fieldOf("wallHeight").forGetter(RingWorldSettings::wallHeightBlocks),
            Codec.INT.optionalFieldOf("surfaceReferenceY", (int)RingGeometry.SURFACE_Y)
                    .forGetter(RingWorldSettings::surfaceReferenceY),
            Codec.INT.fieldOf("format").forGetter(RingWorldSettings::formatVersion)
    ).apply(instance, RingWorldSettings::new));
    private static final PersistentStateType<RingWorldSettings> TYPE = new PersistentStateType<>(
            STORAGE_KEY, RingWorldSettings::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int widthBlocks;
    private final int circumferenceBlocks;
    private final long generatorSeed;
    private final int wallHeightBlocks;
    private final int surfaceReferenceY;
    private final int formatVersion;

    public RingWorldSettings() {
        this(RingWorldConfig.load().widthBlocks(), RingWorldConfig.load().circumferenceBlocks(),
                0L, RingWorldConfig.load().wallHeightBlocks(),
                (int)RingGeometry.SURFACE_Y, FORMAT_VERSION);
        // This constructor is used only when no saved state exists yet.
        RingDimensionReport.forVanillaOverworld(geometry(), wallHeightBlocks).requireValid();
        markDirty();
    }

    public RingWorldSettings(int widthBlocks, int circumferenceBlocks, long generatorSeed, int wallHeightBlocks, int formatVersion) {
        this(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks,
                (int)RingGeometry.SURFACE_Y, formatVersion);
    }

    public RingWorldSettings(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                             int wallHeightBlocks, int surfaceReferenceY, int formatVersion) {
        new RingGeometry(widthBlocks, circumferenceBlocks);
        if (wallHeightBlocks < 32) throw new IllegalArgumentException("wall height must be at least 32 blocks");
        if (surfaceReferenceY != (int)RingGeometry.SURFACE_Y) {
            throw new IllegalArgumentException("settings format 2 requires surfaceReferenceY="
                    + (int)RingGeometry.SURFACE_Y);
        }
        if (formatVersion < 1 || formatVersion > FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported RingWorld settings format " + formatVersion);
        }
        this.widthBlocks = widthBlocks;
        this.circumferenceBlocks = circumferenceBlocks;
        this.generatorSeed = generatorSeed;
        this.wallHeightBlocks = wallHeightBlocks;
        this.surfaceReferenceY = surfaceReferenceY;
        this.formatVersion = formatVersion;
    }

    public static RingWorldSettings get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        RingWorldSettings saved = manager.get(TYPE);
        if (saved != null) {
            if (saved.formatVersion() == FORMAT_VERSION) return saved;
            RingWorldSettings upgraded = new RingWorldSettings(
                    saved.widthBlocks(), saved.circumferenceBlocks(), saved.generatorSeed(),
                    saved.wallHeightBlocks(), saved.surfaceReferenceY(), FORMAT_VERSION);
            upgraded.markDirty();
            manager.set(TYPE, upgraded);
            RingWorldMod.LOGGER.info("Migrated RingWorld settings format {} to {} for {}x{} world",
                    saved.formatVersion(), FORMAT_VERSION,
                    saved.circumferenceBlocks(), saved.widthBlocks());
            return upgraded;
        }
        if (hasExistingOverworldRegions(world)) {
            throw new IllegalStateException(
                    "This Overworld already contains region files but has no RingWorld settings. "
                            + "Existing flat worlds cannot be converted in place.");
        }

        RingWorldConfig config = RingWorldConfig.load();
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(config.widthBlocks(), config.circumferenceBlocks()),
                config.wallHeightBlocks());
        report.requireValid();
        RingWorldSettings created = new RingWorldSettings(
                config.widthBlocks(), config.circumferenceBlocks(), world.getSeed(),
                config.wallHeightBlocks(), (int)RingGeometry.SURFACE_Y, FORMAT_VERSION);
        created.markDirty();
        manager.set(TYPE, created);
        RingWorldMod.LOGGER.info(
                "Created RingWorld layout: {}x{} blocks ({}x{} chunks), radius={}, centreY={}, "
                        + "wallTopY={}, cloudBaseY={}, atlasCells={}",
                created.circumferenceBlocks(), created.widthBlocks(),
                report.geometry().circumferenceChunks(), report.geometry().widthChunks(),
                String.format(java.util.Locale.ROOT, "%.2f", report.geometry().radius()),
                String.format(java.util.Locale.ROOT, "%.2f", report.geometry().physicalCenterY()),
                report.wallTopYExclusive(), report.cloudBaseY(), report.atlasCellCount());
        report.warnings().forEach(warning ->
                RingWorldMod.LOGGER.warn("RingWorld layout warning: {}", warning));
        return created;
    }

    public int widthBlocks() { return widthBlocks; }
    public int circumferenceBlocks() { return circumferenceBlocks; }
    public long generatorSeed() { return generatorSeed; }
    public int wallHeightBlocks() { return wallHeightBlocks; }
    public int surfaceReferenceY() { return surfaceReferenceY; }
    public int formatVersion() { return formatVersion; }
    public long layoutFingerprint() { return RingLayoutFingerprint.compute(this); }
    public RingGeometry geometry() { return new RingGeometry(widthBlocks, circumferenceBlocks); }

    private static boolean hasExistingOverworldRegions(ServerWorld world) {
        Path regionDirectory = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("region");
        if (!Files.isDirectory(regionDirectory)) return false;
        try (var files = Files.list(regionDirectory)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".mca"));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not inspect existing Overworld region directory " + regionDirectory,
                    exception);
        }
    }
}
