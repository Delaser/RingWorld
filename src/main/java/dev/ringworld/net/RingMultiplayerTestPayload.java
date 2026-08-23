package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opt-in development result packet used by the two-client seam regression. */
public record RingMultiplayerTestPayload(String role, String phase, boolean passed, double value)
        implements CustomPacketPayload {
    public static final Type<RingMultiplayerTestPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "multiplayer_test"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingMultiplayerTestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RingMultiplayerTestPayload::role,
            ByteBufCodecs.STRING_UTF8, RingMultiplayerTestPayload::phase,
            ByteBufCodecs.BOOL, RingMultiplayerTestPayload::passed,
            ByteBufCodecs.DOUBLE, RingMultiplayerTestPayload::value,
            RingMultiplayerTestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
