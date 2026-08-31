package dev.ringworld.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Coarse, seed-derived surface colours shown until authoritative Atlas cells arrive. */
public final class RingTerrainPreview {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_COLUMNS = 4_096;
    public static final int MAX_ROWS = 64;
    public static final int MAX_COMPRESSED_BYTES = 2 * 1_024 * 1_024;
    private static final int MAX_CELLS = MAX_COLUMNS * MAX_ROWS;

    private final long worldHash;
    private final int columns;
    private final int rows;
    private final int[] colors;
    private final short[] heights;

    public RingTerrainPreview(long worldHash, int columns, int rows,
                              int[] colors, short[] heights) {
        if (columns <= 0 || rows <= 0 || columns > MAX_COLUMNS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("invalid terrain-preview dimensions");
        }
        int cells = Math.multiplyExact(columns, rows);
        if (cells > MAX_CELLS || colors.length != cells || heights.length != cells) {
            throw new IllegalArgumentException("terrain-preview cell data does not match dimensions");
        }
        this.worldHash = worldHash;
        this.columns = columns;
        this.rows = rows;
        this.colors = colors.clone();
        this.heights = heights.clone();
    }

    public long worldHash() { return worldHash; }
    public int columns() { return columns; }
    public int rows() { return rows; }
    public int cellCount() { return colors.length; }

    public int color(int column, int row) {
        return colors[index(column, row)] & 0xFFFFFF;
    }

    public float height(int column, int row) {
        return Short.toUnsignedInt(heights[index(column, row)]);
    }

    public int sampleColor(int targetColumn, int targetRow, int targetColumns, int targetRows) {
        return color(sampleIndex(targetColumn, targetColumns, columns),
                sampleIndex(targetRow, targetRows, rows));
    }

    public float sampleHeight(int targetColumn, int targetRow, int targetColumns, int targetRows) {
        return height(sampleIndex(targetColumn, targetColumns, columns),
                sampleIndex(targetRow, targetRows, rows));
    }

    /**
     * Maps a left-to-right display column to canonical preview data with the
     * X=C-1 / X=0 join in the centre of the image instead of at its edges.
     */
    public int centeredSeamSourceColumn(int displayColumn) {
        if (displayColumn < 0 || displayColumn >= columns) {
            throw new IndexOutOfBoundsException("terrain-preview display column outside bounds");
        }
        return Math.floorMod(displayColumn + columns / 2, columns);
    }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            output.writeByte(FORMAT_VERSION);
            output.writeLong(worldHash);
            output.writeShort(columns);
            output.writeShort(rows);
            for (int index = 0; index < colors.length; index++) {
                output.writeShort(heights[index]);
                output.writeByte(colors[index] >> 16);
                output.writeByte(colors[index] >> 8);
                output.writeByte(colors[index]);
            }
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > MAX_COMPRESSED_BYTES) {
            throw new IOException("terrain preview exceeds compressed payload limit");
        }
        return encoded;
    }

    public static RingTerrainPreview decode(byte[] encoded) throws IOException {
        if (encoded.length == 0 || encoded.length > MAX_COMPRESSED_BYTES) {
            throw new IOException("invalid terrain-preview payload size");
        }
        try (DataInputStream input = new DataInputStream(
                new InflaterInputStream(new ByteArrayInputStream(encoded)))) {
            int version = input.readUnsignedByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("unsupported terrain-preview format " + version);
            }
            long worldHash = input.readLong();
            int columns = input.readUnsignedShort();
            int rows = input.readUnsignedShort();
            if (columns <= 0 || rows <= 0 || columns > MAX_COLUMNS || rows > MAX_ROWS) {
                throw new IOException("invalid terrain-preview dimensions");
            }
            int cells = Math.multiplyExact(columns, rows);
            int[] colors = new int[cells];
            short[] heights = new short[cells];
            for (int index = 0; index < cells; index++) {
                heights[index] = input.readShort();
                colors[index] = input.readUnsignedByte() << 16
                        | input.readUnsignedByte() << 8
                        | input.readUnsignedByte();
            }
            if (input.read() != -1) throw new IOException("trailing terrain-preview data");
            return new RingTerrainPreview(worldHash, columns, rows, colors, heights);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IOException("malformed terrain-preview payload", exception);
        }
    }

    private int index(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("terrain-preview cell outside bounds");
        }
        return row * columns + column;
    }

    private static int sampleIndex(int targetIndex, int targetSize, int sourceSize) {
        if (targetIndex < 0 || targetIndex >= targetSize || targetSize <= 0) {
            throw new IndexOutOfBoundsException("terrain-preview sample outside bounds");
        }
        return Math.min(sourceSize - 1,
                (int)(((long)targetIndex * 2L + 1L) * sourceSize / (targetSize * 2L)));
    }
}
