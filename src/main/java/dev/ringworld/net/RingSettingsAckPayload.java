package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client acknowledgement that it installed the server's immutable geometry. */
public record RingSettingsAckPayload(int width, int circumference, int formatVersion)
        implements CustomPayload {
    public static final Id<RingSettingsAckPayload> ID =
            new Id<>(Identifier.of(RingWorldMod.MOD_ID, "settings_ack"));
    public static final PacketCodec<RegistryByteBuf, RingSettingsAckPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RingSettingsAckPayload::width,
            PacketCodecs.VAR_INT, RingSettingsAckPayload::circumference,
            PacketCodecs.VAR_INT, RingSettingsAckPayload::formatVersion,
            RingSettingsAckPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
