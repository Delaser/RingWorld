package dev.ringworld.world;
import net.minecraft.world.level.levelgen.NoiseRouter;
/** World-owned, immutable compilation policy installed before generation starts. */
public interface RingRandomStateAccess {
    void ringworld$configureNoise(RingGeometry geometry, int mapping,
            RingWorldGenerationSettings settings, long seed, int seaLevel, NoiseRouter router);
}
