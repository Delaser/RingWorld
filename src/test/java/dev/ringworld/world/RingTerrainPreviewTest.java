package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RingTerrainPreviewTest {
    @Test
    void compressedRoundTripPreservesIdentityColoursAndHeights() throws IOException {
        int[] colors = {
                0x315C78, 0x526B3B, 0xB7A66A, 0xD9E3DF,
                0xA45E3B, 0x3F6136, 0x72736D, 0x6F596F
        };
        short[] heights = {48, 67, 64, 72, 76, 69, 104, 66};
        RingTerrainPreview source = new RingTerrainPreview(91L, 4, 2, colors, heights);

        RingTerrainPreview decoded = RingTerrainPreview.decode(source.encode());

        assertEquals(91L, decoded.worldHash());
        assertEquals(4, decoded.columns());
        assertEquals(2, decoded.rows());
        for (int row = 0; row < 2; row++) for (int column = 0; column < 4; column++) {
            int index = row * 4 + column;
            assertEquals(colors[index], decoded.color(column, row));
            assertEquals(Short.toUnsignedInt(heights[index]), decoded.height(column, row));
        }
    }

    @Test
    void rejectsEmptyOrWrongWorldPreview() throws IOException {
        RingTerrainPreview preview = new RingTerrainPreview(
                7L, 1, 1, new int[]{0x123456}, new short[]{70});
        assertThrows(IOException.class, () -> RingTerrainPreview.decode(new byte[0]));

        RingTerrainAtlas atlas = new RingTerrainAtlas(new RingGeometry(128, 2_048), 8L);
        assertThrows(IllegalArgumentException.class,
                () -> RingSurfacePlaceholder.resolve(atlas, 16, 8, preview));
    }

    @Test
    void seedPreviewFlavoursUnknownCellsWhileRealAtlasCellsWin() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, 31L);
        RingTerrainPreview preview = new RingTerrainPreview(
                31L, 2, 1, new int[]{0x315C78, 0xB7A66A}, new short[]{48, 66});

        RingSurfacePlaceholder.Surface initial = RingSurfacePlaceholder.resolve(
                atlas, 4, 1, preview);
        assertEquals(0xFF315C78, initial.argb()[0]);
        assertEquals(0xFFB7A66A, initial.argb()[3]);
        assertEquals(48.0F, initial.heights()[0]);
        assertEquals(66.0F, initial.heights()[3]);

        atlas.putCell(0, 0, 91, 0x123456);
        RingSurfacePlaceholder.Surface withRealCell = RingSurfacePlaceholder.resolve(
                atlas, atlas.columns(), atlas.rows(), preview);
        assertEquals(0xFF123456, withRealCell.argb()[0]);
        assertEquals(91.0F, withRealCell.heights()[0]);
        assertEquals(0xFF315C78, withRealCell.argb()[1],
                "a generated cell must not smear into the seed preview");
    }

    @Test
    void centeredDisplayPlacesTheCanonicalSeamInTheMiddle() {
        RingTerrainPreview preview = new RingTerrainPreview(
                17L, 8, 1,
                new int[]{0, 1, 2, 3, 4, 5, 6, 7},
                new short[]{0, 1, 2, 3, 4, 5, 6, 7});

        int[] displayed = new int[preview.columns()];
        for (int displayColumn = 0; displayColumn < displayed.length; displayColumn++) {
            displayed[displayColumn] = preview.centeredSeamSourceColumn(displayColumn);
        }

        assertArrayEquals(new int[]{4, 5, 6, 7, 0, 1, 2, 3}, displayed);
        assertEquals(7, displayed[displayed.length / 2 - 1]);
        assertEquals(0, displayed[displayed.length / 2]);
        assertThrows(IndexOutOfBoundsException.class,
                () -> preview.centeredSeamSourceColumn(-1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> preview.centeredSeamSourceColumn(preview.columns()));
    }
}
