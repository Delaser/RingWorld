package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
            new StreamCodec<>() {
                @Override
                public RingTerrainAtlasMetadataPayload decode(RegistryFriendlyByteBuf buf) {
                    return new RingTerrainAtlasMetadataPayload(
                            buf.readVarLong(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readVarLong()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RingTerrainAtlasMetadataPayload value) {
                    buf.writeVarLong(value.worldHash());
                    buf.writeVarInt(value.sampleStep());
                    buf.writeVarInt(value.columns());
                    buf.writeVarInt(value.rows());
                    buf.writeVarInt(value.tileSize());
                    buf.writeVarInt(value.presentCells());
                    buf.writeBoolean(value.complete());
                    buf.writeVarLong(value.revision());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
