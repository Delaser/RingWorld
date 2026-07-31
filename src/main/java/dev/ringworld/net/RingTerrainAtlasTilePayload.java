package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One independently cacheable colour-and-height tile. */
public record RingTerrainAtlasTilePayload(long worldHash, int tileX, int tileZ, byte[] data)
        implements CustomPacketPayload {
    public static final Type<RingTerrainAtlasTilePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_atlas_tile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainAtlasTilePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, RingTerrainAtlasTilePayload::worldHash,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasTilePayload::tileX,
            ByteBufCodecs.VAR_INT, RingTerrainAtlasTilePayload::tileZ,
            ByteBufCodecs.byteArray(4096), RingTerrainAtlasTilePayload::data,
            RingTerrainAtlasTilePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
