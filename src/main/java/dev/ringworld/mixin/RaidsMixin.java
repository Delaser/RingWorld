package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRaidSupport;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes vanilla raid-centre POI averaging see the joined RingWorld seam. */
@Mixin(Raids.class)
abstract class RaidsMixin {
    @Invoker("getOrCreateRaid")
    protected abstract Raid ringworld$invokeGetOrCreateRaid(ServerLevel level, BlockPos center);

    @Redirect(method = "createOrExtendRaid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getInRange(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"))
    private Stream<PoiRecord> ringworld$periodicVillagePois(
            PoiManager poiManager, Predicate<Holder<PoiType>> typePredicate,
            BlockPos raidPosition, int range, PoiManager.Occupancy occupancy,
            ServerPlayer player, BlockPos triggerPosition) {
        ServerLevel level = player.level();
        if (level.dimension() != Level.OVERWORLD) {
            return poiManager.getInRange(typePredicate, raidPosition, range, occupancy);
        }

        RingGeometry geometry = RingWorldServer.geometryFor(level);
        LinkedHashMap<BlockPos, PoiRecord> canonicalPois = new LinkedHashMap<>();
        for (int queryX : RingRaidSupport.periodicQueryXs(geometry, raidPosition.getX())) {
            poiManager.getInRange(typePredicate, new BlockPos(queryX, raidPosition.getY(), raidPosition.getZ()),
                    range, occupancy).forEach(poi -> {
                BlockPos position = poi.getPos();
                BlockPos canonical = new BlockPos(geometry.wrapBlockX(position.getX()), position.getY(), position.getZ());
                canonicalPois.putIfAbsent(canonical, poi);
            });
        }
        // In 26.1.2 createOrExtendRaid consumes only PoiRecord.getPos() after
        // this call. These synthetic records must remain transient averaging
        // inputs and must never be inserted back into PoiManager.
        return canonicalPois.entrySet().stream().map(entry -> {
            BlockPos canonical = entry.getKey();
            int nearestX = (int) geometry.nearestImageX(canonical.getX(), triggerPosition.getX());
            BlockPos nearest = new BlockPos(nearestX, canonical.getY(), canonical.getZ());
            PoiRecord original = entry.getValue();
            return nearest.equals(original.getPos()) ? original
                    : new PoiRecord(nearest, original.getPoiType(), () -> { });
        });
    }

    @Redirect(method = "createOrExtendRaid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/raid/Raids;getOrCreateRaid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/entity/raid/Raid;"))
    private Raid ringworld$canonicalRaidCentre(
            Raids raids, ServerLevel level, BlockPos center) {
        if (level.dimension() != Level.OVERWORLD) {
            return ringworld$invokeGetOrCreateRaid(level, center);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        BlockPos canonical = new BlockPos(geometry.wrapBlockX(center.getX()), center.getY(), center.getZ());
        return ringworld$invokeGetOrCreateRaid(level, canonical);
    }
}
