package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete immutable layout sent before the client renders a ring world. */
public record RingSettingsPayload(int width, int circumference, long seed, int wallHeight,
                                  int surfaceReferenceY, int terrainNoiseMapping,
                                  RingWallStyle wallStyle, RingSkyProfile skyProfile,
                                  int formatVersion, long fingerprint)
        implements CustomPacketPayload {
    /**
     * The channel name is versioned whenever its byte layout changes. Reusing
     * the old identifier makes an old codec consume its known prefix and then
     * crash on the unread fields before either side can explain the mismatch.
     */
    public static final Type<RingSettingsPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "settings_v5"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsPayload> CODEC =
            StreamCodec.of(RingSettingsPayload::encode, RingSettingsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, RingSettingsPayload payload) {
        buffer.writeVarInt(payload.width());
        buffer.writeVarInt(payload.circumference());
        buffer.writeLong(payload.seed());
        buffer.writeVarInt(payload.wallHeight());
        buffer.writeVarInt(payload.surfaceReferenceY());
        buffer.writeVarInt(payload.terrainNoiseMapping());
        RingWallStyle wallStyle = payload.wallStyle();
        buffer.writeVarInt(wallStyle.thicknessBlocks());
        buffer.writeVarInt(wallStyle.palette().id());
        buffer.writeVarInt(wallStyle.pattern().id());
        buffer.writeVarInt(wallStyle.decayPercent());
        buffer.writeVarInt(wallStyle.formatVersion());
        RingSkyProfile skyProfile = payload.skyProfile();
        buffer.writeVarInt(skyProfile.backdrop().id());
        buffer.writeVarInt(skyProfile.lightSource().id());
        buffer.writeVarInt(skyProfile.formatVersion());
        buffer.writeVarInt(payload.formatVersion());
        buffer.writeLong(payload.fingerprint());
    }

    private static RingSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
        int width = buffer.readVarInt();
        int circumference = buffer.readVarInt();
        long seed = buffer.readLong();
        int wallHeight = buffer.readVarInt();
        int surfaceReferenceY = buffer.readVarInt();
        int terrainNoiseMapping = buffer.readVarInt();
        RingWallStyle wallStyle = new RingWallStyle(
                buffer.readVarInt(), RingWallStyle.Palette.fromId(buffer.readVarInt()),
                RingWallStyle.Pattern.fromId(buffer.readVarInt()), buffer.readVarInt(),
                buffer.readVarInt());
        RingSkyProfile skyProfile = new RingSkyProfile(
                RingSkyProfile.Backdrop.fromId(buffer.readVarInt()),
                RingSkyProfile.LightSource.fromId(buffer.readVarInt()),
                buffer.readVarInt());
        return new RingSettingsPayload(width, circumference, seed, wallHeight,
                surfaceReferenceY, terrainNoiseMapping, wallStyle, skyProfile,
                buffer.readVarInt(), buffer.readLong());
    }

    public RingSettingsPayload {
        if (wallStyle == null) throw new IllegalArgumentException("wall style is required");
        if (skyProfile == null) throw new IllegalArgumentException("sky profile is required");
        // Reuse the persisted settings contract so malformed wire geometry,
        // mapping, style, or format values fail before client state can see them.
        new RingWorldSettings(width, circumference, seed, wallHeight, surfaceReferenceY,
                terrainNoiseMapping, wallStyle, formatVersion);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
