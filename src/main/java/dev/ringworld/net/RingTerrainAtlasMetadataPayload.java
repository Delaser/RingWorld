package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Describes the tiled, world-specific distant terrain cache. */
public record RingTerrainAtlasMetadataPayload(long worldHash, int sampleStep, int columns, int rows,
                                              int tileSize, int presentCells, boolean complete,
                                              long revision)
        implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasMetadataPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_atlas_metadata_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasMetadataPayload> CODEC =
            StreamCodec.of(RingTerrainAtlasMetadataPayload::encode,
                    RingTerrainAtlasMetadataPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, RingTerrainAtlasMetadataPayload payload) {
        buffer.writeLong(payload.worldHash());
        buffer.writeVarInt(payload.sampleStep());
        buffer.writeVarInt(payload.columns());
        buffer.writeVarInt(payload.rows());
        buffer.writeVarInt(payload.tileSize());
        buffer.writeVarInt(payload.presentCells());
        buffer.writeBoolean(payload.complete());
        buffer.writeVarLong(payload.revision());
    }

    private static RingTerrainAtlasMetadataPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RingTerrainAtlasMetadataPayload(buffer.readLong(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
