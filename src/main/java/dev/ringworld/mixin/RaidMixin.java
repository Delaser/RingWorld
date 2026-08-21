package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRaidSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps the active raid centre, retention, and wave probes local through X=0/C. */
@Mixin(Raid.class)
abstract class RaidMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow private BlockPos center;

    @Redirect(
            method = {"tick", "updateRaiders", "findRandomSpawnPos"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ringworld$periodicVillage(ServerLevel world, BlockPos pos) {
        if (world.dimension() != Level.OVERWORLD) return world.isVillage(pos);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        for (int imageX : RingRaidSupport.periodicQueryXs(geometry, pos.getX())) {
            if (world.isVillage(new BlockPos(imageX, pos.getY(), pos.getZ()))) return true;
        }
        return false;
    }

    @Inject(method = "moveRaidCenterToNearbyVillageSection", at = @At("HEAD"), cancellable = true)
    private void ringworld$moveCenterPeriodically(CallbackInfo ci) {
        if (level.dimension() != Level.OVERWORLD) return;

        RingGeometry geometry = RingWorldServer.geometryFor(level);
        Map<Long, SectionPos> canonicalSections = new LinkedHashMap<>();
        SectionPos.cube(SectionPos.of(center), 2).forEach(section -> {
            int canonicalX = Math.floorMod(section.x(), geometry.circumferenceChunks());
            SectionPos canonical = SectionPos.of(canonicalX, section.y(), section.z());
            canonicalSections.putIfAbsent(canonical.asLong(), canonical);
        });

        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (SectionPos section : canonicalSections.values()) {
            BlockPos candidate = section.center();
            if (!ringworld$periodicVillage(level, candidate)) continue;
            double distance = RingRaidSupport.periodicDistanceSquared(
                    geometry,
                    center.getX(), center.getY(), center.getZ(),
                    candidate.getX(), candidate.getY(), candidate.getZ());
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        if (nearest != null) center = ringworld$canonical(geometry, nearest);
        ci.cancel();
    }

    @Redirect(
            method = "updateRaiders",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double ringworld$periodicRaiderDistance(BlockPos raidCenter, Vec3i raiderPos) {
        if (level.dimension() != Level.OVERWORLD) return raidCenter.distSqr(raiderPos);
        return RingRaidSupport.periodicDistanceSquared(
                RingWorldServer.geometryFor(level),
                raidCenter.getX(), raidCenter.getY(), raidCenter.getZ(),
                raiderPos.getX(), raiderPos.getY(), raiderPos.getZ());
    }

    @Redirect(
            method = "findRandomSpawnPos",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int ringworld$canonicalSpawnHeight(
            ServerLevel world, Heightmap.Types type, int blockX, int blockZ) {
        if (world.dimension() != Level.OVERWORLD) return world.getHeight(type, blockX, blockZ);
        return world.getHeight(type, RingWorldServer.geometryFor(world).wrapBlockX(blockX), blockZ);
    }

    @Redirect(
            method = "findRandomSpawnPos",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;hasChunksAt(IIII)Z"))
    private boolean ringworld$canonicalSpawnChunks(
            ServerLevel world, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (world.dimension() != Level.OVERWORLD) {
            return world.hasChunksAt(minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        }

        RingGeometry geometry = RingWorldServer.geometryFor(world);
        for (RingRaidSupport.XWindow window :
                RingRaidSupport.canonicalBlockWindows(geometry, minBlockX, maxBlockX)) {
            if (!world.hasChunksAt(window.minX(), minBlockZ, window.maxX(), maxBlockZ)) return false;
        }
        return true;
    }

    @Inject(method = "findRandomSpawnPos", at = @At("RETURN"), cancellable = true)
    private void ringworld$canonicalSpawnResult(
            int proximity, int tries, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos result = cir.getReturnValue();
        if (result == null || level.dimension() != Level.OVERWORLD) return;
        cir.setReturnValue(ringworld$canonical(RingWorldServer.geometryFor(level), result));
    }

    private static BlockPos ringworld$canonical(RingGeometry geometry, BlockPos pos) {
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos.immutable() : new BlockPos(x, pos.getY(), pos.getZ());
    }
}