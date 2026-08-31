package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingAtlasFidelity;
import dev.ringworld.world.RingWorldGenerationSettings;
import dev.ringworld.world.RingWorldLayout;

/** Complete immutable layout sent before the client renders a ring world. */
public record RingSettingsPayload(int width, int circumference, long seed, int wallHeight,
                                  int surfaceReferenceY, int terrainNoiseMapping,
                                  RingWallStyle wallStyle, RingSkyProfile skyProfile,
                                  RingWorldGenerationSettings generationSettings,
                                  int formatVersion, long fingerprint)
        implements CustomPacketPayload {
    /** Source-compatible constructor for tests and pre-format-5 default layouts. */
    public RingSettingsPayload(int width, int circumference, long seed, int wallHeight,
                               int surfaceReferenceY, int terrainNoiseMapping,
                               RingWallStyle wallStyle, RingSkyProfile skyProfile,
                               int formatVersion, long fingerprint) {
        this(width, circumference, seed, wallHeight, surfaceReferenceY, terrainNoiseMapping,
                wallStyle, skyProfile, RingWorldGenerationSettings.DEFAULT,
                formatVersion, fingerprint);
    }

    /**
     * The channel name is versioned whenever its byte layout changes. Reusing
     * the old identifier makes an old codec consume its known prefix and then
     * crash on the unread fields before either side can explain the mismatch.
     */
    public static final Type<RingSettingsPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "settings_v6"));
    private static final StreamCodec<RegistryFriendlyByteBuf, RingWallStyle> WALL_STYLE_CODEC =
            StreamCodec.ofMember((style, buffer) -> {
                buffer.writeVarInt(style.thicknessBlocks());
                buffer.writeVarInt(style.palette().id());
                buffer.writeVarInt(style.pattern().id());
                buffer.writeVarInt(style.decayPercent());
                buffer.writeVarInt(style.formatVersion());
            }, buffer -> new RingWallStyle(
                    buffer.readVarInt(), RingWallStyle.Palette.fromId(buffer.readVarInt()),
                    RingWallStyle.Pattern.fromId(buffer.readVarInt()), buffer.readVarInt(),
                    buffer.readVarInt()));
    private static final StreamCodec<RegistryFriendlyByteBuf, RingSkyProfile> SKY_PROFILE_CODEC =
            StreamCodec.ofMember((profile, buffer) -> {
                buffer.writeVarInt(profile.backdrop().id());
                buffer.writeVarInt(profile.lightSource().id());
                buffer.writeVarInt(profile.formatVersion());
            }, buffer -> new RingSkyProfile(
                    RingSkyProfile.Backdrop.fromId(buffer.readVarInt()),
                    RingSkyProfile.LightSource.fromId(buffer.readVarInt()),
                    buffer.readVarInt()));
    private static final StreamCodec<RegistryFriendlyByteBuf, RingWorldGenerationSettings>
            GENERATION_SETTINGS_CODEC = StreamCodec.ofMember((settings, buffer) -> {
                buffer.writeVarInt(settings.atlasFidelity().id());
                buffer.writeVarInt(settings.layout().id());
                buffer.writeBoolean(settings.continuousRiver());
                buffer.writeBoolean(settings.moreStructures());
                buffer.writeVarInt(settings.formatVersion());
            }, buffer -> new RingWorldGenerationSettings(
                    RingAtlasFidelity.fromId(buffer.readVarInt()),
                    RingWorldLayout.fromId(buffer.readVarInt()),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt()));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RingSettingsPayload::width,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::circumference,
            ByteBufCodecs.LONG, RingSettingsPayload::seed,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::wallHeight,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::surfaceReferenceY,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::terrainNoiseMapping,
            WALL_STYLE_CODEC, RingSettingsPayload::wallStyle,
            SKY_PROFILE_CODEC, RingSettingsPayload::skyProfile,
            GENERATION_SETTINGS_CODEC, RingSettingsPayload::generationSettings,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::formatVersion,
            ByteBufCodecs.LONG, RingSettingsPayload::fingerprint,
            RingSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
