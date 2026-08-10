package dev.ringworld.world;

import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;

/** Implemented by the vanilla noise generator mixin once it belongs to a ring Overworld. */
public interface RingWorldGeneratorAccess {
    void ringworld$setGeometry(RingGeometry geometry);
    RingGeometry ringworld$getGeometry();
    void ringworld$setTerrainNoiseMapping(int mappingVersion);
    int ringworld$getTerrainNoiseMapping();
    void ringworld$setWallHeight(int wallHeightBlocks);
    int ringworld$getWallHeight();
    void ringworld$setGuaranteeStronghold(boolean guaranteeStronghold);
    boolean ringworld$guaranteesStronghold();
    Climate.Sampler ringworld$getPeriodicClimateSampler(RandomState randomState);
}
