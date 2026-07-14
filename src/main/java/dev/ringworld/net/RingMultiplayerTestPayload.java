package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Opt-in development result packet used by the two-client seam regression. */
public record RingMultiplayerTestPayload(String role, String phase, boolean passed, double value)
        implements CustomPayload {
    public static final Id<RingMultiplayerTestPayload> ID =
            new Id<>(Identifier.of(RingWorldMod.MOD_ID, "multiplayer_test"));
    public static final PacketCodec<RegistryByteBuf, RingMultiplayerTestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RingMultiplayerTestPayload::role,
            PacketCodecs.STRING, RingMultiplayerTestPayload::phase,
            PacketCodecs.BOOLEAN, RingMultiplayerTestPayload::passed,
            PacketCodecs.DOUBLE, RingMultiplayerTestPayload::value,
            RingMultiplayerTestPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
