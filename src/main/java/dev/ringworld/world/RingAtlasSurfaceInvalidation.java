package dev.ringworld.world;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Expands one Atlas cell by a block-space light radius. X is periodic and
     * Z remains bounded by the finite ring width.
     */
    public static Set<Cell> cellsWithinRadius(RingGeometry geometry, int sampleStep,
                                              Cell center, int radiusBlocks) {
        if (sampleStep <= 0 || 16 % sampleStep != 0) {
            throw new IllegalArgumentException("atlas sample step must divide one chunk");
        }
        if (radiusBlocks < 0) {
            throw new IllegalArgumentException("atlas invalidation radius must be non-negative");
        }
        int columns = divideCeil(geometry.circumferenceBlocks(), sampleStep);
        int rows = divideCeil(geometry.widthBlocks(), sampleStep);
        if (center.column() >= columns || center.row() >= rows) {
            throw new IllegalArgumentException("atlas invalidation center is outside the atlas");
        }
        int radiusCells = divideCeil(radiusBlocks, sampleStep);
        Set<Cell> cells = new LinkedHashSet<>();
        for (int dz = -radiusCells; dz <= radiusCells; dz++) {
            int row = center.row() + dz;
            if (row < 0 || row >= rows) continue;
            for (int dx = -radiusCells; dx <= radiusCells; dx++) {
                cells.add(new Cell(Math.floorMod(center.column() + dx, columns), row));
            }
        }
        return Set.copyOf(cells);
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
