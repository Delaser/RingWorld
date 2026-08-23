package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies periodic distance to server-authoritative block/entity reach. */
@Mixin(Player.class)
abstract class PlayerInteractionDistanceMixin {
    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicBlockReach(BlockPos pos, double additionalRange,
                                              CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        RingGeometry geometry = geometry(player);
        if (geometry == null) return;
        double range = player.blockInteractionRange() + additionalRange;
        AABB blockImage = new RingTopology(geometry).projectBoxNear(new AABB(pos), player.getX());
        cir.setReturnValue(blockImage.distanceToSqr(player.getEyePosition()) < range * range);
    }

    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicEntityReach(AABB box, double additionalRange,
                                               CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        RingGeometry geometry = geometry(player);
        if (geometry == null) return;
        double range = player.entityInteractionRange() + additionalRange;
        AABB entityImage = new RingTopology(geometry).projectBoxNear(box, player.getX());
        cir.setReturnValue(entityImage.distanceToSqr(player.getEyePosition()) < range * range);
    }

    private static RingGeometry geometry(Player player) {
        return player.level() instanceof ServerLevel world && world.dimension() == Level.OVERWORLD
                ? RingWorldServer.geometryFor(world) : null;
    }
}
