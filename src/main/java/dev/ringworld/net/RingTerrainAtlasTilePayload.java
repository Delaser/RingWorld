package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One independently cacheable colour-and-height tile. */
public record RingTerrainAtlasTilePayload(long worldHash, int tileX, int tileZ, byte[] data)
        implements CustomPayload {
    public static final Id<RingTerrainAtlasTilePayload> ID = new Id<>(
            Identifier.of(RingWorldMod.MOD_ID, "terrain_atlas_tile"));
    public static final PacketCodec<RegistryByteBuf, RingTerrainAtlasTilePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.LONG, RingTerrainAtlasTilePayload::worldHash,
            PacketCodecs.VAR_INT, RingTerrainAtlasTilePayload::tileX,
            PacketCodecs.VAR_INT, RingTerrainAtlasTilePayload::tileZ,
            PacketCodecs.byteArray(4096), RingTerrainAtlasTilePayload::data,
            RingTerrainAtlasTilePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
