package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Bit-exact frozen-implementation oracle and owner-thread allocation contract. */
class RingTerrainAtlasSampleAllocationTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2048);
    private static volatile long blackhole;

    static Stream<Arguments> fixtures() {
        return Stream.of(1, 2, 4, 8).flatMap(step -> Stream.of(
                Arguments.of(step, true), Arguments.of(step, false)));
    }

    @ParameterizedTest(name = "source step {0}, complete fixture {1}")
    @MethodSource("fixtures")
    void immutableAndReusableSamplesMatchFrozenPriorJarBits(int step, boolean dense) throws Exception {
        var atlas = fixture(step, dense);
        var scratch = new RingTerrainAtlas.MutableSurfaceSample();
        try (var stream = getClass().getResourceAsStream("atlas-sampling-golden-v1.csv")) {
            assertNotNull(stream, "Frozen existing-jar vectors must be packaged in both loader test lanes");
            List<String> lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList();
            assertEquals("# frozenJarSha256=d3c2a43ef11c0ce08b106d4003d40b060ce1f597cf0f5f7203aee9701a8a2efc", lines.get(0));
            int checked = 0;
            for (String line : lines) {
                if (line.startsWith("#")) continue;
                String[] fields = line.split(",");
                if (Integer.parseInt(fields[0]) != step || Boolean.parseBoolean(fields[1]) != dense) continue;
                double x = value(fields[2]), z = value(fields[3]);
                var immutable = atlas.sample(x, z);
                atlas.sampleInto(x, z, scratch);
                assertBits(fields[4], immutable.height()); assertBits(fields[4], scratch.height());
                assertEquals(Integer.parseInt(fields[5]), immutable.color()); assertEquals(immutable.color(), scratch.color());
                assertBits(fields[6], immutable.blockLight()); assertBits(fields[6], scratch.blockLight());
                assertBits(fields[7], immutable.coverage()); assertBits(fields[7], scratch.coverage());
                assertBits(fields[8], immutable.waterCoverage()); assertBits(fields[8], scratch.waterCoverage());
                assertEquals(Boolean.parseBoolean(fields[9]), immutable.present()); assertEquals(immutable.present(), scratch.present());
                checked++;
            }
            assertEquals(512, checked, "All seam/band-edge and randomized frozen vectors required");
        }
    }

    @Test
    void missingSampleResetsEveryReusedValueAndPreservesImmutableSingleton() {
        var filled = fixture(8, true);
        var empty = new RingTerrainAtlas(GEOMETRY, 42L, 8);
        var scratch = new RingTerrainAtlas.MutableSurfaceSample();
        filled.sampleInto(4.0, -60.0, scratch);
        assertTrue(scratch.present());
        empty.sampleInto(4.0, -60.0, scratch);
        var missing = RingTerrainAtlas.SurfaceSample.MISSING;
        assertSame(missing, empty.sample(4.0, -60.0));
        assertEquals(missing.height(), scratch.height()); assertEquals(missing.color(), scratch.color());
        assertEquals(missing.blockLight(), scratch.blockLight()); assertEquals(missing.coverage(), scratch.coverage());
        assertEquals(missing.waterCoverage(), scratch.waterCoverage()); assertFalse(scratch.present());
    }

    @Test
    void separateScratchResultsAndPreviouslyReturnedImmutableValuesStayIndependent() {
        var atlas = fixture(1, true);
        var left = new RingTerrainAtlas.MutableSurfaceSample();
        var right = new RingTerrainAtlas.MutableSurfaceSample();
        var before = atlas.sample(0.5, -63.5);
        atlas.sampleInto(0.5, -63.5, left);
        atlas.sampleInto(113.5, 12.5, right);
        assertEquals(before.height(), left.height()); assertEquals(before.color(), left.color());
        atlas.sampleInto(511.5, 14.5, left);
        assertEquals(before, atlas.sample(0.5, -63.5));
        assertThrows(NullPointerException.class, () -> atlas.sampleInto(0.5, 0.5, null));
    }

    @Test
    void reusableTextureSamplingDoesNotAllocateOneResultPerPoint() {
        var bean = assertInstanceOf(com.sun.management.ThreadMXBean.class, ManagementFactory.getThreadMXBean());
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        var atlas = fixture(8, true);
        var scratch = new RingTerrainAtlas.MutableSurfaceSample();
        sampleLoop(atlas, scratch, 30_000);
        long id = Thread.currentThread().getId();
        long start = bean.getThreadAllocatedBytes(id);
        sampleLoop(atlas, scratch, 200_000);
        long allocated = bean.getThreadAllocatedBytes(id) - start;
        assertTrue(allocated < 262_144L, "Reusable sampling allocated " + allocated + " bytes for 200,000 points");
        System.out.println("Reusable Atlas sampling: " + allocated + " allocated bytes for 200,000 points");
    }

    private static void sampleLoop(RingTerrainAtlas atlas, RingTerrainAtlas.MutableSurfaceSample scratch, int count) {
        long checksum = 0;
        for (int i = 0; i < count; i++) {
            atlas.sampleInto((i & 2047) + 0.125, ((i * 7) & 127) - 64 + 0.375, scratch);
            checksum += scratch.color() ^ Double.doubleToRawLongBits(scratch.height());
        }
        blackhole = checksum;
    }

    private static RingTerrainAtlas fixture(int step, boolean dense) {
        var atlas = new RingTerrainAtlas(GEOMETRY, 42L, step);
        for (int z = 0; z < 128 / step; z++) for (int x = 0; x < 2048 / step; x++) {
            if (!dense && Math.floorMod(x * 31 + z * 17, 11) == 0) continue;
            int height = (x * 37 + z * 19) % 384 - 64;
            int color = (x * 1103515245 + z * 12345) ^ 0x12abcd;
            atlas.putCell(x, z, height, color, (x ^ z) & 15, 0xabcdef, (x + 3 * z) & 15);
        }
        return atlas;
    }

    private static double value(String hex) { return Double.longBitsToDouble(Long.parseUnsignedLong(hex, 16)); }
    private static void assertBits(String expected, double actual) {
        assertEquals(Long.parseUnsignedLong(expected, 16), Double.doubleToRawLongBits(actual));
    }
}
