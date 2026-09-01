package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingTerrainPreviewSamplerTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2_048);

    @Test
    void stagesUseDeterministicIncreasingDimensionsWithoutChunksOrSaves() {
        int[][] expected = {
                {512, 16, 128, 8},
                {1_024, 32, 256, 16},
                {2_048, 32, 512, 16},
                {2_048, 64, 1_024, 32}
        };
        long worldHash = 0x5052_4556_4945_57L;
        int previousStage = -1;
        for (RingTerrainPreviewStage stage : RingTerrainPreviewStage.values()) {
            CountingSource source = new CountingSource();
            RingTerrainPreview preview = RingTerrainPreviewSampler.generate(
                    worldHash, GEOMETRY, stage, source);
            int[] dimensions = expected[stage.wireValue()];

            assertTrue(stage.wireValue() > previousStage);
            assertEquals(worldHash, preview.worldHash());
            assertEquals(dimensions[0], preview.columns());
            assertEquals(dimensions[1], preview.rows());
            assertEquals(dimensions[2] * dimensions[3], source.terrainCalls);
            assertEquals(dimensions[0] * dimensions[1], source.surfaceCalls);
            assertEquals(0x123456, preview.color(0, 0));
            assertEquals(70.0f, preview.height(0, 0));
            previousStage = stage.wireValue();
        }
    }

    @Test
    void interruptedWorkerCancelsBeforeSampling() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () ->
                    RingTerrainPreviewSampler.generate(1L, GEOMETRY,
                            RingTerrainPreviewStage.CURRENT, new CountingSource()));
        } finally {
            Thread.interrupted();
        }
    }

    private static final class CountingSource implements RingTerrainPreviewSampler.Source {
        private int terrainCalls;
        private int surfaceCalls;

        @Override
        public int terrainHeight(int x, int z) {
            terrainCalls++;
            return 70;
        }

        @Override
        public RingTerrainPreviewSampler.PreviewSample surface(
                int x, int z, int terrainHeight) {
            surfaceCalls++;
            return new RingTerrainPreviewSampler.PreviewSample(0x123456, terrainHeight);
        }
    }
}
