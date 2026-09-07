package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Loader-neutral wall palette and deterministic shader metadata. */
public final class RingWallShaderStyle {
    private static final int MAX_SHADER_MATERIALS = 5;

    private RingWallShaderStyle() { }

    public static Encoded encode(RingWallStyle style, long worldSeed, Level world) {
        return encode(style, worldSeed,
                state -> state.getMapColor(world, BlockPos.ZERO).col & 0xFFFFFF);
    }

    static Encoded encode(RingWallStyle style, long worldSeed,
                          ToIntFunction<BlockState> colorLookup) {
        if (style == null) throw new IllegalArgumentException("wall style is required");
        if (colorLookup == null) throw new IllegalArgumentException("colour lookup is required");
        List<Run> runs = runs(style, colorLookup);
        while (runs.size() > MAX_SHADER_MATERIALS) mergeSmallestAdjacentRun(runs);
        while (runs.size() < MAX_SHADER_MATERIALS) {
            Run last = runs.getLast();
            runs.add(new Run(100, 100, last.rgb()));
        }
        float[] palette = new float[16];
        for (int index = 0; index < 4; index++) {
            Run run = runs.get(index);
            palette[index * 4] = red(run.rgb());
            palette[index * 4 + 1] = green(run.rgb());
            palette[index * 4 + 2] = blue(run.rgb());
            palette[index * 4 + 3] = run.end() / 100.0F;
        }
        Run fifth = runs.get(4);
        int seedBits = (int)((worldSeed ^ worldSeed >>> 32) & 31L);
        int packedPatternSeed = style.pattern().id() * 32 + seedBits;
        int vertexArgb = packedPatternSeed << 24 | fifth.rgb();
        return new Encoded(palette, vertexArgb, style.decayPercent() / 100.0F,
                style.pattern().id(), seedBits);
    }

    public static int[] paletteColors(RingWallStyle style, Level world) {
        int[] colors = new int[100];
        for (int roll = 0; roll < colors.length; roll++) {
            colors[roll] = RingGenerationBoundary.styledRimBlockForRoll(style, roll)
                    .getMapColor(world, BlockPos.ZERO).col & 0xFFFFFF;
        }
        return colors;
    }

    private static List<Run> runs(RingWallStyle style,
                                  ToIntFunction<BlockState> colorLookup) {
        List<Run> result = new ArrayList<>();
        int start = 0;
        BlockState previous = RingGenerationBoundary.styledRimBlockForRoll(style, 0);
        int previousRgb = colorLookup.applyAsInt(previous) & 0xFFFFFF;
        for (int roll = 1; roll <= 100; roll++) {
            BlockState current = roll == 100 ? null
                    : RingGenerationBoundary.styledRimBlockForRoll(style, roll);
            if (roll < 100 && current.equals(previous)) continue;
            result.add(new Run(start, roll, previousRgb));
            start = roll;
            previous = current;
            if (current != null) previousRgb = colorLookup.applyAsInt(current) & 0xFFFFFF;
        }
        return result;
    }

    private static void mergeSmallestAdjacentRun(List<Run> runs) {
        int smallest = 0;
        for (int index = 1; index < runs.size(); index++) {
            if (runs.get(index).length() < runs.get(smallest).length()) smallest = index;
        }
        int neighbour = smallest == 0 ? 1
                : smallest == runs.size() - 1 ? smallest - 1
                : runs.get(smallest - 1).length() <= runs.get(smallest + 1).length()
                        ? smallest - 1 : smallest + 1;
        int first = Math.min(smallest, neighbour);
        int second = Math.max(smallest, neighbour);
        Run a = runs.get(first), b = runs.get(second);
        int total = a.length() + b.length();
        int rgb = rgb(
                Math.round((red255(a.rgb()) * a.length() + red255(b.rgb()) * b.length())
                        / (float)total),
                Math.round((green255(a.rgb()) * a.length() + green255(b.rgb()) * b.length())
                        / (float)total),
                Math.round((blue255(a.rgb()) * a.length() + blue255(b.rgb()) * b.length())
                        / (float)total));
        runs.set(first, new Run(a.start(), b.end(), rgb));
        runs.remove(second);
    }

    private static float red(int rgb) { return red255(rgb) / 255.0F; }
    private static float green(int rgb) { return green255(rgb) / 255.0F; }
    private static float blue(int rgb) { return blue255(rgb) / 255.0F; }
    private static int red255(int rgb) { return rgb >>> 16 & 255; }
    private static int green255(int rgb) { return rgb >>> 8 & 255; }
    private static int blue255(int rgb) { return rgb & 255; }
    private static int rgb(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
    }

    public record Encoded(float[] paletteColumns, int vertexArgb, float decay,
                          int patternId, int seedBits) {
        public Encoded {
            paletteColumns = paletteColumns.clone();
        }
        @Override public float[] paletteColumns() { return paletteColumns.clone(); }
    }

    private record Run(int start, int end, int rgb) {
        int length() { return end - start; }
    }
}
