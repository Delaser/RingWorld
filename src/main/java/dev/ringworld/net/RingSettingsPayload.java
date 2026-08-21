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
            new StreamCodec<>() {
                @Override
                public RingSettingsPayload decode(RegistryFriendlyByteBuf buf) {
                    return new RingSettingsPayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarLong(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarLong()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RingSettingsPayload value) {
                    buf.writeVarInt(value.width());
                    buf.writeVarInt(value.circumference());
                    buf.writeVarLong(value.seed());
                    buf.writeVarInt(value.wallHeight());
                    buf.writeVarInt(value.surfaceReferenceY());
                    buf.writeVarInt(value.terrainNoiseMapping());
                    buf.writeVarInt(value.formatVersion());
                    buf.writeVarLong(value.fingerprint());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
