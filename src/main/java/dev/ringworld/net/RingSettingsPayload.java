package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Complete immutable layout sent before the client renders a ring world. */
public record RingSettingsPayload(int width, int circumference, long seed, int wallHeight,
                                  int surfaceReferenceY, int formatVersion, long fingerprint)
        implements CustomPacketPayload {
    /**
     * The channel name is versioned whenever its byte layout changes. Reusing
     * the old identifier makes an old codec consume its known prefix and then
     * crash on the unread fields before either side can explain the mismatch.
     */
    public static final Type<RingSettingsPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "settings_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RingSettingsPayload::width,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::circumference,
            ByteBufCodecs.LONG, RingSettingsPayload::seed,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::wallHeight,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::surfaceReferenceY,
            ByteBufCodecs.VAR_INT, RingSettingsPayload::formatVersion,
            ByteBufCodecs.LONG, RingSettingsPayload::fingerprint,
            RingSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
