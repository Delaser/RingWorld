package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Describes the tiled, world-specific distant terrain cache. */
public record RingTerrainAtlasMetadataPayload(long worldHash, int sampleStep, int columns, int rows,
                                              int tileSize, int presentCells, boolean complete)
        implements CustomPayload {
    public static final Id<RingTerrainAtlasMetadataPayload> ID = new Id<>(
            Identifier.of(RingWorldMod.MOD_ID, "terrain_atlas_metadata"));
    public static final PacketCodec<RegistryByteBuf, RingTerrainAtlasMetadataPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.LONG, RingTerrainAtlasMetadataPayload::worldHash,
            PacketCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::sampleStep,
            PacketCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::columns,
            PacketCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::rows,
            PacketCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::tileSize,
            PacketCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::presentCells,
            PacketCodecs.BOOLEAN, RingTerrainAtlasMetadataPayload::complete,
            RingTerrainAtlasMetadataPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
