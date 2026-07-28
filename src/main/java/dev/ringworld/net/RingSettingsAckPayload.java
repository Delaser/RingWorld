package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client acknowledgement of the independently verified immutable layout. */
public record RingSettingsAckPayload(int formatVersion, long fingerprint)
        implements CustomPayload {
    public static final Id<RingSettingsAckPayload> ID =
            new Id<>(Identifier.of(RingWorldMod.MOD_ID, "settings_ack_v2"));
    public static final PacketCodec<RegistryByteBuf, RingSettingsAckPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RingSettingsAckPayload::formatVersion,
            PacketCodecs.LONG, RingSettingsAckPayload::fingerprint,
            RingSettingsAckPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
