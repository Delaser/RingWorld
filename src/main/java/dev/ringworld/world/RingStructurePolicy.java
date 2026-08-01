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
    public static final int FORMAT_VERSION = 2;
    public static final int GUARANTEE_STRONGHOLD = 1;
    public static final int REQUEST_OCEAN_MONUMENT = 1 << 1;
    public static final Identifier STORAGE_ID =
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "structure_policy");

    private static final Codec<RingStructurePolicy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("guarantees").forGetter(RingStructurePolicy::guarantees),
            Codec.INT.fieldOf("format").forGetter(RingStructurePolicy::formatVersion),
            RingMonumentResolution.CODEC.optionalFieldOf("ocean_monument", RingMonumentResolution.disabled())
                    .forGetter(RingStructurePolicy::oceanMonument)
    ).apply(instance, RingStructurePolicy::new));
    private static final SavedDataType<RingStructurePolicy> TYPE = new SavedDataType<>(
            STORAGE_ID, () -> new RingStructurePolicy(0, FORMAT_VERSION),
            CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final int guarantees;
    private final int formatVersion;
    private final RingMonumentResolution oceanMonument;

    public RingStructurePolicy(int guarantees, int formatVersion) {
        this(guarantees, formatVersion, RingMonumentResolution.disabled());
    }

    public RingStructurePolicy(int guarantees, int formatVersion, RingMonumentResolution oceanMonument) {
        if (formatVersion < 1 || formatVersion > FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported RingWorld structure policy format " + formatVersion);
        }
        if (formatVersion == 1 && (guarantees & REQUEST_OCEAN_MONUMENT) != 0) {
            throw new IllegalArgumentException("structure policy v1 cannot request an ocean monument");
        }
        if (formatVersion == FORMAT_VERSION) {
            boolean requested = (guarantees & REQUEST_OCEAN_MONUMENT) != 0;
            boolean disabled = oceanMonument.status() == RingMonumentResolution.Status.DISABLED;
            if (requested == disabled) {
                throw new IllegalArgumentException(
                        "monument request bit and saved resolution status disagree");
            }
        }
        this.guarantees = guarantees;
        this.formatVersion = formatVersion;
        this.oceanMonument = formatVersion == 1 ? RingMonumentResolution.disabled() : oceanMonument;
    }

    /** Called only in the same ownership path that creates first-world settings. */
    static RingStructurePolicy createForNewWorld(SavedDataStorage storage, boolean requestOceanMonument) {
        RingStructurePolicy existing = storage.get(TYPE);
        if (existing != null) return existing;
        int guarantees = GUARANTEE_STRONGHOLD | (requestOceanMonument ? REQUEST_OCEAN_MONUMENT : 0);
        RingStructurePolicy created = new RingStructurePolicy(guarantees, FORMAT_VERSION,
                requestOceanMonument ? RingMonumentResolution.pending() : RingMonumentResolution.disabled());
        created.setDirty();
        storage.set(TYPE, created);
        return created;
    }

    /** Missing policy means a pre-feature world whose structure layout must remain unchanged. */
    public static RingStructurePolicy get(ServerLevel world) {
        RingStructurePolicy saved = world.getDataStorage().get(TYPE);
        return saved != null ? saved : new RingStructurePolicy(0, FORMAT_VERSION);
    }

    /** Resolves a new-world pending request once; saved outcomes never trigger a new search. */
    public static RingStructurePolicy resolvePendingOceanMonument(
            ServerLevel world, RingMonumentResolution resolution) {
        RingStructurePolicy current = get(world);
        if (!current.requestsOceanMonument() || current.oceanMonument().status()
                != RingMonumentResolution.Status.PENDING) return current;
        if (!resolution.isResolved() || resolution.status() == RingMonumentResolution.Status.DISABLED) {
            throw new IllegalArgumentException("monument resolution must be a terminal requested result");
        }
        RingStructurePolicy resolved = new RingStructurePolicy(current.guarantees(), FORMAT_VERSION, resolution);
        resolved.setDirty();
        world.getDataStorage().set(TYPE, resolved);
        return resolved;
    }

    public int guarantees() {
        return guarantees;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public RingMonumentResolution oceanMonument() { return oceanMonument; }

    public boolean guaranteesStronghold() {
        return (guarantees & GUARANTEE_STRONGHOLD) != 0;
    }

    public boolean requestsOceanMonument() {
        return (guarantees & REQUEST_OCEAN_MONUMENT) != 0;
    }
}
