package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client acknowledgement of the independently verified immutable layout. */
public record RingSettingsAckPayload(int formatVersion, long fingerprint)
        implements CustomPacketPayload {
    public static final Type<RingSettingsAckPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "settings_ack_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsAckPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RingSettingsAckPayload::formatVersion,
            ByteBufCodecs.LONG, RingSettingsAckPayload::fingerprint,
            RingSettingsAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
