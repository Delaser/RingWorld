package dev.ringworld.platform.neoforge.mixin;

import dev.ringworld.platform.neoforge.NeoForgeHeadlessPlayerAdmission;
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
 * Rejects explicit headless-prewarm joins before play setup and otherwise
 * queues immutable ring geometry immediately after the play-login packet.
 *
 * <p>NeoForge's {@code PlayerLoggedInEvent} fires only after the initial play
 * packet buffer has been flushed. Sending the geometry there lets canonical
 * chunk packets reach a fresh client before it knows how to choose their
 * nearest periodic image. Put the settings packet directly behind vanilla's
 * login packet instead, so every following position and chunk packet is
 * decoded against the authoritative chart. Headless admission must happen at
 * method head so it cannot reach either packet sequence.</p>
 */
@Mixin(PlayerList.class)
abstract class NeoForgePlayerListMixin {
    @Inject(
            method = "placeNewPlayer",
            at = @At("HEAD"),
            cancellable = true)
    private void ringworld$rejectHeadlessBeforePlayLogin(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie,
            CallbackInfo ci) {
        if (NeoForgeHeadlessPlayerAdmission.rejectBeforePlayLogin(connection, player)) ci.cancel();
    }

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
