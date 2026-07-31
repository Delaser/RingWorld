package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Describes the tiled, world-specific distant terrain cache. */
public record RingTerrainAtlasMetadataPayload(long worldHash, int sampleStep, int columns, int rows,
                                              int tileSize, int presentCells, boolean complete)
        implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasMetadataPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_atlas_metadata"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasMetadataPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, RingTerrainAtlasMetadataPayload::worldHash,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::sampleStep,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::columns,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::rows,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::tileSize,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasMetadataPayload::presentCells,
            ByteBufCodecs.BOOL, RingTerrainAtlasMetadataPayload::complete,
            RingTerrainAtlasMetadataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
