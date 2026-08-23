package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses the nearest ring image for explosion exposure rays and impulse direction. */
@Mixin(Explosion.class)
abstract class ExplosionImplMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private double x;

    @Redirect(
            method = "getSeenPercent",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext ringworld$periodicExposureRay(
            Vec3 start, Vec3 end, ClipContext.Block shapeType,
            ClipContext.Fluid fluidHandling, Entity entity) {
        if (entity.level() instanceof ServerLevel serverWorld
                && serverWorld.dimension() == Level.OVERWORLD) {
            RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
            end = new Vec3(geometry.nearestImageX(end.x, start.x), end.y, end.z);
        }
        return new ClipContext(start, end, shapeType, fluidHandling, entity);
    }

    @Redirect(
            method = "explode",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double ringworld$periodicKnockbackX(Entity entity) {
        double entityX = entity.getX();
        if (!(level instanceof ServerLevel serverLevel) || level.dimension() != Level.OVERWORLD) {
            return entityX;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverLevel);
        return geometry.nearestImageX(entityX, x);
    }
}
