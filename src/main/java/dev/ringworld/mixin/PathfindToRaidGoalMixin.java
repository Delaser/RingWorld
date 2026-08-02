package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRaidSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.PathfindToRaidGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps an active raider's local raid direction and village test continuous at the seam. */
@Mixin(PathfindToRaidGoal.class)
abstract class PathfindToRaidGoalMixin {
    @Shadow @Final private Raider mob;

    @Redirect(
            method = {"canUse", "canContinueToUse"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ringworld$periodicVillageProximity(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return level.isVillage(pos);
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        for (int imageX : RingRaidSupport.periodicQueryXs(geometry, pos.getX())) {
            if (level.isVillage(new BlockPos(imageX, pos.getY(), pos.getZ()))) return true;
        }
        return false;
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/util/DefaultRandomPos;getPosTowards(Lnet/minecraft/world/entity/PathfinderMob;IILnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$nearestRaidDirection(
            PathfinderMob pathfinder, int horizontalDistance, int verticalDistance,
            Vec3 raidCenter, double maxXzRadiansFromDirection) {
        if (!(mob.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return DefaultRandomPos.getPosTowards(
                    pathfinder, horizontalDistance, verticalDistance, raidCenter, maxXzRadiansFromDirection);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        Vec3 nearestCenter = new Vec3(
                geometry.nearestImageX(raidCenter.x, pathfinder.getX()), raidCenter.y, raidCenter.z);
        return DefaultRandomPos.getPosTowards(
                pathfinder, horizontalDistance, verticalDistance, nearestCenter, maxXzRadiansFromDirection);
    }
}
