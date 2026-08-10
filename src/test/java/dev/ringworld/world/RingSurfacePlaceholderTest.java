package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingSurfacePlaceholderTest {
    @Test
    void zeroCellAtlasProducesDeterministicOpaqueWorldSpecificSurface() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingSurfacePlaceholder.Surface first = RingSurfacePlaceholder.resolve(
                new RingTerrainAtlas(geometry, 11L));
        RingSurfacePlaceholder.Surface repeat = RingSurfacePlaceholder.resolve(
                new RingTerrainAtlas(geometry, 11L));
        RingSurfacePlaceholder.Surface other = RingSurfacePlaceholder.resolve(
                new RingTerrainAtlas(geometry, 12L));

        assertArrayEquals(first.argb(), repeat.argb());
        assertFalse(java.util.Arrays.equals(first.argb(), other.argb()));
        for (int pixel : first.argb()) assertEquals(0xFF, pixel >>> 24);
    }

    @Test
    void realCellsRemainExactAndUnknownCellsStayOpaqueDuringDilation() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, 42L);
        atlas.putCell(0, 0, 91, 0x123456);
        atlas.putCell(atlas.columns() - 1, 0, 87, 0x654321);

        RingSurfacePlaceholder.Surface surface = RingSurfacePlaceholder.resolve(atlas);
        assertEquals(0xFF123456, surface.argb()[0]);
        assertEquals(91.0F, surface.heights()[0]);
        assertEquals(0xFF654321, surface.argb()[atlas.columns() - 1]);
        for (int pixel : surface.argb()) assertNotEquals(0, pixel >>> 24);
    }

    @Test
    void resamplesToTheBoundedGpuProfileWithoutChangingOpacity() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(new RingGeometry(512, 32_768), 91L);
        RingSurfacePlaceholder.Surface surface = RingSurfacePlaceholder.resolve(atlas, 256, 32);
        assertEquals(256, surface.columns());
        assertEquals(32, surface.rows());
        assertEquals(256 * 32, surface.argb().length);
        for (int pixel : surface.argb()) assertEquals(0xFF, pixel >>> 24);
    }
}
