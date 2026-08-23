package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingMapCompassSupport;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapFrame;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Calculates player, banner, and frame map-decoration X from a nearest image. */
@Mixin(MapItemSavedData.class)
abstract class MapItemSavedDataMixin {
    @Shadow public int centerX;
    @Shadow public ResourceKey<Level> dimension;
    @Shadow public byte scale;
    @Shadow private Map<String, MapBanner> bannerMarkers;
    @Shadow private Map<String, MapFrame> frameMarkers;
    @Unique private boolean ringworld$storedDecorationImagesAligned;

    @Shadow
    protected abstract void addDecoration(
            Holder<MapDecorationType> type, LevelAccessor level, String key,
            double xPos, double zPos, double yRot, Component name);

    /** Selects the nearest periodic source image before vanilla computes its float delta. */
    @ModifyVariable(method = "addDecoration", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double ringworld$nearestDecorationImage(
            double xPos,
            Holder<MapDecorationType> type, LevelAccessor level, String key,
            double originalXPos, double zPos, double yRot, Component name) {
        if (!(level instanceof ServerLevel world)
                || world.dimension() != Level.OVERWORLD || dimension != Level.OVERWORLD) {
            return xPos;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return RingMapCompassSupport.nearestMapImageX(geometry, xPos, centerX);
    }

    /** Applies the nearest-image rule before vanilla's banner in-map gate. */
    @Redirect(method = "toggleBanner", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getX()I"))
    private int ringworld$nearestBannerGateX(
            net.minecraft.core.BlockPos pos, LevelAccessor level,
            net.minecraft.core.BlockPos requestedPos) {
        if (!(level instanceof ServerLevel world)
                || world.dimension() != Level.OVERWORLD || dimension != Level.OVERWORLD) {
            return pos.getX();
        }
        return RingMapCompassSupport.nearestMapBannerBlockX(
                RingWorldServer.geometryFor(world), pos.getX(), centerX);
    }

    /**
     * Saved banner/frame markers are reconstructed before vanilla has a world
     * reference. Reapply them once when a carried Overworld map first has one,
     * so old saved decorations receive the same nearest-image calculation.
     */
    @Inject(method = "tickCarriedBy", at = @At("TAIL"))
    private void ringworld$alignStoredDecorationImages(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.item.ItemStack map,
            CallbackInfo ci) {
        if (ringworld$storedDecorationImagesAligned
                || !(player.level() instanceof ServerLevel world)
                || world.dimension() != Level.OVERWORLD || dimension != Level.OVERWORLD) return;

        for (MapBanner banner : bannerMarkers.values()) {
            addDecoration(banner.getDecoration(), world, banner.getId(),
                    banner.pos().getX(), banner.pos().getZ(), 180.0, banner.name().orElse(null));
        }
        for (MapFrame storedFrame : frameMarkers.values()) {
            addDecoration(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.FRAME, world,
                    "frame-" + storedFrame.getEntityId(), storedFrame.getPos().getX(),
                    storedFrame.getPos().getZ(), storedFrame.getRotation(), null);
        }
        ringworld$storedDecorationImagesAligned = true;
    }
}
