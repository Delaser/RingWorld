package dev.ringworld.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ringworld.world.*;

/** Worker-only wall colour atlas. Four strips: two inner faces, then two outer faces. */
final class RingWallTexture {
    static NativeImage build(RingTerrainAtlas atlas, RingWallStyle style, long seed,
                              int bottom, int top, int columns, int[] palette, java.util.function.BooleanSupplier cancelled) {
        return build(atlas, style, seed, bottom, top, columns, palette, cancelled,
                Math.min(512, Math.max(1, top - bottom)));
    }

    /** Four independent strips; stop before a mip would merge different wall faces. */
    static NativeImage[] buildMipmapped(RingTerrainAtlas atlas, RingWallStyle style, long seed,
                                        int bottom, int top, int columns, int[] palette,
                                        java.util.function.BooleanSupplier cancelled) {
        int rows = 1;
        while (rows < Math.min(512, Math.max(1, top - bottom))) rows <<= 1;
        NativeImage[] levels = new NativeImage[Integer.numberOfTrailingZeros(rows) + 1];
        try {
            levels[0] = build(atlas, style, seed, bottom, top, columns, palette, cancelled, rows);
            int width = columns;
            int height = rows * 4;
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) pixels[y * width + x] = levels[0].getPixel(x, y);
            }
            for (int level = 1; level < levels.length; level++) {
                if (cancelled.getAsBoolean())
                    throw new java.util.concurrent.CancellationException("obsolete wall texture");
                // Power-of-two strip heights keep every 2x2 filter inside one face.
                // Alpha-weighted RGB prevents decay holes from darkening the wall.
                pixels = RingSurfaceLod.buildNextMipArgb(pixels, width, height);
                width = Math.max(1, width >> 1);
                height >>= 1;
                NativeImage image = new NativeImage(width, height, false);
                levels[level] = image;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) image.setPixel(x, y, pixels[y * width + x]);
                }
            }
            return levels;
        } catch (RuntimeException | Error failure) {
            for (NativeImage image : levels) if (image != null) image.close();
            throw failure;
        }
    }

    private static NativeImage build(RingTerrainAtlas atlas, RingWallStyle style, long seed,
                                      int bottom, int top, int columns, int[] palette,
                                      java.util.function.BooleanSupplier cancelled, int rows) {
        int height = Math.max(1, top - bottom);
        NativeImage image = new NativeImage(columns, rows * 4, false);
        try {
            var geometry = atlas.geometry();
            for (int strip = 0; strip < 4; strip++) {
                int side = strip % 2;
                boolean inner = strip < 2;
                int depth = inner ? style.thicknessBlocks() - 1 : 0;
                int inward = side == 0 ? 1 : -1;
                int edge = side == 0 ? geometry.minWidthZ() : geometry.maxWidthZ();
                int anchorZ = edge + inward * (style.thicknessBlocks() + 3);
                for (int col = 0; col < columns; col++) {
                    if ((col & 63) == 0 && cancelled.getAsBoolean())
                        throw new java.util.concurrent.CancellationException("obsolete wall texture");
                    int x = (int)((col + 0.5) * geometry.circumferenceBlocks() / columns);
                    var feature = RingIndustrialElements.feature(x, geometry.circumferenceBlocks(), seed, side);
                    int base = bottom;
                    for (int dx : new int[]{-18, 0, 18}) {
                        var sample = atlas.sample(feature.centerX() + dx, anchorZ);
                        base = Math.max(base, sample.coverage() > 0 ? (int)Math.ceil(sample.height()) + 1 : 64);
                    }
                    for (int row = 0; row < rows; row++) {
                        int y = bottom + (int)((row + 0.5) * height / rows);
                        int color = 0;
                        if (RingWallPattern.blockPresent(style, x, y, depth, top, geometry.circumferenceBlocks(), seed)) {
                            int roll = RingWallPattern.materialRoll(style, x, y, depth, geometry.circumferenceBlocks(), seed);
                            float shade = 1.0F;
                            if (inner && RingIndustrialElements.enabled(style)) {
                                for (int relief = 3; relief >= -Math.min(3, style.thicknessBlocks() - 1); relief--) {
                                    int motif = RingIndustrialElements.roll(feature, y - base, top - base, relief, style.thicknessBlocks());
                                    if (motif >= 0) { roll = motif; shade = relief < 0 ? 0.48F : 1.0F; break; }
                                    if (motif == RingIndustrialElements.UNCHANGED && relief == 0) break;
                                }
                            }
                            int rgb = palette[roll];
                            color = 0xFF000000 | (int)((rgb >>> 16 & 255) * shade) << 16
                                    | (int)((rgb >>> 8 & 255) * shade) << 8 | (int)((rgb & 255) * shade);
                        }
                        image.setPixel(col, strip * rows + row, color);
                    }
                }
            }
            return image;
        } catch (RuntimeException | Error failure) { image.close(); throw failure; }
    }
}
