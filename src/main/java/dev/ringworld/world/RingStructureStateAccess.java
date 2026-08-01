package dev.ringworld.world;

import net.minecraft.world.level.biome.Climate;
import java.util.function.IntBinaryOperator;

/** Loader-neutral bridge for attaching immutable Overworld geometry to structure state. */
public interface RingStructureStateAccess {
    void ringworld$setStructurePolicy(RingGeometry geometry, RingStructurePolicy policy, int generatorSeaLevel,
                                      Climate.Sampler periodicClimateSampler,
                                      IntBinaryOperator oceanFloorHeight);
    RingMonumentResolution ringworld$resolvePendingOceanMonument();
    boolean ringworld$hasCompatibleSavedOceanMonument();
    boolean ringworld$isGuaranteedOceanMonumentCandidate(Object placement, int chunkX, int chunkZ);

}
