//package dev.ringworld.mixin;
//
//import dev.ringworld.server.RingWorldServer;
//import dev.ringworld.world.RingGeometry;
//import dev.ringworld.world.RingMapCompassSupport;
//import net.minecraft.core.Holder;
//import net.minecraft.network.chat.Component;
//import net.minecraft.resources.ResourceKey;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.LevelAccessor;
//import net.minecraft.world.level.saveddata.maps.MapBanner;
//import net.minecraft.world.level.saveddata.maps.MapFrame;
//import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
//import net.minecraft.world.level.saveddata.maps.MapDecorationType;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.Unique;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.ModifyArgs;
//import org.spongepowered.asm.mixin.injection.Redirect;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
//import org.spongepowered.asm.mixin.gen.Invoker;
//
//import java.util.Map;
//
//// Disabled for initial 1.21.1 backport:
//// MapItemSavedData decoration internals differ in 1.21.1.
//// calculateDecorationLocationAndType and the newer three-argument
//// tickCarriedBy path do not exist.
//// Re-port periodic map decorations after first boot.
///** Calculates player, banner, and frame map-decoration X from a nearest image. */
//@Mixin(MapItemSavedData.class)
//abstract class MapItemSavedDataMixin {
//    @Shadow public int centerX;
//    @Shadow public ResourceKey<Level> dimension;
//    @Shadow public byte scale;
//    @Shadow private Map<String, MapBanner> bannerMarkers;
//    @Shadow private Map<String, MapFrame> frameMarkers;
//    @Unique private RingGeometry ringworld$decorationGeometry;
//    @Unique private double ringworld$decorationCanonicalX;
//    @Unique private boolean ringworld$storedDecorationImagesAligned;
//
//    @Invoker("addDecoration")
//    protected abstract void ringworld$addDecoration(
//            Holder<MapDecorationType> type, LevelAccessor level, String key,
//            double xPos, double zPos, double yRot, Component name);
//
//    /** Captures the unrounded source X before vanilla reduces it to a float delta. */
//    @Inject(method = "addDecoration", at = @At("HEAD"))
//    private void ringworld$captureDecorationContext(
//            Holder<MapDecorationType> type, LevelAccessor level, String key,
//            double xPos, double zPos, double yRot, Component name, CallbackInfo ci) {
//        ringworld$decorationGeometry = level instanceof ServerLevel world
//                && world.dimension() == Level.OVERWORLD && dimension == Level.OVERWORLD
//                ? RingWorldServer.geometryFor(world) : null;
//        ringworld$decorationCanonicalX = xPos;
//    }
//
//    @ModifyArgs(method = "addDecoration", at = @At(value = "INVOKE",
//            target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;calculateDecorationLocationAndType(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/LevelAccessor;DFF)Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData$MapDecorationLocation;"))
//    private void ringworld$nearestDecorationImage(Args args) {
//        RingGeometry geometry = ringworld$decorationGeometry;
//        if (geometry == null) return;
//        int scaling = 1 << scale;
//        args.set(3, RingMapCompassSupport.nearestMapDecorationDeltaX(
//                geometry, centerX, scaling, ringworld$decorationCanonicalX));
//    }
//
//    @Inject(method = "addDecoration", at = @At("RETURN"))
//    private void ringworld$clearDecorationContext(
//            Holder<MapDecorationType> type, LevelAccessor level, String key,
//            double xPos, double zPos, double yRot, Component name, CallbackInfo ci) {
//        ringworld$decorationGeometry = null;
//    }
//
//    /** Applies the nearest-image rule before vanilla's banner in-map gate. */
//    @Redirect(method = "toggleBanner", at = @At(value = "INVOKE",
//            target = "Lnet/minecraft/core/BlockPos;getX()I"))
//    private int ringworld$nearestBannerGateX(
//            net.minecraft.core.BlockPos pos, LevelAccessor level,
//            net.minecraft.core.BlockPos requestedPos) {
//        if (!(level instanceof ServerLevel world)
//                || world.dimension() != Level.OVERWORLD || dimension != Level.OVERWORLD) {
//            return pos.getX();
//        }
//        return RingMapCompassSupport.nearestMapBannerBlockX(
//                RingWorldServer.geometryFor(world), pos.getX(), centerX);
//    }
//
//    /**
//     * Saved banner/frame markers are reconstructed before vanilla has a world
//     * reference. Reapply them once when a carried Overworld map first has one,
//     * so old saved decorations receive the same nearest-image calculation.
//     */
//    @Inject(method = "tickCarriedBy", at = @At("TAIL"))
//    private void ringworld$alignStoredDecorationImages(
//            net.minecraft.world.entity.player.Player player,
//            net.minecraft.world.item.ItemStack map,
//            net.minecraft.world.entity.decoration.ItemFrame frame,
//            CallbackInfo ci) {
//        if (ringworld$storedDecorationImagesAligned
//                || !(player.level() instanceof ServerLevel world)
//                || world.dimension() != Level.OVERWORLD || dimension != Level.OVERWORLD) return;
//
//        for (MapBanner banner : bannerMarkers.values()) {
//            ringworld$addDecoration(banner.getDecoration(), world, banner.getId(),
//                    banner.pos().getX(), banner.pos().getZ(), 180.0, banner.name().orElse(null));
//        }
//        for (MapFrame storedFrame : frameMarkers.values()) {
//            ringworld$addDecoration(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.FRAME, world,
//                    "frame-" + storedFrame.entityId(), storedFrame.pos().getX(), storedFrame.pos().getZ(),
//                    storedFrame.rotation(), null);
//        }
//        ringworld$storedDecorationImagesAligned = true;
//    }
//}
