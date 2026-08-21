package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRaidSupport;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes the raid-village goal select and approach HOME POIs across the joined edge. */
@Mixin(targets = "net.minecraft.world.entity.raid.Raider$RaiderMoveThroughVillageGoal")
abstract class RaiderMoveThroughVillageGoalMixin {
    @Shadow @Final private Raider raider;

    @Redirect(
            method = "hasSuitablePoi",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getRandom(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
    private Optional<BlockPos> ringworld$randomPeriodicPoi(
            PoiManager manager, Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> notVisited,
            PoiManager.Occupancy occupancy, BlockPos origin, int radius, RandomSource random) {
        if (!(raider.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return manager.getRandom(predicate, notVisited, occupancy, origin, radius, random);
        }

        RingGeometry geometry = RingWorldServer.geometryFor(level);
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        for (RingRaidSupport.XWindow window : RingRaidSupport.canonicalBlockWindows(
                geometry, origin.getX() - radius, origin.getX() + radius)) {
            int centerX = window.minX() + (window.maxX() - window.minX()) / 2;
            int queryRadius = Math.max(radius, (window.maxX() - window.minX() + 1) / 2);
            manager.getInSquare(predicate, new BlockPos(centerX, origin.getY(), origin.getZ()), queryRadius, occupancy)
                    .map(record -> record.getPos())
                    .filter(poi -> RingRaidSupport.periodicDistanceSquared(
                            geometry,
                            origin.getX(), origin.getY(), origin.getZ(),
                            poi.getX(), poi.getY(), poi.getZ()) <= (double) radius * radius)
                    .map(BlockPos::immutable)
                    .forEach(candidates::add);
        }

        // This is vanilla's shuffle-then-first-accepted selection, applied once
        // to the canonical de-duplicated seam union. Keep poiPos and the goal's
        // visited list canonical; navigation projects only transient targets.
        List<BlockPos> shuffled = Util.toShuffledList(candidates.stream(), random);
        return shuffled.stream()
                .filter(notVisited)
                .findFirst();
    }

    @Redirect(
            method = "canContinueToUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean ringworld$periodicPoiArrival(BlockPos poi, net.minecraft.core.Position position, double distance) {
        return ringworld$closerToPoi(poi, position, distance);
    }

    @Redirect(
            method = "stop",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean ringworld$periodicVisitedPoiArrival(BlockPos poi, net.minecraft.core.Position position, double distance) {
        return ringworld$closerToPoi(poi, position, distance);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/util/DefaultRandomPos;getPosTowards(Lnet/minecraft/world/entity/PathfinderMob;IILnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$nearestPoiDirection(
            PathfinderMob pathfinder, int horizontalDistance, int verticalDistance,
            Vec3 poi, double maxXzRadiansFromDirection) {
        if (!(raider.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return DefaultRandomPos.getPosTowards(
                    pathfinder, horizontalDistance, verticalDistance, poi, maxXzRadiansFromDirection);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        Vec3 nearestPoi = new Vec3(geometry.nearestImageX(poi.x, pathfinder.getX()), poi.y, poi.z);
        return DefaultRandomPos.getPosTowards(
                pathfinder, horizontalDistance, verticalDistance, nearestPoi, maxXzRadiansFromDirection);
    }

    private boolean ringworld$closerToPoi(BlockPos poi, net.minecraft.core.Position position, double distance) {
        if (!(raider.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return poi.closerToCenterThan(position, distance);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        double dx = geometry.shortestCircumferenceDelta(position.x(), poi.getX() + 0.5);
        double dy = poi.getY() + 0.5 - position.y();
        double dz = poi.getZ() + 0.5 - position.z();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }
}
