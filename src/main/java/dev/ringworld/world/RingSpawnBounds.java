package dev.ringworld.world;

import net.minecraft.core.BlockPos;

/**
 * Finite-width spawn policy shared by first-world bootstrap adapters and
 * loader-neutral dimension tests.
 *
 * <p>Initial spawn is chosen before an Overworld has saved settings, so the
 * platform adapter supplies the validated bootstrap geometry for that one
 * creation-time call. Once settings exist, normal runtime ownership remains
 * with {@link RingWorldSettings}; this helper never reads configuration.</p>
 */
public final class RingSpawnBounds {
    private RingSpawnBounds() { }

    /**
     * Keeps a vanilla biome-selected Z inside the finite band and away from
     * either rim.
     */
    public static int constrainInitialSpawnZ(int vanillaZ, RingGeometry geometry) {
        int safeMargin = Math.min(32, Math.max(1, geometry.widthBlocks() / 4));
        int minSafeZ = geometry.minWidthZ() + safeMargin;
        int maxSafeZ = geometry.maxWidthZ() - safeMargin;
        return vanillaZ >= minSafeZ && vanillaZ <= maxSafeZ ? vanillaZ : 0;
    }

    /**
     * Converts the final new-world spawn candidate into the one canonical
     * persistent plane. This applies after vanilla's local spawn-chunk spiral,
     * which can select a seam alias even when its original climate candidate
     * was canonical.
     */
    public static BlockPos canonicalInitialSpawn(BlockPos candidate, RingGeometry geometry) {
        int canonicalX = geometry.wrapBlockX(candidate.getX());
        return canonicalX == candidate.getX() ? candidate.immutable()
                : new BlockPos(canonicalX, candidate.getY(), candidate.getZ());
    }
}
