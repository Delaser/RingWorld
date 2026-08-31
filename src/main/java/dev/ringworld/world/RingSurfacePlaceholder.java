package dev.ringworld.world;

import java.util.Arrays;

/** Builds the neutral or seed-derived visual fallback used by an incomplete Atlas. */
public final class RingSurfacePlaceholder {
    public static final int NEUTRAL_GREY = 0x6B706F;

    private RingSurfacePlaceholder() { }

    public static Surface resolve(RingTerrainAtlas atlas) {
        return resolve(atlas, atlas.columns(), atlas.rows());
    }

    public static Surface resolve(RingTerrainAtlas atlas, int targetColumns, int targetRows) {
        return resolve(atlas, targetColumns, targetRows, null);
    }

    public static Surface resolve(RingTerrainAtlas atlas, int targetColumns, int targetRows,
                                  RingTerrainPreview preview) {
        if (targetColumns <= 0 || targetRows <= 0) {
            throw new IllegalArgumentException("placeholder dimensions must be positive");
        }
        if (atlas.isComplete()) {
            throw new IllegalArgumentException("completed atlases use the exact surface path");
        }
        if (preview != null && preview.worldHash() != atlas.worldHash()) {
            throw new IllegalArgumentException("terrain preview belongs to another world");
        }

        int cells = Math.multiplyExact(targetColumns, targetRows);
        int[] argb = new int[cells];
        float[] heights = new float[cells];
        if (preview == null) {
            // The first visible replacement is the coherent 512x16 seed
            // preview, never the retired procedural map or growing smears.
            Arrays.fill(argb, 0xFF000000 | NEUTRAL_GREY);
            Arrays.fill(heights, (float)RingGeometry.SURFACE_Y);
            return new Surface(targetColumns, targetRows, argb, heights);
        }

        for (int row = 0; row < targetRows; row++) {
            int atlasRow = sampleIndex(row, targetRows, atlas.rows());
            for (int column = 0; column < targetColumns; column++) {
                int atlasColumn = sampleIndex(column, targetColumns, atlas.columns());
                int targetIndex = row * targetColumns + column;
                int realColor = atlas.cellColor(atlasColumn, atlasRow);
                if (realColor >= 0) {
                    argb[targetIndex] = 0xFF000000 | realColor;
                    heights[targetIndex] = atlas.cellHeight(atlasColumn, atlasRow);
                } else {
                    // Sample directly at GPU resolution so the 4096x64 stage
                    // is not first collapsed through the 2048x32 Atlas grid.
                    argb[targetIndex] = 0xFF000000
                            | preview.sampleColor(column, row, targetColumns, targetRows);
                    heights[targetIndex] = preview.sampleHeight(
                            column, row, targetColumns, targetRows);
                }
            }
        }
        return new Surface(targetColumns, targetRows, argb, heights);
    }

    private static int sampleIndex(int targetIndex, int targetSize, int sourceSize) {
        return Math.min(sourceSize - 1,
                (int)(((long)targetIndex * 2L + 1L) * sourceSize / (targetSize * 2L)));
    }

    public record Surface(int columns, int rows, int[] argb, float[] heights) { }
}
