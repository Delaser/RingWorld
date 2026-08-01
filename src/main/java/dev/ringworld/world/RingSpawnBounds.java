package dev.ringworld.world;

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
     * either rim. Canonical X remains untouched because it is periodic.
     */
    public static int constrainInitialSpawnZ(int vanillaZ, RingGeometry geometry) {
        int safeMargin = Math.min(32, Math.max(1, geometry.widthBlocks() / 4));
        int minSafeZ = geometry.minWidthZ() + safeMargin;
        int maxSafeZ = geometry.maxWidthZ() - safeMargin;
        return vanillaZ >= minSafeZ && vanillaZ <= maxSafeZ ? vanillaZ : 0;
    }
}
