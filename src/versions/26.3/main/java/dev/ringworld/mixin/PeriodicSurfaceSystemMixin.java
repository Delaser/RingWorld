package dev.ringworld.mixin;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingSurfaceSamplingContext;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Set;
/** Preserve the scoped surface-noise mapping through 26.3's material system. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class PeriodicSurfaceSystemMixin implements RingWorldGeneratorAccess {
    @Redirect(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/material/MaterialSystem;buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;Ljava/util/Set;)V"))
    private void ringworld$surface(MaterialSystem system, RandomState state, BiomeManager biomes,
            WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noise, MaterialRule rule, Set<Holder<Biome>> possible) {
        Runnable build = () -> system.buildSurface(state, biomes, context, chunk, noise, rule, possible);
        if (ringworld$getGeometry() == null) build.run();
        else RingSurfaceSamplingContext.run(ringworld$getGeometry(), ringworld$getTerrainNoiseMapping(), build);
    }
}
