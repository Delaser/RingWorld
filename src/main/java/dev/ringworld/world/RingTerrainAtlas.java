package dev.ringworld.world;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A compact, canonical overview of the generated ring surface. X is periodic,
 * Z spans the finite band, and each cell stores the real surface height and
 * map colour sampled from a generated chunk.
 *
 * <p>The atlas deliberately contains data rather than a GPU texture. That
 * keeps the file/network format independent from Minecraft's renderer and
 * lets the sky mesh bilinearly sample exactly the same tiled cache.</p>
 */
public final class RingTerrainAtlas {
    public static final int FORMAT_VERSION = 1;
    public static final int SAMPLE_STEP_BLOCKS = 8;
    public static final int TILE_SIZE = 16;
    private static final int MAGIC = 0x52574154; // RWAT
    private static final int MAX_TILE_BYTES = TILE_SIZE * TILE_SIZE * 7;

    private final RingGeometry geometry;
    private final long worldHash;
    private final int sampleStep;
    private final int columns;
    private final int rows;
    private final short[] heights;
    private final int[] colors;
    private final boolean[] present;
    private int presentCount;

    public RingTerrainAtlas(RingGeometry geometry, long worldHash) {
        this(geometry, worldHash, SAMPLE_STEP_BLOCKS);
    }

    public RingTerrainAtlas(RingGeometry geometry, long worldHash, int sampleStep) {
        if (sampleStep <= 0 || 16 % sampleStep != 0) {
            throw new IllegalArgumentException("atlas sample step must divide one chunk");
        }
        this.geometry = geometry;
        this.worldHash = worldHash;
        this.sampleStep = sampleStep;
        this.columns = divideCeil(geometry.circumferenceBlocks(), sampleStep);
        this.rows = divideCeil(geometry.widthBlocks(), sampleStep);
        this.heights = new short[columns * rows];
        this.colors = new int[columns * rows];
        this.present = new boolean[columns * rows];
    }

