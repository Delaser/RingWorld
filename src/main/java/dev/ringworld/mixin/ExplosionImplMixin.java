package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses the nearest ring image for explosion exposure rays and impulse direction. */
@Mixin(ServerExplosion.class)
abstract class ExplosionImplMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Vec3 center;

    // All exposure rays end at this argument. Project it once before vanilla
    // or another mod's raycast implementation samples the entity.
    @ModifyVariable(method = "getSeenPercent", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Vec3 ringworld$periodicExposureCenter(Vec3 center, Vec3 originalCenter, Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverWorld)
                || serverWorld.dimension() != Level.OVERWORLD) return center;
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        double x = geometry.nearestImageX(center.x, entity.getX());
        return x == center.x ? center : new Vec3(x, center.y, center.z);
    }

    @Redirect(
            // NeoForge patches vanilla's no-argument method into a wrapper and
            // moves the entity loop into hurtEntities(List<BlockPos>). Match
            // both layouts while requiring the eye-position call in whichever
            // implementation actually owns the loop.
            method = {"hurtEntities()V", "hurtEntities(Ljava/util/List;)V"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$periodicKnockbackOrigin(Entity entity) {
        Vec3 entityEye = entity.getEyePosition();
        if (level.dimension() != Level.OVERWORLD) return entityEye;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        return new Vec3(geometry.nearestImageX(entityEye.x, center.x), entityEye.y, entityEye.z);
    }
}
