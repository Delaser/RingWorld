package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.AtlasPregenerationAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One explicit user action for the versioned atlas-pregeneration channel. */
public record RingAtlasPregenerationControlPayload(long worldHash, AtlasPregenerationAction action)
        implements CustomPacketPayload {
    public static final Type<RingAtlasPregenerationControlPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(RingWorldMod.MOD_ID, "atlas_pregen_control_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingAtlasPregenerationControlPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.LONG, RingAtlasPregenerationControlPayload::worldHash,
                    ByteBufCodecs.VAR_INT.map(AtlasPregenerationAction::fromWireValue,
                            AtlasPregenerationAction::wireValue), RingAtlasPregenerationControlPayload::action,
                    RingAtlasPregenerationControlPayload::new);

    public RingAtlasPregenerationControlPayload {
        if (action == null) throw new IllegalArgumentException("action is required");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
