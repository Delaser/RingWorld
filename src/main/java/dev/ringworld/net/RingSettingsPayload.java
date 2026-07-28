package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Complete immutable layout sent before the client renders a ring world. */
public record RingSettingsPayload(int width, int circumference, long seed, int wallHeight,
                                  int surfaceReferenceY, int formatVersion, long fingerprint)
        implements CustomPayload {
    /**
     * The channel name is versioned whenever its byte layout changes. Reusing
     * the old identifier makes an old codec consume its known prefix and then
     * crash on the unread fields before either side can explain the mismatch.
     */
    public static final Id<RingSettingsPayload> ID =
            new Id<>(Identifier.of(RingWorldMod.MOD_ID, "settings_v2"));
    public static final PacketCodec<RegistryByteBuf, RingSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RingSettingsPayload::width,
            PacketCodecs.VAR_INT, RingSettingsPayload::circumference,
            PacketCodecs.LONG, RingSettingsPayload::seed,
            PacketCodecs.VAR_INT, RingSettingsPayload::wallHeight,
            PacketCodecs.VAR_INT, RingSettingsPayload::surfaceReferenceY,
            PacketCodecs.VAR_INT, RingSettingsPayload::formatVersion,
            PacketCodecs.LONG, RingSettingsPayload::fingerprint,
            RingSettingsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
