package dev.ringworld.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Repeatable CPU/storage benchmark for issue #69's production sample-step candidates. */
public final class RingAtlasFidelityBenchmark {
    private static final RingGeometry PRODUCTION = new RingGeometry(256, 16_384);
    private static final int[] SAMPLE_STEPS = {8, 4, 2, 1};
    private static final long HASH = 0x69A7_1A5L;

    private RingAtlasFidelityBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("ringworld-atlas-fidelity-");
        List<String> lines = new ArrayList<>();
        lines.add("# Production atlas fidelity benchmark");
        lines.add("");
        lines.add("Geometry: 16,384-by-256 blocks. Times are one local JVM sample; exact byte budgets are deterministic.");
        lines.add("");
        lines.add("| Step | Cells | Samples/chunk | Raw atlas | Wire | Gzip | Fill | Save | Load | Tiles | CPU texture/mips |");
        lines.add("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        try {
            warmUp();
            for (int step : SAMPLE_STEPS) {
                Result result = runCase(directory, step);
                lines.add(result.markdown());
            }
            lines.add("");
            RingRenderProfile render = RingRenderProfile.create(PRODUCTION, 28 * 16.0);
            lines.add(String.format(Locale.ROOT,
                    "Expanded GPU output is profile-independent: %,dx%,d texture, %,d texture bytes with mips, "
                            + "%,d mesh vertices, %,d mesh bytes, and %,d CPU build-scratch bytes.",
                    render.textureColumns(), render.textureRows(), render.estimatedGpuTextureBytes(),
                    render.vertexCount(), render.estimatedGpuMeshBytes(),
                    render.estimatedTextureBuildScratchBytes()));
            Path report = Path.of("build", "reports", "ringworld", "atlas-fidelity.md");
            Files.createDirectories(report.getParent());
            Files.writeString(report, String.join(System.lineSeparator(), lines) + System.lineSeparator());
            lines.forEach(System.out::println);
            System.out.println("Report: " + report.toAbsolutePath());
        } finally {
            deleteTree(directory);
        }
    }

    private static Result runCase(Path directory, int step) throws Exception {
        System.gc();
        long started = System.nanoTime();
        RingTerrainAtlas atlas = new RingTerrainAtlas(PRODUCTION, HASH, step);
        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                int height = 58 + Math.floorMod(column / 9 + row / 5 + (column * 31 ^ row * 17), 37);
                int color = terrainColor(column, row, height);
                atlas.putCell(column, row, height, color);
            }
        }
        long fillNanos = System.nanoTime() - started;

        Path cache = directory.resolve("step-" + step + ".rwat.gz");
        started = System.nanoTime();
        atlas.save(cache);
        long saveNanos = System.nanoTime() - started;
        long gzipBytes = Files.size(cache);

        started = System.nanoTime();
        RingTerrainAtlas loaded = RingTerrainAtlas.load(cache, PRODUCTION, HASH);
        long loadNanos = System.nanoTime() - started;
        if (!loaded.isComplete()) throw new IllegalStateException("benchmark cache was incomplete");

        started = System.nanoTime();
        long encodedTileBytes = 0L;
        for (int tileZ = 0; tileZ < atlas.tileRows(); tileZ++) {
            for (int tileX = 0; tileX < atlas.tileColumns(); tileX++) {
                encodedTileBytes += atlas.encodeTile(tileX, tileZ).length;
            }
        }
        long tileNanos = System.nanoTime() - started;
        if (encodedTileBytes != atlas.estimatedWireBytes()) {
            throw new IllegalStateException("wire estimate diverged from encoded tiles");
        }

        started = System.nanoTime();
        int checksum = buildClientTextureAndMips(loaded);
        long textureNanos = System.nanoTime() - started;
        if (checksum == 0) throw new IllegalStateException("benchmark output was optimized away");

        return new Result(step, atlas.cellCount(), 256 / (step * step), atlas.estimatedMemoryBytes(),
                encodedTileBytes, gzipBytes, fillNanos, saveNanos, loadNanos, tileNanos, textureNanos);
    }

    private static int buildClientTextureAndMips(RingTerrainAtlas atlas) {
        RingRenderProfile profile = RingRenderProfile.create(PRODUCTION, 28 * 16.0);
        int width = profile.textureColumns();
        int height = profile.textureRows();
        int[] pixels = new int[width * height];
        float[] heights = new float[pixels.length];
        double spacingX = (double)PRODUCTION.circumferenceBlocks() / width;
        double spacingZ = (double)PRODUCTION.widthBlocks() / height;
        for (int row = 0; row < height; row++) {
            double z = PRODUCTION.minWidthZ() + (row + 0.5) * spacingZ;
            for (int column = 0; column < width; column++) {
                RingTerrainAtlas.SurfaceSample sample = atlas.sample((column + 0.5) * spacingX, z);
                int index = row * width + column;
                heights[index] = (float)sample.height();
                pixels[index] = RingSurfaceLod.surfaceArgb(sample.color(), sample.coverage());
            }
        }
        for (int row = 0; row < height; row++) {
            int lowerRow = Math.max(0, row - 1);
            int upperRow = Math.min(height - 1, row + 1);
            for (int column = 0; column < width; column++) {
                int leftColumn = Math.floorMod(column - 1, width);
                int rightColumn = Math.floorMod(column + 1, width);
                int index = row * width + column;
                int alpha = pixels[index] >>> 24;
                float center = heights[index];
                int shaded = RingSurfaceLod.shadeSurfaceColor(
                        pixels[index], center,
                        heights[row * width + leftColumn], heights[row * width + rightColumn],
                        heights[lowerRow * width + column], heights[upperRow * width + column],
                        spacingX, spacingZ);
                pixels[index] = alpha << 24 | shaded;
            }
        }
        int checksum = pixels[pixels.length / 2];
        while (Math.min(width, height) > 1) {
            pixels = RingSurfaceLod.buildNextMipArgb(pixels, width, height);
            width = Math.max(1, width >> 1);
            height = Math.max(1, height >> 1);
            checksum = 31 * checksum + pixels[pixels.length / 2];
        }
        return checksum;
    }

    private static int terrainColor(int column, int row, int height) {
        int red = 38 + Math.floorMod(column / 11 + height * 3, 96);
        int green = 72 + Math.floorMod(row / 3 + height * 5, 128);
        int blue = 28 + Math.floorMod(column / 17 + row / 7 + height, 112);
        return red << 16 | green << 8 | blue;
    }

    private static void warmUp() throws IOException {
        RingTerrainAtlas atlas = new RingTerrainAtlas(PRODUCTION, HASH, 16);
        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                atlas.putCell(column, row, 64, 0x447744);
            }
        }
        atlas.encodeTile(0, 0);
        buildClientTextureAndMips(atlas);
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static double millis(long nanos) { return nanos / 1_000_000.0; }

    private record Result(int step, int cells, int samplesPerChunk, long rawBytes, long wireBytes,
                          long gzipBytes, long fillNanos, long saveNanos, long loadNanos,
                          long tileNanos, long textureNanos) {
        private String markdown() {
            return String.format(Locale.ROOT,
                    "| %d | %,d | %d | %,d B | %,d B | %,d B | %.1f ms | %.1f ms | %.1f ms | %.1f ms | %.1f ms |",
                    step, cells, samplesPerChunk, rawBytes, wireBytes, gzipBytes,
                    millis(fillNanos), millis(saveNanos), millis(loadNanos),
                    millis(tileNanos), millis(textureNanos));
        }
    }
}