    public static long worldHash(RingWorldSettings settings) {
        long value = 0x9E3779B97F4A7C15L ^ settings.generatorSeed();
        value = mix(value ^ Integer.toUnsignedLong(settings.circumferenceBlocks()));
        value = mix(value ^ (Integer.toUnsignedLong(settings.widthBlocks()) << 1));
        value = mix(value ^ (Integer.toUnsignedLong(settings.formatVersion()) << 32));
        return value;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public RingGeometry geometry() { return geometry; }
    public long worldHash() { return worldHash; }
    public int sampleStep() { return sampleStep; }
    public int columns() { return columns; }
    public int rows() { return rows; }
    public int tileColumns() { return divideCeil(columns, TILE_SIZE); }
    public int tileRows() { return divideCeil(rows, TILE_SIZE); }
    public int presentCount() { return presentCount; }
    public int cellCount() { return present.length; }
    public boolean isComplete() { return presentCount == present.length; }
    public double completion() { return present.length == 0 ? 1.0 : (double)presentCount / present.length; }

    /** Stores a sample selected by canonical block coordinates. */
    public boolean putBlockSample(int blockX, int blockZ, int surfaceY, int mapColor) {
        int column = geometry.wrapBlockX(blockX) / sampleStep;
        int row = Math.floorDiv(blockZ - geometry.minWidthZ(), sampleStep);
        if (row < 0 || row >= rows) return false;
        return putCell(column, row, surfaceY, mapColor);
    }

    public boolean putCell(int column, int row, int surfaceY, int mapColor) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) return false;
        int index = index(column, row);
        short clampedHeight = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, surfaceY));
        int rgb = mapColor & 0xFFFFFF;
        boolean changed = !present[index] || heights[index] != clampedHeight || colors[index] != rgb;
        if (!present[index]) {
            present[index] = true;
            presentCount++;
        }
        heights[index] = clampedHeight;
        colors[index] = rgb;
        return changed;
    }

    public boolean hasCell(int column, int row) {
        if (row < 0 || row >= rows) return false;
        return present[index(Math.floorMod(column, columns), row)];
    }

    /** Raw canonical texel access for the client-side GPU surface texture. */
    public int cellColor(int column, int row) {
        if (row < 0 || row >= rows) return -1;
        int index = index(Math.floorMod(column, columns), row);
        return present[index] ? colors[index] : -1;
    }

    /** Raw canonical height access for the low-detail cylindrical mesh. */
    public int cellHeight(int column, int row) {
        if (row < 0 || row >= rows) return (int)RingGeometry.SURFACE_Y;
        int index = index(Math.floorMod(column, columns), row);
        return present[index] ? heights[index] : (int)RingGeometry.SURFACE_Y;
    }

    /**
     * Bilinearly samples the overview. X wraps at the exact ring seam. Missing
     * neighbours are ignored so a partly generated atlas improves the Arch
     * progressively without producing zero-height spikes.
     */
    public SurfaceSample sample(double canonicalX, double canonicalZ) {
        double atlasX = geometry.wrapX(canonicalX) / sampleStep - 0.5;
        double atlasZ = (canonicalZ - geometry.minWidthZ()) / sampleStep - 0.5;
        int x0 = (int)Math.floor(atlasX);
        int z0 = (int)Math.floor(atlasZ);
        double fx = atlasX - x0;
        double fz = atlasZ - z0;

        double height = 0.0;
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        double weightTotal = 0.0;
        for (int dz = 0; dz <= 1; dz++) {
            int row = Math.max(0, Math.min(rows - 1, z0 + dz));
            double wz = dz == 0 ? 1.0 - fz : fz;
            for (int dx = 0; dx <= 1; dx++) {
                int column = Math.floorMod(x0 + dx, columns);
                double wx = dx == 0 ? 1.0 - fx : fx;
                double weight = wx * wz;
                int index = index(column, row);
                if (!present[index] || weight <= 0.0) continue;
                height += heights[index] * weight;
                red += (colors[index] >> 16 & 0xFF) * weight;
                green += (colors[index] >> 8 & 0xFF) * weight;
                blue += (colors[index] & 0xFF) * weight;
                weightTotal += weight;
            }
        }
        if (weightTotal <= 0.0) return SurfaceSample.MISSING;
        int color = clampColor(red / weightTotal) << 16
                | clampColor(green / weightTotal) << 8
                | clampColor(blue / weightTotal);
        return new SurfaceSample(height / weightTotal, color, weightTotal);
    }

    public byte[] encodeTile(int tileX, int tileZ) {
        checkTile(tileX, tileZ);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAX_TILE_BYTES);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                int firstX = tileX * TILE_SIZE;
                int firstZ = tileZ * TILE_SIZE;
                int tileWidth = Math.min(TILE_SIZE, columns - firstX);
                int tileHeight = Math.min(TILE_SIZE, rows - firstZ);
                output.writeByte(tileWidth);
                output.writeByte(tileHeight);
                for (int z = 0; z < tileHeight; z++) {
                    for (int x = 0; x < tileWidth; x++) {
                        int index = index(firstX + x, firstZ + z);
                        output.writeBoolean(present[index]);
                        output.writeShort(heights[index]);
                        output.writeInt(colors[index]);
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("memory-backed atlas tile encoding failed", impossible);
        }
    }

    public void applyTile(int tileX, int tileZ, byte[] data) throws IOException {
        checkTile(tileX, tileZ);
        if (data.length > MAX_TILE_BYTES + 2) throw new IOException("oversized terrain atlas tile");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            int firstX = tileX * TILE_SIZE;
            int firstZ = tileZ * TILE_SIZE;
            int expectedWidth = Math.min(TILE_SIZE, columns - firstX);
            int expectedHeight = Math.min(TILE_SIZE, rows - firstZ);
            int tileWidth = input.readUnsignedByte();
            int tileHeight = input.readUnsignedByte();
            if (tileWidth != expectedWidth || tileHeight != expectedHeight) {
                throw new IOException("terrain atlas tile dimensions do not match metadata");
            }
            for (int z = 0; z < tileHeight; z++) {
                for (int x = 0; x < tileWidth; x++) {
                    boolean incomingPresent = input.readBoolean();
                    int height = input.readShort();
                    int color = input.readInt();
                    int index = index(firstX + x, firstZ + z);
                    // Atlas samples are immutable once generated. A client may
                    // have a more complete disk cache than a newly started or
                    // still-pregenerating server snapshot, so an absent wire
                    // cell must never erase valid cached terrain.
                    if (incomingPresent) {
                        if (!present[index]) presentCount++;
                        present[index] = true;
                        heights[index] = (short)height;
                        colors[index] = color & 0xFFFFFF;
                    }
                }
            }
            if (input.available() != 0) throw new IOException("trailing terrain atlas tile data");
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary))))) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeLong(worldHash);
            output.writeInt(geometry.widthBlocks());
            output.writeInt(geometry.circumferenceBlocks());
            output.writeInt(sampleStep);
            output.writeInt(columns);
            output.writeInt(rows);
            for (int index = 0; index < present.length; index++) {
                output.writeBoolean(present[index]);
                output.writeShort(heights[index]);
                output.writeInt(colors[index]);
            }
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static RingTerrainAtlas load(Path path, RingGeometry expectedGeometry,
                                        long expectedHash) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(path))))) {
            if (input.readInt() != MAGIC) throw new IOException("not a RingWorld terrain atlas");
            if (input.readInt() != FORMAT_VERSION) throw new IOException("unsupported terrain atlas version");
            long hash = input.readLong();
            int width = input.readInt();
            int circumference = input.readInt();
            int sampleStep = input.readInt();
            RingGeometry geometry = new RingGeometry(width, circumference);
            RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, hash, sampleStep);
            if (!geometry.equals(expectedGeometry) || hash != expectedHash
                    || input.readInt() != atlas.columns || input.readInt() != atlas.rows) {
                throw new IOException("terrain atlas does not match this ring world");
            }
            for (int index = 0; index < atlas.present.length; index++) {
                atlas.present[index] = input.readBoolean();
                atlas.heights[index] = input.readShort();
                atlas.colors[index] = input.readInt() & 0xFFFFFF;
                if (atlas.present[index]) atlas.presentCount++;
            }
            return atlas;
        }
    }

    public int firstMissingChunkIndex() {
        int chunksAlong = geometry.circumferenceBlocks() >> 4;
        int chunksAcross = geometry.widthBlocks() >> 4;
        for (int chunkX = 0; chunkX < chunksAlong; chunkX++) {
            for (int chunkRow = 0; chunkRow < chunksAcross; chunkRow++) {
                if (!isChunkPresent(chunkX, chunkRow)) return chunkX * chunksAcross + chunkRow;
            }
        }
        return chunksAlong * chunksAcross;
    }

    public boolean isChunkPresent(int chunkX, int chunkRow) {
        int samplesPerChunk = 16 / sampleStep;
        int firstX = chunkX * samplesPerChunk;
        int firstZ = chunkRow * samplesPerChunk;
        for (int z = 0; z < samplesPerChunk; z++) {
            for (int x = 0; x < samplesPerChunk; x++) {
                if (!hasCell(firstX + x, firstZ + z)) return false;
            }
        }
        return true;
    }

    public void clear() {
        Arrays.fill(heights, (short)0);
        Arrays.fill(colors, 0);
        Arrays.fill(present, false);
        presentCount = 0;
    }

    private int index(int column, int row) { return row * columns + column; }

    private void checkTile(int tileX, int tileZ) {
        if (tileX < 0 || tileX >= tileColumns() || tileZ < 0 || tileZ >= tileRows()) {
            throw new IndexOutOfBoundsException("terrain atlas tile outside metadata");
        }
    }

    private static int divideCeil(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int clampColor(double value) {
        return Math.max(0, Math.min(255, (int)Math.round(value)));
    }

    public record SurfaceSample(double height, int color, double coverage) {
        public static final SurfaceSample MISSING = new SurfaceSample(RingGeometry.SURFACE_Y, -1, 0.0);
        public boolean present() { return color >= 0 && coverage > 0.0; }
    }
}
