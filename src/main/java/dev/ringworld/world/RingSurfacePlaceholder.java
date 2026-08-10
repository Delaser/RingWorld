package dev.ringworld.world;

import java.util.Arrays;

/** Builds the deterministic, opaque visual fallback used by an incomplete Atlas. */
public final class RingSurfacePlaceholder {
    private static final int MAX_BIOME_INFLUENCE_DISTANCE = 64;
    private static final int WATER = 0x315C78;
    private static final int GRASS = 0x526B3B;
    private static final int STONE = 0x676860;
    private static final int SAND = 0xA69A68;

    private RingSurfacePlaceholder() { }

    public static Surface resolve(RingTerrainAtlas atlas) {
        return resolve(atlas, atlas.columns(), atlas.rows());
    }

    public static Surface resolve(RingTerrainAtlas atlas, int targetColumns, int targetRows) {
        if (targetColumns <= 0 || targetRows <= 0) {
            throw new IllegalArgumentException("placeholder dimensions must be positive");
        }
        Surface source = resolveNative(atlas);
        if (targetColumns == source.columns() && targetRows == source.rows()) return source;
        int[] argb = new int[Math.multiplyExact(targetColumns, targetRows)];
        float[] heights = new float[argb.length];
        for (int row = 0; row < targetRows; row++) {
            int sourceRow = Math.min(source.rows() - 1,
                    (int)(((long)row * 2L + 1L) * source.rows() / (targetRows * 2L)));
            for (int column = 0; column < targetColumns; column++) {
                int sourceColumn = Math.min(source.columns() - 1,
                        (int)(((long)column * 2L + 1L) * source.columns()
                                / (targetColumns * 2L)));
                int targetIndex = row * targetColumns + column;
                int sourceIndex = sourceRow * source.columns() + sourceColumn;
                argb[targetIndex] = source.argb()[sourceIndex];
                heights[targetIndex] = source.heights()[sourceIndex];
            }
        }
        return new Surface(targetColumns, targetRows, argb, heights);
    }

    private static Surface resolveNative(RingTerrainAtlas atlas) {
        if (atlas.isComplete()) {
            throw new IllegalArgumentException("completed atlases use the exact surface path");
        }
        int columns = atlas.columns();
        int rows = atlas.rows();
        int cells = Math.multiplyExact(columns, rows);
        int[] nearest = new int[cells];
        int[] distance = new int[cells];
        int[] queue = new int[cells];
        Arrays.fill(nearest, -1);
        Arrays.fill(distance, Integer.MAX_VALUE);
        int head = 0;
        int tail = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (!atlas.hasCell(column, row)) continue;
                nearest[index] = index;
                distance[index] = 0;
                queue[tail++] = index;
            }
        }

        // Multi-source Manhattan dilation is linear, wraps only around X, and
        // never samples mutable Atlas state after the caller's snapshot.
        while (head < tail) {
            int index = queue[head++];
            int row = index / columns;
            int column = index - row * columns;
            int nextDistance = distance[index] + 1;
            if (nextDistance > MAX_BIOME_INFLUENCE_DISTANCE) continue;
            tail = visit(Math.floorMod(column - 1, columns), row, columns,
                    index, nextDistance, nearest, distance, queue, tail);
            tail = visit(Math.floorMod(column + 1, columns), row, columns,
                    index, nextDistance, nearest, distance, queue, tail);
            if (row > 0) tail = visit(column, row - 1, columns,
                    index, nextDistance, nearest, distance, queue, tail);
            if (row + 1 < rows) tail = visit(column, row + 1, columns,
                    index, nextDistance, nearest, distance, queue, tail);
        }

        int[] argb = new int[cells];
        float[] heights = new float[cells];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                int realColor = atlas.cellColor(column, row);
                if (realColor >= 0) {
                    argb[index] = 0xFF000000 | realColor;
                    heights[index] = atlas.cellHeight(column, row);
                    continue;
                }
                int fallback = proceduralColor(atlas.worldHash(), column, row, columns, rows);
                int source = nearest[index];
                if (source >= 0) {
                    int sourceRow = source / columns;
                    int sourceColumn = source - sourceRow * columns;
                    double confidence = biomeInfluence(distance[index]);
                    fallback = blendRgb(fallback, atlas.cellColor(sourceColumn, sourceRow),
                            confidence);
                }
                argb[index] = 0xFF000000 | fallback;
                heights[index] = (float)RingGeometry.SURFACE_Y;
            }
        }
        return new Surface(columns, rows, argb, heights);
    }

    static int proceduralColor(long worldHash, int column, int row, int columns, int rows) {
        long mixedHash = worldHash ^ Long.rotateLeft(worldHash, 23)
                ^ 0x9E3779B97F4A7C15L;
        double phase = (mixedHash & 0xFFFF) * (Math.PI * 2.0 / 65536.0);
        double around = Math.PI * 2.0 * (column + 0.5) / columns;
        double across = Math.PI * (row + 0.5) / rows;
        double field = Math.sin(around * 3.0 + phase)
                + 0.62 * Math.sin(around * 7.0 - across * 1.7 + phase * 0.37)
                + 0.38 * Math.cos(around * 13.0 + across * 2.3 - phase * 0.61);
        if (field < -0.55) return WATER;
        if (field < -0.28) return blendRgb(WATER, SAND, (field + 0.55) / 0.27);
        if (field > 1.25) return STONE;
        return blendRgb(GRASS, STONE, Math.max(0.0, (field - 0.72) / 0.53) * 0.45);
    }

    /** Smooth confidence used to carry a real generated terrain palette into unknown cells. */
    static double biomeInfluence(int distance) {
        if (distance <= 0) return 1.0;
        if (distance > MAX_BIOME_INFLUENCE_DISTANCE) return 0.0;
        double linear = 1.0 - (double)distance / (MAX_BIOME_INFLUENCE_DISTANCE + 1.0);
        double smooth = linear * linear * (3.0 - 2.0 * linear);
        return smooth * 0.92;
    }

    private static int visit(int column, int row, int columns, int from, int candidateDistance,
                             int[] nearest, int[] distance, int[] queue, int tail) {
        int index = row * columns + column;
        if (candidateDistance >= distance[index]) return tail;
        distance[index] = candidateDistance;
        nearest[index] = nearest[from];
        queue[tail] = index;
        return tail + 1;
    }

    private static int blendRgb(int first, int second, double amount) {
        double t = Math.max(0.0, Math.min(1.0, amount));
        int red = (int)Math.round((first >> 16 & 0xFF) * (1.0 - t)
                + (second >> 16 & 0xFF) * t);
        int green = (int)Math.round((first >> 8 & 0xFF) * (1.0 - t)
                + (second >> 8 & 0xFF) * t);
        int blue = (int)Math.round((first & 0xFF) * (1.0 - t)
                + (second & 0xFF) * t);
        return red << 16 | green << 8 | blue;
    }

    public record Surface(int columns, int rows, int[] argb, float[] heights) { }
}
