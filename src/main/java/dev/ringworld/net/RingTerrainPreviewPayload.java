package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import java.io.IOException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One coarse seed-derived map used only while real Atlas cells are incomplete. */
public record RingTerrainPreviewPayload(long worldHash, int stage, byte[] data)
        implements CustomPacketPayload {
    public static final Type<RingTerrainPreviewPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "terrain_preview_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingTerrainPreviewPayload> CODEC =
            StreamCodec.composite(
                    RingWireCodecs.LONG, RingTerrainPreviewPayload::worldHash,
                    ByteBufCodecs.VAR_INT, RingTerrainPreviewPayload::stage,
                    ByteBufCodecs.byteArray(RingTerrainPreview.MAX_COMPRESSED_BYTES),
                    RingTerrainPreviewPayload::data,
                    RingTerrainPreviewPayload::new);

    public RingTerrainPreviewPayload {
        RingTerrainPreviewStage.fromWireValue(stage);
        if (data == null) throw new IllegalArgumentException("terrain-preview data is required");
        if (data.length == 0 || data.length > RingTerrainPreview.MAX_COMPRESSED_BYTES) {
            throw new IllegalArgumentException("invalid terrain-preview payload size");
        }
        try {
            RingTerrainPreview preview = RingTerrainPreview.decode(data);
            if (preview.worldHash() != worldHash) {
                throw new IllegalArgumentException("terrain-preview world hash mismatch");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid terrain-preview body", exception);
        }
        data = data.clone();
    }

    @Override
    public byte[] data() { return data.clone(); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
