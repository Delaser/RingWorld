package dev.ringworld.world;

/** Thread-confined coordinate identity for vanilla surface-system sampling. */
public final class RingSurfaceSamplingContext {
    private static final ThreadLocal<RingNoiseCoordinates> ACTIVE = new ThreadLocal<>();

    private RingSurfaceSamplingContext() { }

    public static void run(RingGeometry geometry, int mappingVersion, Runnable operation) {
        if (mappingVersion != RingTerrainNoiseMapping.ANNULAR_COMPLETE) {
            operation.run();
            return;
        }
        RingNoiseCoordinates previous = ACTIVE.get();
        ACTIVE.set(RingNoiseCoordinates.forGeometry(geometry, mappingVersion));
        try {
            operation.run();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    /** Maps a vanilla noise call whose inputs were multiplied by {@code scale}. */
    public static Coordinates mapScaled(double x, double z, double scale) {
        RingNoiseCoordinates coordinates = ACTIVE.get();
        if (coordinates == null) return new Coordinates(x, z);
        int sourceX = (int)Math.round(x / scale);
        int sourceZ = (int)Math.round(z / scale);
        return new Coordinates(coordinates.noiseX(sourceX, sourceZ) * scale,
                coordinates.noiseZ(sourceX, sourceZ) * scale);
    }

    public static BlockCoordinates mapBlock(int x, int z) {
        RingNoiseCoordinates coordinates = ACTIVE.get();
        return coordinates == null ? new BlockCoordinates(x, z)
                : new BlockCoordinates(coordinates.noiseX(x, z), coordinates.noiseZ(x, z));
    }

    public record Coordinates(double x, double z) { }
    public record BlockCoordinates(int x, int z) { }
}
