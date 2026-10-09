package dev.ringworld.world;

import com.mojang.serialization.Codec;
import dev.ringworld.RingWorldMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Mutable placement policy saved separately from immutable terrain identity. */
public final class RingBuildingSettings extends SavedData {
    private static final Codec<RingBuildingSettings> CODEC = Codec.BOOL
            .optionalFieldOf("outsideBuilding", true).codec()
            .xmap(RingBuildingSettings::new, RingBuildingSettings::outsideBuilding);
    private static final SavedDataType<RingBuildingSettings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "building_settings"),
            () -> new RingBuildingSettings(true), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private boolean outsideBuilding;

    public RingBuildingSettings(boolean enabled) { outsideBuilding = enabled; }
    public boolean outsideBuilding() { return outsideBuilding; }
    public void setOutsideBuilding(boolean enabled) {
        if (outsideBuilding != enabled) { outsideBuilding = enabled; setDirty(); }
    }
    public static RingBuildingSettings get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }
    static Codec<RingBuildingSettings> codecForTests() { return CODEC; }

    /** Applies to player placement only; automation and existing blocks are unaffected. */
    public static boolean allows(boolean enabled, RingGeometry geometry, int z) {
        return enabled || z >= geometry.minWidthZ() && z <= geometry.maxWidthZ();
    }
}
