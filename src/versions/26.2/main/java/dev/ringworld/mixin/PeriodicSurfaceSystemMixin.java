package dev.ringworld.mixin;

import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingSurfaceSamplingContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Set;

/** Version-owned surface ABI; the coordinate policy remains shared. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class PeriodicSurfaceSystemMixin implements RingWorldGeneratorAccess {

    /** Keeps every vanilla surface-only noise on the saved terrain mapping. */
    @Redirect(
            method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/levelgen/blending/Blender;Ljava/util/Set;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;ZLnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;Ljava/util/Set;)V"))
    private void ringworld$periodicSurfaceSystem(
            SurfaceSystem surfaceSystem, RandomState randomState, BiomeManager biomeManager,
            boolean legacyRandom, WorldGenerationContext context,
            ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource rules, Set<Holder<Biome>> biomes) {
        if (ringworld$getGeometry() == null) {
            surfaceSystem.buildSurface(randomState, biomeManager, legacyRandom,
                    context, chunk, noiseChunk, rules, biomes);
            return;
        }
        RingSurfaceSamplingContext.run(ringworld$getGeometry(), ringworld$getTerrainNoiseMapping(),
                () -> surfaceSystem.buildSurface(randomState, biomeManager, legacyRandom,
                        context, chunk, noiseChunk, rules, biomes));
    }

}
