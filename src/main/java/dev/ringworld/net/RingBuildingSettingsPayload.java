package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative mutable policy and permission snapshot for one layout. */
public record RingBuildingSettingsPayload(long fingerprint, boolean enabled, boolean canControl)
        implements CustomPacketPayload {
    public static final Type<RingBuildingSettingsPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "building_settings_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingBuildingSettingsPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.LONG, RingBuildingSettingsPayload::fingerprint,
                    ByteBufCodecs.BOOL, RingBuildingSettingsPayload::enabled,
                    ByteBufCodecs.BOOL, RingBuildingSettingsPayload::canControl,
                    RingBuildingSettingsPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
