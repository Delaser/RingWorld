package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/** Raycasts against the periodic image of seam-adjacent entity hitboxes. */
@Mixin(ProjectileUtil.class)
abstract class ProjectileUtilMixin {
    @Inject(
            method = "getEntityCollision(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;F)Lnet/minecraft/util/hit/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void ringworld$periodicEntityCollision(
            World world, Entity source, Vec3d start, Vec3d end, Box query,
            Predicate<Entity> predicate, float margin,
            CallbackInfoReturnable<EntityHitResult> cir) {
        if (!(world instanceof ServerWorld serverWorld)
                || serverWorld.getRegistryKey() != World.OVERWORLD) return;

        RingTopology topology = new RingTopology(RingWorldServer.geometryFor(serverWorld));
        double nearestDistance = Double.MAX_VALUE;
        Entity nearestEntity = null;
        Vec3d nearestHit = null;
        for (Entity candidate : world.getOtherEntities(source, query, predicate)) {
            Box hitbox = topology.projectBoxNear(candidate.getBoundingBox(), source.getX()).expand(margin);
            Optional<Vec3d> hit = hitbox.raycast(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.squaredDistanceTo(hit.get());
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
            method = "collectPiercingCollisions(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;FLnet/minecraft/world/RaycastContext$ShapeType;Z)Ljava/util/Collection;",
            at = @At("HEAD"), cancellable = true)
    private static void ringworld$periodicPiercingCollisions(
            World world, Entity source, Vec3d start, Vec3d end, Box query,
            Predicate<Entity> predicate, float margin, RaycastContext.ShapeType shapeType,
            boolean includeInside, CallbackInfoReturnable<Collection<EntityHitResult>> cir) {
        if (!(world instanceof ServerWorld serverWorld)
                || serverWorld.getRegistryKey() != World.OVERWORLD) return;

        RingTopology topology = new RingTopology(RingWorldServer.geometryFor(serverWorld));
        Collection<EntityHitResult> hits = new ArrayList<>();
        for (Entity candidate : world.getOtherEntities(source, query, predicate)) {
            Box hitbox = topology.projectBoxNear(candidate.getBoundingBox(), source.getX());
            if (includeInside && hitbox.contains(start)) {
                hits.add(new EntityHitResult(candidate, start));
                continue;
            }

            Optional<Vec3d> directHit = hitbox.raycast(start, end);
            if (directHit.isPresent()) {
                hits.add(new EntityHitResult(candidate, directHit.get()));
                continue;
            }
            if (margin <= 0.0F) continue;

            Optional<Vec3d> marginHit = hitbox.expand(margin).raycast(start, end);
            if (marginHit.isEmpty()) continue;
            Vec3d marginPoint = marginHit.get();
            Vec3d center = hitbox.getCenter();
            BlockHitResult obstruction = world.getCollisionsIncludingWorldBorder(new RaycastContext(
                    marginPoint, center, shapeType, RaycastContext.FluidHandling.NONE, source));
            if (obstruction.getType() != HitResult.Type.MISS) center = obstruction.getPos();
            hitbox.raycast(marginPoint, center)
                    .ifPresent(hit -> hits.add(new EntityHitResult(candidate, hit)));
        }
        cir.setReturnValue(hits);
    }
}
