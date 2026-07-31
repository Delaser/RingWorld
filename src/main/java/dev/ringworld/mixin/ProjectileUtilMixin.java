package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Raycasts against the periodic image of seam-adjacent entity hitboxes. */
@Mixin(ProjectileUtil.class)
abstract class ProjectileUtilMixin {
    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void ringworld$periodicEntityCollision(
            Level world, Entity source, Vec3 start, Vec3 end, AABB query,
            Predicate<Entity> predicate, float margin,
            CallbackInfoReturnable<EntityHitResult> cir) {
        if (!(world instanceof ServerLevel serverWorld)
                || serverWorld.dimension() != Level.OVERWORLD) return;

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

    /**
     * Persistent projectiles (including ordinary arrows) use the piercing
     * collector even at pierce level zero, so it needs the same periodic
     * candidate projection as the single-hit helper above.
     */
    @Inject(
            method = "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;Z)Ljava/util/Collection;",
            at = @At("HEAD"), cancellable = true)
    private static void ringworld$periodicPiercingCollisions(
            Level world, Entity source, Vec3 start, Vec3 end, AABB query,
            Predicate<Entity> predicate, float margin, ClipContext.Block shapeType,
            boolean includeInside, CallbackInfoReturnable<Collection<EntityHitResult>> cir) {
        if (!(world instanceof ServerLevel serverWorld)
                || serverWorld.dimension() != Level.OVERWORLD) return;

        RingTopology topology = new RingTopology(RingWorldServer.geometryFor(serverWorld));
        Collection<EntityHitResult> hits = new ArrayList<>();
        for (Entity candidate : world.getEntities(source, query, predicate)) {
            AABB hitbox = topology.projectBoxNear(candidate.getBoundingBox(), source.getX());
            if (includeInside && hitbox.contains(start)) {
                hits.add(new EntityHitResult(candidate, start));
                continue;
            }

            Optional<Vec3> directHit = hitbox.clip(start, end);
            if (directHit.isPresent()) {
                hits.add(new EntityHitResult(candidate, directHit.get()));
                continue;
            }
            if (margin <= 0.0F) continue;

            Optional<Vec3> marginHit = hitbox.inflate(margin).clip(start, end);
            if (marginHit.isEmpty()) continue;
            Vec3 marginPoint = marginHit.get();
            Vec3 center = hitbox.getCenter();
            BlockHitResult obstruction = world.clipIncludingBorder(new ClipContext(
                    marginPoint, center, shapeType, ClipContext.Fluid.NONE, source));
            if (obstruction.getType() != HitResult.Type.MISS) center = obstruction.getLocation();
            hitbox.clip(marginPoint, center)
                    .ifPresent(hit -> hits.add(new EntityHitResult(candidate, hit)));
        }
        cir.setReturnValue(hits);
    }
}
