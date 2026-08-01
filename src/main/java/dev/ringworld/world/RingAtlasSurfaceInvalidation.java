package dev.ringworld.world;

import java.util.Optional;

/** Loader-neutral mapping from block mutations to canonical atlas cells. */
public final class RingAtlasSurfaceInvalidation {
    private RingAtlasSurfaceInvalidation() { }

    public static Optional<Cell> cellFor(RingGeometry geometry, int sampleStep,
                                         int blockX, int blockZ) {
        if (sampleStep <= 0 || 16 % sampleStep != 0) {
            throw new IllegalArgumentException("atlas sample step must divide one chunk");
        }
        int row = Math.floorDiv(blockZ - geometry.minWidthZ(), sampleStep);
        int rows = divideCeil(geometry.widthBlocks(), sampleStep);
        if (row < 0 || row >= rows) return Optional.empty();
        return Optional.of(new Cell(geometry.wrapBlockX(blockX) / sampleStep, row));
    }

    /** The changed block can alter the sampled top when it reaches the stored top face. */
    public static boolean mayAffectSurface(int changedBlockY, int storedTopFaceY) {
        return (long)changedBlockY + 1L >= storedTopFaceY;
    }

    private static int divideCeil(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    public record Cell(int column, int row) {
        public Cell {
            if (column < 0 || row < 0) throw new IllegalArgumentException("negative atlas cell");
        }
    }
}
