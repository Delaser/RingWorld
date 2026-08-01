package dev.ringworld.world;

/**
 * Pure colour/filtering helpers for the atlas-backed distant surface.
 *
 * <p>The client owns the GPU resources, but keeping the resampling rules here
 * makes the circumference seam and width-edge behaviour independently
 * testable.</p>
 */
public final class RingSurfaceLod {
    private RingSurfaceLod() { }

    /** Encodes atlas coverage as transparency without inventing missing terrain. */
    public static int surfaceArgb(int rgb, double coverage) {
        if (!Double.isFinite(coverage)) {
            throw new IllegalArgumentException("surface coverage must be finite");
        }
        if (rgb < 0 || coverage <= 0.0) return 0;
        int alpha = clampChannel(clamp(coverage, 0.0, 1.0) * 255.0);
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /**
     * Applies the average luminance contributed by a block texture to a biome
     * tint. Vanilla multiplies grass, foliage, and water tint by textured
     * pixels; using the raw tint as a finished atlas colour makes the LOD
     * unnaturally bright.
     */
    public static int applyTextureLuminance(int rgb, double luminance) {
        if (!Double.isFinite(luminance) || luminance < 0.0) {
            throw new IllegalArgumentException("texture luminance must be finite and non-negative");
        }
        return scaleRgb(rgb, luminance);
    }

    /**
     * Uses a block's map colour when a dedicated server cannot evaluate
     * Minecraft's client-loaded biome colour maps.
     *
     * <p>{@code GrassColors} and {@code FoliageColors} allocate zero-filled
     * lookup tables in common code; the client resource reload replaces them,
     * but a dedicated server never does. Treating that zero as a real biome
     * tint made newly generated remote atlases overwhelmingly black.</p>
     */
    public static int applyTextureLuminanceWithMapFallback(
            int biomeTint, int mapColor, double luminance) {
        int tint = biomeTint & 0xFFFFFF;
        int fallback = mapColor & 0xFFFFFF;
        return applyTextureLuminance(tint == 0 && fallback != 0 ? fallback : tint, luminance);
    }

    /**
     * Adds restrained relief shading to a top-surface map colour.
     *
     * <p>The central star is locally overhead, so the primary term depends on
     * slope magnitude rather than an arbitrary compass-facing light. A small
     * local-height term keeps ridges and valleys readable after mipmapping.</p>
     */
    public static int shadeSurfaceColor(int rgb, double centerHeight,
                                        double leftHeight, double rightHeight,
                                        double lowerHeight, double upperHeight,
                                        double sampleSpacingX, double sampleSpacingZ) {
        if (!(sampleSpacingX > 0.0) || !(sampleSpacingZ > 0.0)) {
            throw new IllegalArgumentException("surface sample spacing must be positive");
        }
        double gradientX = (rightHeight - leftHeight) / (2.0 * sampleSpacingX);
        double gradientZ = (upperHeight - lowerHeight) / (2.0 * sampleSpacingZ);
        double normalY = 1.0 / Math.sqrt(1.0 + gradientX * gradientX + gradientZ * gradientZ);
        double neighbourMean = (leftHeight + rightHeight + lowerHeight + upperHeight) * 0.25;
        double localRelief = clamp((centerHeight - neighbourMean) / 12.0, -1.0, 1.0);
        double brightness = clamp(0.82 + 0.18 * normalY + 0.045 * localRelief, 0.72, 1.04);
        return scaleRgb(rgb, brightness);
    }

    /**
     * Builds one box-filtered mip level. X is periodic and Z clamps at the
     * finite band edges, matching the sampler used by the renderer.
     */
    public static int[] buildNextMipArgb(int[] source, int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0
                || source.length != sourceWidth * sourceHeight) {
            throw new IllegalArgumentException("invalid source mip dimensions");
        }
        int targetWidth = Math.max(1, sourceWidth >> 1);
        int targetHeight = Math.max(1, sourceHeight >> 1);
        int[] target = new int[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            int y0 = Math.min(sourceHeight - 1, y * 2);
            int y1 = Math.min(sourceHeight - 1, y0 + 1);
            for (int x = 0; x < targetWidth; x++) {
                int x0 = Math.floorMod(x * 2, sourceWidth);
                int x1 = Math.floorMod(x0 + 1, sourceWidth);
                target[y * targetWidth + x] = averageArgb(
                        source[y0 * sourceWidth + x0],
                        source[y0 * sourceWidth + x1],
                        source[y1 * sourceWidth + x0],
                        source[y1 * sourceWidth + x1]);
            }
        }
        return target;
    }

    private static int averageArgb(int first, int second, int third, int fourth) {
        int alpha = ((first >>> 24) + (second >>> 24)
                + (third >>> 24) + (fourth >>> 24) + 2) >> 2;
        int alphaTotal = (first >>> 24) + (second >>> 24)
                + (third >>> 24) + (fourth >>> 24);
        if (alphaTotal == 0) return 0;
        int red = ((first >> 16 & 0xFF) * (first >>> 24)
                + (second >> 16 & 0xFF) * (second >>> 24)
                + (third >> 16 & 0xFF) * (third >>> 24)
                + (fourth >> 16 & 0xFF) * (fourth >>> 24)
                + alphaTotal / 2) / alphaTotal;
        int green = ((first >> 8 & 0xFF) * (first >>> 24)
                + (second >> 8 & 0xFF) * (second >>> 24)
                + (third >> 8 & 0xFF) * (third >>> 24)
                + (fourth >> 8 & 0xFF) * (fourth >>> 24)
                + alphaTotal / 2) / alphaTotal;
        int blue = ((first & 0xFF) * (first >>> 24)
                + (second & 0xFF) * (second >>> 24)
                + (third & 0xFF) * (third >>> 24)
                + (fourth & 0xFF) * (fourth >>> 24)
                + alphaTotal / 2) / alphaTotal;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int scaleRgb(int rgb, double scale) {
        int red = clampChannel((rgb >> 16 & 0xFF) * scale);
        int green = clampChannel((rgb >> 8 & 0xFF) * scale);
        int blue = clampChannel((rgb & 0xFF) * scale);
        return red << 16 | green << 8 | blue;
    }

    private static int clampChannel(double value) {
        return Math.max(0, Math.min(255, (int)Math.round(value)));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
