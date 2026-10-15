package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** UI request; the server independently validates authority and world identity. */
public record RingBuildingControlPayload(long fingerprint, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<RingBuildingControlPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "building_control_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingBuildingControlPayload> CODEC =
            StreamCodec.composite(RingWireCodecs.LONG, RingBuildingControlPayload::fingerprint,
                    ByteBufCodecs.BOOL, RingBuildingControlPayload::enabled,
                    RingBuildingControlPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
