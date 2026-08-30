package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPortalDestinationBounds;
import dev.ringworld.world.RingWorldSettings;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes Overworld portal lookup and creation respect periodic X and finite Z. */
@Mixin(PortalForcer.class)
abstract class PortalForcerMixin {
    @Shadow @Final private ServerLevel level;

    /**
     * Vanilla uses one flat square POI query. Querying the two adjacent X
     * images as well makes a portal at C-1 visible from an anchor near zero,
     * while the returned owner remains canonical.
     */
    @Inject(method = "findClosestPortalPosition", at = @At("HEAD"), cancellable = true)
    private void ringworld$findPeriodicPortal(
            BlockPos approximateExitPos, boolean toNether, WorldBorder worldBorder,
            CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int rimThickness = RingWorldSettings.get(level).wallStyle().thicknessBlocks();
        BlockPos anchor = RingPortalDestinationBounds.normalizeSearchAnchor(
                geometry, approximateExitPos, rimThickness);
        int radius = toNether ? 16 : 128;
        PoiManager pois = level.getPoiManager();
        Set<BlockPos> candidates = new LinkedHashSet<>();

        for (BlockPos query : RingPortalDestinationBounds.periodicQueryAnchors(geometry, anchor)) {
            pois.ensureLoadedAndValid(level, query, radius);
            pois.getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL), query, radius,
                            PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPos)
                    .map(pos -> new BlockPos(geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ()))
                    .filter(worldBorder::isWithinBounds)
                    .filter(pos -> RingPortalDestinationBounds.isSafePortalBlock(
                            geometry, pos, rimThickness))
                    .filter(pos -> level.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                    .forEach(candidates::add);
        }

        cir.setReturnValue(candidates.stream()
                .min(Comparator.<BlockPos>comparingDouble(pos ->
                                RingPortalDestinationBounds.periodicDistanceSquared(geometry, anchor, pos))
                        .thenComparingInt(BlockPos::getY)));
    }

    /** Keeps the full vanilla 16-block placement sweep clear of either rim. */
    @ModifyVariable(method = "createPortal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos ringworld$normalizePortalCreationAnchor(BlockPos origin) {
        if (level.dimension() != Level.OVERWORLD) return origin;
        return RingPortalDestinationBounds.normalizeSearchAnchor(
                RingWorldServer.geometryFor(level), origin,
                RingWorldSettings.get(level).wallStyle().thicknessBlocks());
    }
}
