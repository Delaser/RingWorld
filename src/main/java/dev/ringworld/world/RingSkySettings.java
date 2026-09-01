package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ringworld.RingWorldMod;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Saved presentation settings; deliberately separate from terrain/layout identity. */
public final class RingSkySettings extends SavedData {
    public static final ResourceLocation STORAGE_ID =
            ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "sky_settings");
    private static final String STORAGE_KEY = STORAGE_ID.getNamespace() + "/" + STORAGE_ID.getPath();
    private static final Codec<RingSkySettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RingSkyProfile.CODEC.fieldOf("profile").forGetter(RingSkySettings::profile)
    ).apply(instance, RingSkySettings::new));
    private static final SavedData.Factory<RingSkySettings> FACTORY = new SavedData.Factory<>(
            () -> new RingSkySettings(RingSkyProfile.DEFAULT), RingSkySettings::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final RingSkyProfile profile;

    public RingSkySettings(RingSkyProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    private static RingSkySettings load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow(
                () -> new IllegalStateException("Could not decode saved RingWorld sky settings"));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .orElseThrow(() -> new IllegalStateException("Could not encode RingWorld sky settings"));
    }

    public RingSkyProfile profile() { return profile; }

    public static RingSkySettings get(ServerLevel world) {
        DimensionDataStorage storage = world.getDataStorage();
        RingSkySettings saved = storage.get(FACTORY, STORAGE_KEY);
        if (saved != null) return saved;
        RingSkySettings fallback = new RingSkySettings(RingSkyProfile.DEFAULT);
        fallback.setDirty();
        storage.set(STORAGE_KEY, fallback);
        return fallback;
    }

    public static RingSkySettings setProfile(ServerLevel world, RingSkyProfile profile) {
        RingSkySettings replacement = new RingSkySettings(profile);
        replacement.setDirty();
        world.getDataStorage().set(STORAGE_KEY, replacement);
        return replacement;
    }

    static RingSkySettings createForNewWorld(
            DimensionDataStorage storage, RingSkyProfile profile) {
        RingSkySettings created = new RingSkySettings(profile);
        created.setDirty();
        storage.set(STORAGE_KEY, created);
        return created;
    }

    static Codec<RingSkySettings> codecForTests() { return CODEC; }
}
