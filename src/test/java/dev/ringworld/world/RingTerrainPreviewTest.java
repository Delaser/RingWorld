package dev.ringworld.world;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingTerrainPreviewTest {
    @Test
    void compressedRoundTripPreservesIdentityColoursAndHeights() throws IOException {
        int[] colors = {0x315C78, 0x526B3B, 0xB7A66A, 0xD9E3DF};
        short[] heights = {48, 67, 64, 72};
        RingTerrainPreview source = new RingTerrainPreview(91L, 4, 1, colors, heights);

        RingTerrainPreview decoded = RingTerrainPreview.decode(source.encode());

        assertEquals(91L, decoded.worldHash());
        assertEquals(4, decoded.columns());
        assertEquals(1, decoded.rows());
        for (int column = 0; column < 4; column++) {
            assertEquals(colors[column], decoded.color(column, 0));
            assertEquals(Short.toUnsignedInt(heights[column]), decoded.height(column, 0));
        }
    }

    @Test
    void validatesDimensionsPayloadSizeFormatAndTrailingData() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new RingTerrainPreview(
                1L, 0, 1, new int[0], new short[0]));
        assertThrows(IllegalArgumentException.class, () -> new RingTerrainPreview(
                1L, 2, 1, new int[1], new short[2]));
        assertThrows(IOException.class, () -> RingTerrainPreview.decode(new byte[0]));
        assertThrows(IOException.class, () -> RingTerrainPreview.decode(
                new byte[RingTerrainPreview.MAX_COMPRESSED_BYTES + 1]));
        assertThrows(IOException.class, () -> RingTerrainPreview.decode(
                encodedBody(2, 1, 1, false)));
        assertThrows(IOException.class, () -> RingTerrainPreview.decode(
                encodedBody(RingTerrainPreview.FORMAT_VERSION, 1, 1, true)));
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

    private static byte[] encodedBody(
            int version, int columns, int rows, boolean trailing) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            output.writeByte(version);
            output.writeLong(1L);
            output.writeShort(columns);
            output.writeShort(rows);
            for (int cell = 0; cell < columns * rows; cell++) {
                output.writeShort(64);
                output.writeByte(0x12);
                output.writeByte(0x34);
                output.writeByte(0x56);
            }
            if (trailing) output.writeByte(0x7F);
        }
        return bytes.toByteArray();
    }
}
