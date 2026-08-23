package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete immutable layout sent before the client renders a ring world. */
public record RingSettingsPayload(int width, int circumference, long seed, int wallHeight,
                                  int surfaceReferenceY, int terrainNoiseMapping,
                                  int formatVersion, long fingerprint)
        implements CustomPacketPayload {
    /**
     * The channel name is versioned whenever its byte layout changes. Reusing
     * the old identifier makes an old codec consume its known prefix and then
     * crash on the unread fields before either side can explain the mismatch.
     */
    public static final Type<RingSettingsPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "settings_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsPayload> CODEC =
            StreamCodec.of(RingSettingsPayload::encode, RingSettingsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, RingSettingsPayload payload) {
        buffer.writeVarInt(payload.width());
        buffer.writeVarInt(payload.circumference());
        buffer.writeLong(payload.seed());
        buffer.writeVarInt(payload.wallHeight());
        buffer.writeVarInt(payload.surfaceReferenceY());
        buffer.writeVarInt(payload.terrainNoiseMapping());
        buffer.writeVarInt(payload.formatVersion());
        buffer.writeLong(payload.fingerprint());
    }

    private static RingSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RingSettingsPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
