package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldStorageAccess;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Owns first-world spawn bounds and the read-only dimension-storage bridge. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin implements RingWorldStorageAccess {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    @Override
    public Path ringworld$getDimensionPath(ResourceKey<Level> dimension) {
        return storageSource.getDimensionPath(dimension);
    }

    @Redirect(
            method = "setInitialSpawn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Climate$Sampler;findSpawnPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos ringworld$constrainInitialSpawn(Climate.Sampler sampler) {
        BlockPos vanilla = sampler.findSpawnPosition();
        RingWorldConfig config = RingWorldConfig.load();
        RingGeometry geometry = new RingGeometry(config.widthBlocks(), config.circumferenceBlocks());

        // Preserve vanilla's preferred biome along the periodic axis. Across
        // the finite width, fall back to the middle of the band whenever the
        // noise sampler selected the exterior or the rim's immediate margin.
        // The following vanilla spawn-locating spiral is then free to find a
        // safe surface while staying well away from either edge.
        int safeMargin = Math.min(32, Math.max(1, geometry.widthBlocks() / 4));
        int minSafeZ = geometry.minWidthZ() + safeMargin;
        int maxSafeZ = geometry.maxWidthZ() - safeMargin;
        int z = vanilla.getZ() >= minSafeZ && vanilla.getZ() <= maxSafeZ ? vanilla.getZ() : 0;
        return new BlockPos(vanilla.getX(), vanilla.getY(), z);
    }
}
