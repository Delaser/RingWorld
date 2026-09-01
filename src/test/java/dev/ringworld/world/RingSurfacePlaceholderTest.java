package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingSurfacePlaceholderTest {
    @Test
    void zeroCellAtlasProducesUniformOpaqueNeutralSurface() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingSurfacePlaceholder.Surface first = RingSurfacePlaceholder.resolve(
                new RingTerrainAtlas(geometry, 11L));
        RingSurfacePlaceholder.Surface repeat = RingSurfacePlaceholder.resolve(
                new RingTerrainAtlas(geometry, 11L));
        assertArrayEquals(first.argb(), repeat.argb());
        for (int pixel : first.argb()) {
            assertEquals(0xFF000000 | RingSurfacePlaceholder.NEUTRAL_GREY, pixel);
        }
        for (float height : first.heights()) {
            assertEquals((float)RingGeometry.SURFACE_Y, height);
        }
    }

    @Test
    void realAtlasCellsOverridePreviewWhileMissingCellsUseItAtTargetResolution() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, 42L);
        int[] colors = {
                0x010101, 0x020202, 0x030303, 0x040404,
                0x111111, 0x121212, 0x131313, 0x141414
        };
        short[] heights = {61, 62, 63, 64, 71, 72, 73, 74};
        RingTerrainPreview preview = new RingTerrainPreview(
                42L, 4, 2, colors, heights);
        // Target cell (0,0) samples the centre of this Atlas region.
        atlas.putCell(32, 4, 91, 0xABCDEF);

        RingSurfacePlaceholder.Surface surface = RingSurfacePlaceholder.resolve(
                atlas, 4, 2, preview);

        assertEquals(0xFFABCDEF, surface.argb()[0]);
        assertEquals(91.0F, surface.heights()[0]);
        for (int index = 1; index < colors.length; index++) {
            assertEquals(0xFF000000 | colors[index], surface.argb()[index]);
            assertEquals(Short.toUnsignedInt(heights[index]), surface.heights()[index]);
        }
    }

    @Test
    void absentPreviewUsesNeutralOnlyForMissingCells() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, 91L);
        atlas.putCell(32, 4, 83, 0x123456);

        RingSurfacePlaceholder.Surface surface = RingSurfacePlaceholder.resolve(atlas, 4, 2);

        assertEquals(0xFF123456, surface.argb()[0]);
        assertEquals(83.0F, surface.heights()[0]);
        for (int index = 1; index < surface.argb().length; index++) {
            assertEquals(0xFF000000 | RingSurfacePlaceholder.NEUTRAL_GREY,
                    surface.argb()[index]);
            assertEquals((float)RingGeometry.SURFACE_Y, surface.heights()[index]);
        }
    }

    @Test
    void validatesPreviewIdentityAndKeepsCompleteAtlasOutOfPlaceholderPath() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(new RingGeometry(512, 32_768), 91L);
        RingTerrainPreview wrong = new RingTerrainPreview(
                92L, 1, 1, new int[]{0x123456}, new short[]{64});
        assertThrows(IllegalArgumentException.class,
                () -> RingSurfacePlaceholder.resolve(atlas, 1, 1, wrong));

        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                atlas.putCell(column, row, 64, 0x123456);
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> RingSurfacePlaceholder.resolve(atlas, 1, 1, null));
    }
}
