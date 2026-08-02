package dev.ringworld.platform.neoforge.mixin;

import dev.ringworld.platform.neoforge.NeoForgeRingWorldNetworking;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Queues immutable ring geometry immediately after the play-login packet.
 *
 * <p>NeoForge's {@code PlayerLoggedInEvent} fires only after the initial play
 * packet buffer has been flushed. Sending the geometry there lets canonical
 * chunk packets reach a fresh client before it knows how to choose their
 * nearest periodic image. Put the settings packet directly behind vanilla's
 * login packet instead, so every following position and chunk packet is
 * decoded against the authoritative chart.</p>
 */
@Mixin(PlayerList.class)
abstract class NeoForgePlayerListMixin {
    @Inject(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void ringworld$sendSettingsBeforeInitialWorldPackets(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie,
            CallbackInfo ci) {
        NeoForgeRingWorldNetworking.sendSettings(player);
    }
}
