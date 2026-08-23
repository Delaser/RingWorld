package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Lets a client reuse a complete atlas cached by world hash. */
public record RingTerrainAtlasRequestPayload(long worldHash, long revision, boolean cacheComplete)
        implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasRequestPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_atlas_request_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasRequestPayload> CODEC = StreamCodec.composite(
            RingWireCodecs.LONG, RingTerrainAtlasRequestPayload::worldHash,
            ByteBufCodecs.VAR_LONG, RingTerrainAtlasRequestPayload::revision,
            ByteBufCodecs.BOOL, RingTerrainAtlasRequestPayload::cacheComplete,
            RingTerrainAtlasRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
