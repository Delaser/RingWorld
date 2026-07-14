package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses the nearest ring image for explosion exposure rays and impulse direction. */
@Mixin(ExplosionImpl.class)
abstract class ExplosionImplMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private Vec3d pos;

    @Redirect(
            method = "calculateReceivedDamage",
            at = @At(value = "NEW", target = "(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/world/RaycastContext$ShapeType;Lnet/minecraft/world/RaycastContext$FluidHandling;Lnet/minecraft/entity/Entity;)Lnet/minecraft/world/RaycastContext;"))
    private static RaycastContext ringworld$periodicExposureRay(
            Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType,
            RaycastContext.FluidHandling fluidHandling, Entity entity) {
        if (entity.getEntityWorld() instanceof ServerWorld serverWorld
                && serverWorld.getRegistryKey() == World.OVERWORLD) {
            RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
            end = new Vec3d(geometry.nearestImageX(end.x, start.x), end.y, end.z);
        }
        return new RaycastContext(start, end, shapeType, fluidHandling, entity);
    }

    @Redirect(
            method = "damageEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getEyePos()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d ringworld$periodicKnockbackOrigin(Entity entity) {
        Vec3d entityEye = entity.getEyePos();
        if (world.getRegistryKey() != World.OVERWORLD) return entityEye;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return new Vec3d(geometry.nearestImageX(entityEye.x, pos.x), entityEye.y, entityEye.z);
    }
}
