package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ringworld.RingWorldMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Immutable server-side structure guarantees, separate from the geometry wire format. */
public final class RingStructurePolicy extends SavedData {
    public static final int FORMAT_VERSION = 1;
    public static final int GUARANTEE_STRONGHOLD = 1;
    public static final Identifier STORAGE_ID =
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "structure_policy");

    private static final Codec<RingStructurePolicy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("guarantees").forGetter(RingStructurePolicy::guarantees),
            Codec.INT.fieldOf("format").forGetter(RingStructurePolicy::formatVersion)
    ).apply(instance, RingStructurePolicy::new));
    private static final SavedDataType<RingStructurePolicy> TYPE = new SavedDataType<>(
            STORAGE_ID, () -> new RingStructurePolicy(0, FORMAT_VERSION),
            CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int guarantees;
    private final int formatVersion;

    public RingStructurePolicy(int guarantees, int formatVersion) {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported RingWorld structure policy format " + formatVersion);
        }
        this.guarantees = guarantees;
        this.formatVersion = formatVersion;
    }

    /** Called only in the same ownership path that creates first-world settings. */
    static RingStructurePolicy createForNewWorld(SavedDataStorage storage) {
        RingStructurePolicy existing = storage.get(TYPE);
        if (existing != null) return existing;
        RingStructurePolicy created = new RingStructurePolicy(GUARANTEE_STRONGHOLD, FORMAT_VERSION);
        created.setDirty();
        storage.set(TYPE, created);
        return created;
    }

    /** Missing policy means a pre-feature world whose structure layout must remain unchanged. */
    public static RingStructurePolicy get(ServerLevel world) {
        RingStructurePolicy saved = world.getDataStorage().get(TYPE);
        return saved != null ? saved : new RingStructurePolicy(0, FORMAT_VERSION);
    }

    public int guarantees() {
        return guarantees;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public boolean guaranteesStronghold() {
        return (guarantees & GUARANTEE_STRONGHOLD) != 0;
    }
}
