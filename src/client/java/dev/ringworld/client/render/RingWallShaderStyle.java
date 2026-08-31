package dev.ringworld.client.render;

import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingWallStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Derives Atlas-wall shader inputs from the real generated block palette. */
final class RingWallShaderStyle {
    private static final int MAX_SHADER_MATERIALS = 5;

    private RingWallShaderStyle() { }

    static Encoded encode(RingWallStyle style, long worldSeed, Level world) {
        List<Run> runs = runs(style, world);
        while (runs.size() > MAX_SHADER_MATERIALS) mergeSmallestAdjacentRun(runs);
        while (runs.size() < MAX_SHADER_MATERIALS) {
            Run last = runs.getLast();
            runs.add(new Run(100, 100, last.rgb()));
        }

        Matrix4f palette = new Matrix4f();
        for (int index = 0; index < 4; index++) {
            Run run = runs.get(index);
            palette.setColumn(index, new Vector4f(
                    red(run.rgb()), green(run.rgb()), blue(run.rgb()), run.end() / 100.0F));
        }
        Run fifth = runs.get(4);
        int seedBits = (int)((worldSeed ^ worldSeed >>> 32) & 31L);
        int metadata = style.pattern().id() * 32 + seedBits;
        int vertexArgb = metadata << 24 | fifth.rgb();
        return new Encoded(palette, vertexArgb);
    }

    private static List<Run> runs(RingWallStyle style, Level world) {
        List<Run> result = new ArrayList<>();
        int start = 0;
        BlockState previous = RingGenerationBoundary.styledRimBlockForRoll(style, 0);
        int previousRgb = mapRgb(previous, world);
        for (int roll = 1; roll <= 100; roll++) {
            BlockState current = roll == 100 ? null
                    : RingGenerationBoundary.styledRimBlockForRoll(style, roll);
            if (roll < 100 && current.equals(previous)) continue;
            result.add(new Run(start, roll, previousRgb));
            start = roll;
            previous = current;
            if (current != null) previousRgb = mapRgb(current, world);
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

    private static int mapRgb(BlockState state, Level world) {
        return state.getMapColor(world, BlockPos.ZERO).col & 0xFFFFFF;
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

    record Encoded(Matrix4f paletteMatrix, int vertexArgb) { }
    private record Run(int start, int end, int rgb) {
        int length() { return end - start; }
    }
}
