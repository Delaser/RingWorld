package dev.ringworld.mixin;

import dev.ringworld.server.RingChunkGraphAccess;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Retry the periodic location criterion after outstanding ticket movement settles. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerLocationWorkMixin {
    @Redirect(method = "doTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/criterion/PlayerTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void ringworld$locationAfterTickets(PlayerTrigger trigger, ServerPlayer player) {
        if (trigger == CriteriaTriggers.LOCATION && player.level().dimension() == Level.OVERWORLD
                && ((RingChunkGraphAccess) player.level().getChunkSource()).ringworld$graphPending()) return;
        trigger.trigger(player);
    }
}
