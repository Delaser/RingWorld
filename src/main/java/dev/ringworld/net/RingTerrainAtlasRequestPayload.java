package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Lets a client reuse a complete atlas cached by world hash. */
public record RingTerrainAtlasRequestPayload(long worldHash, boolean cacheComplete)
        implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasRequestPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_atlas_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, RingTerrainAtlasRequestPayload::worldHash,
            ByteBufCodecs.BOOL, RingTerrainAtlasRequestPayload::cacheComplete,
            RingTerrainAtlasRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
