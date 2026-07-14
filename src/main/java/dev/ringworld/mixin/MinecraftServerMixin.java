package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla's first-world spawn search inside the finite ring band. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Redirect(
            method = "setupSpawn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/biome/source/util/MultiNoiseUtil$MultiNoiseSampler;findBestSpawnPosition()Lnet/minecraft/util/math/BlockPos;"))
    private static BlockPos ringworld$constrainInitialSpawn(MultiNoiseUtil.MultiNoiseSampler sampler) {
        BlockPos vanilla = sampler.findBestSpawnPosition();
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
