package dev.ringworld.world;

import com.mojang.serialization.Codec;
import dev.ringworld.RingWorldMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Mutable player-placement policy, saved independently of immutable terrain identity. */
public final class RingBuildingSettings extends SavedData {
    private static final String STORAGE_KEY = RingWorldMod.MOD_ID + "/building_settings";
    private static final Codec<RingBuildingSettings> CODEC = Codec.BOOL
            .optionalFieldOf("outsideBuilding", true).codec()
            .xmap(RingBuildingSettings::new, RingBuildingSettings::outsideBuilding);
    private static final SavedData.Factory<RingBuildingSettings> FACTORY = new SavedData.Factory<>(
            () -> new RingBuildingSettings(true), RingBuildingSettings::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private boolean outsideBuilding;

    public RingBuildingSettings(boolean enabled) { outsideBuilding = enabled; }
    public boolean outsideBuilding() { return outsideBuilding; }

    public void setOutsideBuilding(boolean enabled) {
        if (outsideBuilding != enabled) { outsideBuilding = enabled; setDirty(); }
    }

    private static RingBuildingSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow(
                () -> new IllegalStateException("Could not decode RingWorld building settings"));
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result()
                .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).orElseThrow(
                        () -> new IllegalStateException("Could not encode RingWorld building settings"));
    }

    public static RingBuildingSettings get(ServerLevel world) {
        var storage = world.getDataStorage();
        RingBuildingSettings saved = RingSavedDataGuard.get(world, FACTORY, STORAGE_KEY);
        if (saved != null) return saved;
        RingBuildingSettings created = new RingBuildingSettings(true);
        created.setDirty();
        storage.set(STORAGE_KEY, created);
        return created;
    }

    static Codec<RingBuildingSettings> codecForTests() { return CODEC; }

    /** Applies to players; automation and already placed blocks retain vanilla behavior. */
    public static boolean allows(boolean enabled, RingGeometry geometry, int z) {
        return enabled || z >= geometry.minWidthZ() && z <= geometry.maxWidthZ();
    }
}
