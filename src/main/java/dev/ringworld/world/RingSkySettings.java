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

/** Saved presentation settings; deliberately separate from terrain/layout identity. */
public final class RingSkySettings extends SavedData {
    public static final Identifier STORAGE_ID =
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "sky_settings");
    private static final Codec<RingSkySettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RingSkyProfile.CODEC.fieldOf("profile").forGetter(RingSkySettings::profile)
    ).apply(instance, RingSkySettings::new));
    private static final SavedDataType<RingSkySettings> TYPE = new SavedDataType<>(
            STORAGE_ID, () -> new RingSkySettings(RingSkyProfile.DEFAULT), CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final RingSkyProfile profile;

    public RingSkySettings(RingSkyProfile profile) {
        this.profile = java.util.Objects.requireNonNull(profile, "profile");
    }

    public RingSkyProfile profile() { return profile; }

    public static RingSkySettings get(ServerLevel world) {
        SavedDataStorage storage = world.getDataStorage();
        RingSkySettings saved = storage.get(TYPE);
        if (saved != null) return saved;
        RingSkySettings fallback = new RingSkySettings(RingSkyProfile.DEFAULT);
        fallback.setDirty();
        storage.set(TYPE, fallback);
        return fallback;
    }

    public static RingSkySettings setProfile(ServerLevel world, RingSkyProfile profile) {
        RingSkySettings replacement = new RingSkySettings(profile);
        replacement.setDirty();
        world.getDataStorage().set(TYPE, replacement);
        return replacement;
    }

    static RingSkySettings createForNewWorld(
            SavedDataStorage storage, RingSkyProfile profile) {
        RingSkySettings created = new RingSkySettings(profile);
        created.setDirty();
        storage.set(TYPE, created);
        return created;
    }

    static Codec<RingSkySettings> codecForTests() { return CODEC; }
}
