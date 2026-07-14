package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTopology;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies periodic distance to server-authoritative block/entity reach. */
@Mixin(PlayerEntity.class)
abstract class PlayerInteractionDistanceMixin {
    @Inject(method = "canInteractWithBlockAt", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicBlockReach(BlockPos pos, double additionalRange,
                                              CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        RingGeometry geometry = geometry(player);
        if (geometry == null) return;
        double range = player.getBlockInteractionRange() + additionalRange;
        Box blockImage = new RingTopology(geometry).projectBoxNear(new Box(pos), player.getX());
        cir.setReturnValue(blockImage.squaredMagnitude(player.getEyePos()) < range * range);
    }

    @Inject(method = "canInteractWithEntityIn", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicEntityReach(Box box, double additionalRange,
                                               CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        RingGeometry geometry = geometry(player);
        if (geometry == null) return;
        double range = player.getEntityInteractionRange() + additionalRange;
        Box entityImage = new RingTopology(geometry).projectBoxNear(box, player.getX());
        cir.setReturnValue(entityImage.squaredMagnitude(player.getEyePos()) < range * range);
    }

    /**
     * Attacks use their own 1.21.11 reach model and never call the ordinary
     * interaction method above. Preserve that model, but give it the target's
     * nearest periodic image before the server validates the attack packet.
     */
    @Inject(method = "canAttackEntityIn", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicAttackReach(Box box, double additionalRange,
                                               CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        RingGeometry geometry = geometry(player);
        if (geometry == null) return;
        Box entityImage = new RingTopology(geometry).projectBoxNear(box, player.getX());
        cir.setReturnValue(player.getAttackRange().isWithinRange(player, entityImage, additionalRange));
    }

    private static RingGeometry geometry(PlayerEntity player) {
        return player.getEntityWorld() instanceof ServerWorld world && world.getRegistryKey() == World.OVERWORLD
                ? RingWorldServer.geometryFor(world) : null;
    }
}
