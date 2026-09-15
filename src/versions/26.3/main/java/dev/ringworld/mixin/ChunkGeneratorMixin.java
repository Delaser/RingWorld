package dev.ringworld.mixin;

import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingIndustrialElementPlacement;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps finite-band mutations in the asynchronous generation pipeline. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorFeatures(WorldGenLevel world, ChunkAccess chunk,
                                                StructureManager structures, CallbackInfo ci) {
        RingWorldGeneratorAccess access = ringworld$access();
        if (access == null) return;
        RingGeometry geometry = access.ringworld$getGeometry();
        if (geometry == null || !RingGenerationBoundary.isExterior(chunk, geometry)) return;
        // An adjacent interior feature can spill a few blocks into this chunk
        // before its own feature stage. Clear those off-thread, then leave the
        // exterior empty without scheduling live chunk updates.
        RingGenerationBoundary.clearExterior(chunk);
        ci.cancel();
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void ringworld$buildRimAfterFeatures(WorldGenLevel world, ChunkAccess chunk,
                                                 StructureManager structures, CallbackInfo ci) {
        RingWorldGeneratorAccess access = ringworld$access();
        if (access == null) return;
        RingGeometry geometry = access.ringworld$getGeometry();
        if (geometry != null) {
            RingGenerationBoundary.installRim(chunk, geometry, access.ringworld$getWallHeight(),
                    access.ringworld$getWallStyle(), world.getSeed());
            RingGenerationBoundary.installUnderside(chunk, geometry,
                    access.ringworld$getWallStyle(), world.getSeed());
            RingIndustrialElementPlacement.install(chunk, geometry, access.ringworld$getWallHeight(),
                    access.ringworld$getWallStyle(), world.getSeed(), (x, z) ->
                            ((ChunkGenerator)(Object)this).getBaseHeight(x, z,
                                    Heightmap.Types.WORLD_SURFACE_WG, world,
                                    world.getLevel().getChunkSource().randomState()));
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "doCreateBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;)V"))
    private void ringworld$riverBiome(ChunkAccess chunk, net.minecraft.world.level.biome.BiomeResolver resolver,
            net.minecraft.world.level.levelgen.blending.Blender blender,
            net.minecraft.world.level.levelgen.RandomState state, ChunkAccess originalChunk) {
        RingWorldGeneratorAccess access = ringworld$access();
        if (access == null || access.ringworld$getGeometry() == null
                || !access.ringworld$getGenerationSettings().continuousRiver()) {
            chunk.fillBiomesFromNoise(resolver);
            return;
        }
        var river = ((ChunkGenerator)(Object)this).getBiomeSource().possibleBiomes().stream()
                .filter(holder -> holder.is(net.minecraft.world.level.biome.Biomes.RIVER))
                .findFirst().orElse(null);
        if (river == null) {
            chunk.fillBiomesFromNoise(resolver);
            return;
        }
        var macro = new dev.ringworld.world.RingMacroTerrain(access.ringworld$getGeometry(),
                state.seed(), access.ringworld$getGenerationSettings());
        chunk.fillBiomesFromNoise((x, y, z) -> macro.riverInfluence(x << 2, z << 2) >= 0.72
                ? river : resolver.getNoiseBiome(x, y, z));
    }

    private RingWorldGeneratorAccess ringworld$access() {
        return (Object) this instanceof RingWorldGeneratorAccess access ? access : null;
    }
}
