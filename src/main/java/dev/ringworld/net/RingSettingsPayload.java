package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Immutable geometry sent by the server before the client renders a ring world. */
public record RingSettingsPayload(int width, int circumference, long seed, int formatVersion)
        implements CustomPayload {
    public static final Id<RingSettingsPayload> ID = new Id<>(Identifier.of(RingWorldMod.MOD_ID, "settings"));
    public static final PacketCodec<RegistryByteBuf, RingSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RingSettingsPayload::width,
            PacketCodecs.VAR_INT, RingSettingsPayload::circumference,
            PacketCodecs.LONG, RingSettingsPayload::seed,
            PacketCodecs.VAR_INT, RingSettingsPayload::formatVersion,
            RingSettingsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
