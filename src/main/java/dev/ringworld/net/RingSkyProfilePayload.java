package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Live server-authoritative sky presentation update; terrain identity is unchanged. */
public record RingSkyProfilePayload(int backdrop, int lightSource, int formatVersion)
        implements CustomPacketPayload {
    public static final Type<RingSkyProfilePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "sky_profile_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingSkyProfilePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RingSkyProfilePayload::backdrop,
                    ByteBufCodecs.VAR_INT, RingSkyProfilePayload::lightSource,
                    ByteBufCodecs.VAR_INT, RingSkyProfilePayload::formatVersion,
                    RingSkyProfilePayload::new);

    public static RingSkyProfilePayload from(RingSkyProfile profile) {
        return new RingSkyProfilePayload(
                profile.backdrop().id(), profile.lightSource().id(), profile.formatVersion());
    }

    public RingSkyProfile profile() {
        return new RingSkyProfile(RingSkyProfile.Backdrop.fromId(backdrop),
                RingSkyProfile.LightSource.fromId(lightSource), formatVersion);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
