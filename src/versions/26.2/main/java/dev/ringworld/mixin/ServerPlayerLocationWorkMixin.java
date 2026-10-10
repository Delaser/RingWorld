package dev.ringworld.mixin;

import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Avoid forcing structure chunk lookups on the first location check after movement. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerLocationWorkMixin {
    @Unique private long ringworld$lastLocationChunk = Long.MIN_VALUE;

    @Redirect(method = "doTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/triggers/PlayerTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void ringworld$locationAfterTickets(PlayerTrigger trigger, ServerPlayer player) {
        if (trigger == CriteriaTriggers.LOCATION && player.level().dimension() == Level.OVERWORLD) {
            long chunk = player.chunkPosition().pack();
            if (ringworld$lastLocationChunk != chunk) {
                ringworld$lastLocationChunk = chunk;
                return; // Retry at the next vanilla location check, after ticket movement settles.
            }
        }
        trigger.trigger(player);
    }
}
