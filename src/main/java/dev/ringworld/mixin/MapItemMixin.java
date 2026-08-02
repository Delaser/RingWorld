package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingMapCompassSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Keeps vanilla filled-map sampling in the map's nearest periodic X image. */
@Mixin(MapItem.class)
abstract class MapItemMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double ringworld$nearestMapPlayerImage(Entity player, Level level,
                                                    Entity owner, MapItemSavedData data) {
        RingGeometry geometry = geometry(level);
        return geometry == null ? player.getX()
                : RingMapCompassSupport.nearestMapImageX(geometry, player.getX(), data.centerX);
    }

    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk ringworld$canonicalMapSampleChunk(Level level, int imageChunkX, int chunkZ) {
        RingGeometry geometry = geometry(level);
        return level.getChunk(geometry == null ? imageChunkX
                : RingMapCompassSupport.canonicalMapSampleChunkX(geometry, imageChunkX), chunkZ);
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;checkBanners(Lnet/minecraft/world/level/BlockGetter;II)V"))
    private void ringworld$canonicalBannerSampleX(Args args) {
        BlockGetter level = args.get(0);
        if (!(level instanceof Level mapLevel)) return;
        RingGeometry geometry = geometry(mapLevel);
        if (geometry != null) args.set(1, geometry.wrapBlockX(args.get(1)));
    }

    private static RingGeometry geometry(Level level) {
        return level instanceof ServerLevel world && world.dimension() == Level.OVERWORLD
                ? RingWorldServer.geometryFor(world) : null;
    }
}
