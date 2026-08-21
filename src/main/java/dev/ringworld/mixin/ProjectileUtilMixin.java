package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raycasts against the periodic image of seam-adjacent entity hitboxes. */
@Mixin(ProjectileUtil.class)
abstract class ProjectileUtilMixin {
    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void ringworld$periodicEntityCollision(Level world, Entity source, Vec3 start, Vec3 end,
                                                          AABB query, Predicate<Entity> predicate, float margin,
                                                          CallbackInfoReturnable<EntityHitResult> cir) {
        if (!(world instanceof ServerLevel serverWorld) || serverWorld.dimension() != Level.OVERWORLD) return;

        RingTopology topology = new RingTopology(RingWorldServer.geometryFor(serverWorld));
        double nearestDistance = Double.MAX_VALUE;
        Entity nearestEntity = null;
        Vec3 nearestHit = null;

        for (Entity candidate : world.getEntities(source, query, predicate)) {
            AABB hitbox = topology.projectBoxNear(candidate.getBoundingBox(), source.getX()).inflate(margin);
            Optional<Vec3> hit = hitbox.clip(start, end);
            if (hit.isEmpty()) continue;

            double distance = start.distanceToSqr(hit.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = candidate;
                nearestHit = hit.get();
            }
        }

        cir.setReturnValue(nearestEntity == null ? null : new EntityHitResult(nearestEntity, nearestHit));
    }
}