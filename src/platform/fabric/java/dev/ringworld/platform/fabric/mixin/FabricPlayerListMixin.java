package dev.ringworld.platform.fabric.mixin;

import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.platform.fabric.FabricHeadlessPlayerAdmission;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places immutable ring geometry before vanilla's initial world packets.
 *
 * <p>Fabric's normal play JOIN callback runs after {@code placeNewPlayer}
 * has queued the initial position and chunk buffer.  A seam-adjacent chunk
 * can therefore be rejected against the canonical view centre before the
 * client knows the circumference.  Queue the settings payload immediately
 * after the play-login packet, matching the proven NeoForge adapter while
 * keeping the transport-specific hook out of common topology code.</p>
 */
@Mixin(PlayerList.class)
abstract class FabricPlayerListMixin {
    @Inject(
            method = "placeNewPlayer",
            at = @At("HEAD"),
            cancellable = true)
    private void ringworld$rejectHeadlessBeforePlayLogin(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie,
            CallbackInfo ci) {
        if (FabricHeadlessPlayerAdmission.rejectBeforePlayLogin(connection, player)) ci.cancel();
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
        RingWorldNetworking.sendSettings(player);
    }
}
