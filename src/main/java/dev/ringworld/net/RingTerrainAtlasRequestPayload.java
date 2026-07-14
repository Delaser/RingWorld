package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Lets a client reuse a complete atlas cached by world hash. */
public record RingTerrainAtlasRequestPayload(long worldHash, boolean cacheComplete)
        implements CustomPayload {
    public static final Id<RingTerrainAtlasRequestPayload> ID = new Id<>(
            Identifier.of(RingWorldMod.MOD_ID, "terrain_atlas_request"));
    public static final PacketCodec<RegistryByteBuf, RingTerrainAtlasRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.LONG, RingTerrainAtlasRequestPayload::worldHash,
            PacketCodecs.BOOLEAN, RingTerrainAtlasRequestPayload::cacheComplete,
            RingTerrainAtlasRequestPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
