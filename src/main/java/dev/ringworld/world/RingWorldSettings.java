package dev.ringworld.world;

import dev.ringworld.RingWorldMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

/** Immutable ring dimensions stored alongside a world once it is created. */
public final class RingWorldSettings extends PersistentState {
    public static final String STORAGE_KEY = RingWorldMod.MOD_ID + "_settings";
    public static final int FORMAT_VERSION = 1;
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
            Codec.INT.fieldOf("format").forGetter(RingWorldSettings::formatVersion)
    ).apply(instance, RingWorldSettings::new));
    private static final PersistentStateType<RingWorldSettings> TYPE = new PersistentStateType<>(
            STORAGE_KEY, RingWorldSettings::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int widthBlocks;
    private final int circumferenceBlocks;
    private final long generatorSeed;
    private final int wallHeightBlocks;
    private final int formatVersion;

    public RingWorldSettings() {
        this(RingWorldConfig.load().widthBlocks(), RingWorldConfig.load().circumferenceBlocks(),
                0L, RingWorldConfig.load().wallHeightBlocks(), FORMAT_VERSION);
        // This constructor is used only when no saved state exists yet.
        markDirty();
    }

    public RingWorldSettings(int widthBlocks, int circumferenceBlocks, long generatorSeed, int wallHeightBlocks, int formatVersion) {
        new RingGeometry(widthBlocks, circumferenceBlocks);
        if (wallHeightBlocks < 32) throw new IllegalArgumentException("wall height must be at least 32 blocks");
        this.widthBlocks = widthBlocks;
        this.circumferenceBlocks = circumferenceBlocks;
        this.generatorSeed = generatorSeed;
        this.wallHeightBlocks = wallHeightBlocks;
        this.formatVersion = formatVersion;
    }

    public static RingWorldSettings get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        RingWorldSettings saved = manager.get(TYPE);
        if (saved != null) return saved;

        RingWorldConfig config = RingWorldConfig.load();
        RingWorldSettings created = new RingWorldSettings(
                config.widthBlocks(), config.circumferenceBlocks(), world.getSeed(),
                config.wallHeightBlocks(), FORMAT_VERSION);
        created.markDirty();
        manager.set(TYPE, created);
        return created;
    }

    public int widthBlocks() { return widthBlocks; }
    public int circumferenceBlocks() { return circumferenceBlocks; }
    public long generatorSeed() { return generatorSeed; }
    public int wallHeightBlocks() { return wallHeightBlocks; }
    public int formatVersion() { return formatVersion; }
    public RingGeometry geometry() { return new RingGeometry(widthBlocks, circumferenceBlocks); }
}
