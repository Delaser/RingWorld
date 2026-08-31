package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingTerrainPreview;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One coarse seed-derived map used only while real Atlas cells are incomplete. */
public record RingTerrainPreviewPayload(long worldHash, int stage, byte[] data)
        implements CustomPacketPayload {
    public static final Type<RingTerrainPreviewPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_preview_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainPreviewPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.LONG, RingTerrainPreviewPayload::worldHash,
                    ByteBufCodecs.VAR_INT, RingTerrainPreviewPayload::stage,
                    ByteBufCodecs.byteArray(RingTerrainPreview.MAX_COMPRESSED_BYTES),
                    RingTerrainPreviewPayload::data,
                    RingTerrainPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
