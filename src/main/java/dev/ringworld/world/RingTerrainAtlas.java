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
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A compact, canonical overview of the generated ring surface. X is periodic,
 * Z spans the finite band, and each cell stores the real surface height,
 * map colour, and exposed block-light level sampled from a generated chunk.
 *
 * <p>The atlas deliberately contains data rather than a GPU texture. That
 * keeps the file/network format independent from Minecraft's renderer and
 * lets the sky mesh bilinearly sample exactly the same tiled cache.</p>
 */
public final class RingTerrainAtlas {
    /** Format 10 adds water coverage in the upper nibble of the existing light byte. */
    // Sampling semantics changed: cached top-only columns must be rebuilt.
    public static final int FORMAT_VERSION = 11;
    public static final int SAMPLE_STEP_BLOCKS = 1;
    public static final int TILE_SIZE = 16;
    /** Short height, top and side colours, packed light/water byte, and presence accounting per cell. */
    public static final int ESTIMATED_BYTES_PER_CELL = 12;
    private static final int MAGIC = 0x52574154; // RWAT
    private static final int MAX_TILE_BYTES = TILE_SIZE * TILE_SIZE * ESTIMATED_BYTES_PER_CELL;

    private final RingGeometry geometry;
    private final long worldHash;
    private final int sampleStep;
    private final int columns;
    private final int rows;
    // Snapshots share pages. Each owner detaches only the pages it subsequently edits.
    private static final int PAGE_SHIFT = 10;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final int PAGE_MASK = PAGE_SIZE - 1;
    private static final Page EMPTY = new Page();
    private final Page[] pages;
    private final java.util.BitSet writable = new java.util.BitSet();
    private final int cellCount;

    private static final class Page {
        final short[] heights;
        final int[] colors;
        final int[] sideColors;
        final byte[] blockLights;
        final boolean[] present;

        Page() {
            heights = new short[PAGE_SIZE];
            colors = new int[PAGE_SIZE];
            sideColors = new int[PAGE_SIZE];
            blockLights = new byte[PAGE_SIZE];
            present = new boolean[PAGE_SIZE];
        }

        Page(Page source) {
            heights = source.heights.clone();
            colors = source.colors.clone();
            sideColors = source.sideColors.clone();
            blockLights = source.blockLights.clone();
            present = source.present.clone();
        }
    }

    private Page writablePage(int index) {
        int page = index >>> PAGE_SHIFT;
        if (!writable.get(page)) {
            pages[page] = pages[page] == EMPTY ? new Page() : new Page(pages[page]);
            writable.set(page);
        }
        return pages[page];
    }

    // X-major indexing matches the pregeneration cursor. Partial edge chunks can own fewer samples.
    private final short[] missingByChunk;
    private final short[] samplesByTile;
    private final java.util.BitSet populatedTiles = new java.util.BitSet();
    private long contentFingerprint;
    private int completeChunks;
    private int presentCount;
    private long revision;

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
        long cells = Math.multiplyExact((long)columns, rows);
        if (cells > RingDimensionReport.MAX_ATLAS_CELLS) {
            throw new IllegalArgumentException(RingDimensionReport.atlasLimitMessage(cells));
        }
        this.cellCount = Math.toIntExact(cells);
        missingByChunk = new short[Math.multiplyExact(geometry.circumferenceChunks(), geometry.widthChunks())];
        samplesByTile = new short[Math.multiplyExact(tileColumns(), tileRows())];
        resetCoverage();
        pages = new Page[divideCeil(cellCount, PAGE_SIZE)];
        Arrays.fill(pages, EMPTY);
    }

    public static long worldHash(RingWorldSettings settings) {
        long value = RingLayoutFingerprint.compute(settings);
        value = RingLayoutFingerprint.mix(value ^ ((long)FORMAT_VERSION << 32));
        return RingLayoutFingerprint.mix(value
                ^ SAMPLE_STEP_BLOCKS);
    }

    public RingGeometry geometry() { return geometry; }
    public long worldHash() { return worldHash; }
    public int sampleStep() { return sampleStep; }

    // A Medium cell uses the block at offset (1, 1), centred 1.5 blocks from the cell edge.
    double sampleCenterInCells() {
        return sampleStep == RingLodQuality.MEDIUM.sampleStep() ? 0.75 : 0.5;
    }

    double sampleCoordinate(double blockOffset) {
        return blockOffset / sampleStep - sampleCenterInCells();
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public int tileColumns() { return divideCeil(columns, TILE_SIZE); }
    public int tileRows() { return divideCeil(rows, TILE_SIZE); }
    public int presentCount() { return presentCount; }
    public long revision() { return revision; }
    public java.util.BitSet populatedTiles() { return (java.util.BitSet)populatedTiles.clone(); }
    public long contentFingerprint() { return contentFingerprint; }

    // Include position and every field that goes over the wire. XOR lets an edit replace just its own contribution.
    private static long sampleFingerprint(int index, Page page, int offset) {
        if (!page.present[offset]) return 0;
        long location = ((long)index << 24) | ((long)Short.toUnsignedInt(page.heights[offset]) << 8)
                | Byte.toUnsignedInt(page.blockLights[offset]);
        long colors = ((long)(page.colors[offset] & 0xffffff) << 24) | (page.sideColors[offset] & 0xffffff);
        colors |= (long)layer(page.colors[offset], page.sideColors[offset]) << 48;
        return RingLayoutFingerprint.mix(RingLayoutFingerprint.mix(location) ^ colors ^ 0x62A9D9ED799705F5L);
    }

    public int cellCount() { return cellCount; }
    public long estimatedMemoryBytes() {
        return (long)cellCount * ESTIMATED_BYTES_PER_CELL;
    }
    public long estimatedWireBytes() {
        return (long)cellCount * ESTIMATED_BYTES_PER_CELL
                + (long)tileColumns() * tileRows() * 2L;
    }
    public boolean isComplete() { return presentCount == cellCount; }
    public double completion() { return cellCount == 0 ? 1.0 : (double)presentCount / cellCount; }

    /** Stable key for mesh height and distinct side-material vertex colours. */
    public long surfaceHeightFingerprint() {
        return surfaceHeightFingerprint(RingWallStyle.DEFAULT.thicknessBlocks());
    }

    public long surfaceHeightFingerprint(int rimThickness) {
        // The rim closure bakes the highest of the two neighbouring rows' side colours even when it equals the top.
        double innerWidth = geometry.widthBlocks() - 2.0 * rimThickness;
        double inset = rimThickness + Math.min(sampleStep, innerWidth);
        int lowRow = (int)Math.floor(sampleCoordinate(inset));
        int highRow = (int)Math.floor(sampleCoordinate(geometry.widthBlocks() - inset));
        long fingerprint = 0xCBF29CE484222325L;
        for (int index = 0; index < cellCount; index++) {
            Page page = pages[index >>> PAGE_SHIFT];
            long sample = page.present[index & PAGE_MASK]
                    ? 0x1_0000L | Short.toUnsignedLong(page.heights[index & PAGE_MASK])
                    : 0L;
            if (sample != 0 && ((page.blockLights[index & PAGE_MASK] & 255) >>> 4) == 15) {
                sample |= 1L << 49;
            }
            int row = index / columns;
            boolean rim = rimThickness > 0 && (row == lowRow || row == lowRow + 1 || row == highRow || row == highRow + 1);
            int side = page.sideColors[index & PAGE_MASK];
            int top = page.colors[index & PAGE_MASK];
            boolean distinct = ((side ^ top) & 0xffffff) != 0;
            if (sample != 0 && layer(top, side) != 0) {
                sample ^= RingLayoutFingerprint.mix((long)layer(top, side) << 24 | (top & 0xffffff));
            }
            if (sampleStep == RingLodQuality.MEDIUM.sampleStep() && sample != 0 && shoreline(page, index & PAGE_MASK)) {
                sample |= 1L << 48;
            }
            fingerprint ^= sample ^ (page.present[index & PAGE_MASK] && (rim || distinct)
                    ? ((distinct ? 0x1000000L : 0x2000000L) | (side & 0xffffff)) << 17 : 0L);
            fingerprint *= 0x100000001B3L;
        }
        fingerprint ^= Integer.toUnsignedLong(columns);
        fingerprint *= 0x100000001B3L;
        return fingerprint ^ Integer.toUnsignedLong(rows);
    }

    /**
     * Returns an independent point-in-time copy suitable for loader-neutral
     * background work. Network updates and server recaptures may continue to
     * mutate the live atlas without racing consumers of this snapshot.
     */
    public RingTerrainAtlas snapshot() {
        RingTerrainAtlas copy = new RingTerrainAtlas(geometry, worldHash, sampleStep);
        System.arraycopy(pages, 0, copy.pages, 0, pages.length);
        writable.clear();
        System.arraycopy(missingByChunk, 0, copy.missingByChunk, 0, missingByChunk.length);
        System.arraycopy(samplesByTile, 0, copy.samplesByTile, 0, samplesByTile.length);
        copy.populatedTiles.or(populatedTiles);
        copy.contentFingerprint = contentFingerprint;
        copy.completeChunks = completeChunks;
        copy.presentCount = presentCount;
        copy.revision = revision;
        return copy;
    }

    /** Owner-thread checkpoint slice; destination is exclusively owned until copying finishes. */
    public void copyCheckpointCells(RingTerrainAtlas destination, int first, int count) {
        if (!geometry.equals(destination.geometry) || worldHash != destination.worldHash
                || sampleStep != destination.sampleStep || first < 0 || count < 0
                || first > cellCount - count) throw new IllegalArgumentException("invalid checkpoint slice");
        for (int i = first; i < first + count;) {
            int offset = i & PAGE_MASK;
            int length = Math.min(first + count - i, PAGE_SIZE - offset);
            Page source = pages[i >>> PAGE_SHIFT];
            if (source != destination.pages[i >>> PAGE_SHIFT]) {
                Page target = destination.writablePage(i);
                for (int j = offset; j < offset + length; j++) {
                    if (source.present[j] != target.present[j]) {
                        int index = i + j - offset;
                        destination.changePresence(index % columns, index / columns, source.present[j] ? 1 : -1);
                    }
                }
                System.arraycopy(source.heights, offset, target.heights, offset, length);
                System.arraycopy(source.colors, offset, target.colors, offset, length);
                System.arraycopy(source.sideColors, offset, target.sideColors, offset, length);
                System.arraycopy(source.blockLights, offset, target.blockLights, offset, length);
                System.arraycopy(source.present, offset, target.present, offset, length);
            }
            i += length;
        }
    }

    /** Call only after all slices and subsequently dirtied slices match this live source. */
    public void finishCheckpointCopy(RingTerrainAtlas source) {
        if (!geometry.equals(source.geometry) || worldHash != source.worldHash
                || sampleStep != source.sampleStep || presentCount != source.presentCount) {
            throw new IllegalStateException("incomplete checkpoint copy");
        }
        contentFingerprint = source.contentFingerprint;
        revision = source.revision;
    }

    /** Advances one coalesced authoritative surface-change generation. */
    public long advanceRevision() {
        revision = Math.addExact(revision, 1L);
        return revision;
    }

    /** Commits a fully received authoritative revision without allowing rollback. */
    public boolean commitRevision(long incomingRevision) throws IOException {
        if (incomingRevision < 0L) throw new IOException("negative terrain atlas revision");
        if (incomingRevision < revision) return false;
        boolean changed = incomingRevision > revision;
        revision = incomingRevision;
        return changed;
    }

    /** Stores a sample selected by canonical block coordinates. */
    public boolean putBlockSample(int blockX, int blockZ, int surfaceY, int mapColor) {
        return putBlockSample(blockX, blockZ, surfaceY, mapColor, 0);
    }

    public boolean putBlockSample(int blockX, int blockZ, int surfaceY,
                                  int mapColor, int blockLight) {
        return putBlockSample(blockX, blockZ, surfaceY, mapColor, blockLight, mapColor);
    }

    public boolean putBlockSample(int blockX, int blockZ, int surfaceY,
                                  int mapColor, int blockLight, int sideColor) {
        return putBlockSample(blockX, blockZ, surfaceY, mapColor, blockLight, sideColor, 0);
    }

    public boolean putBlockSample(int blockX, int blockZ, int surfaceY,
                                  int mapColor, int blockLight, int sideColor, int waterCoverage) {
        int column = geometry.wrapBlockX(blockX) / sampleStep;
        int row = Math.floorDiv(blockZ - geometry.minWidthZ(), sampleStep);
        if (row < 0 || row >= rows) return false;
        return putCell(column, row, surfaceY, mapColor, blockLight, sideColor, waterCoverage);
    }

    public boolean putCell(int column, int row, int surfaceY, int mapColor) {
        return putCell(column, row, surfaceY, mapColor, 0);
    }

    public boolean putCell(int column, int row, int surfaceY,
                           int mapColor, int blockLight) {
        return putCell(column, row, surfaceY, mapColor, blockLight, mapColor);
    }

    public boolean putCell(int column, int row, int surfaceY,
                           int mapColor, int blockLight, int sideColor) {
        return putCell(column, row, surfaceY, mapColor, blockLight, sideColor, 0);
    }

    public boolean putCell(int column, int row, int surfaceY,
                           int mapColor, int blockLight, int sideColor, int waterCoverage) {
        return putCell(column, row, surfaceY, mapColor, blockLight, sideColor, waterCoverage, 0);
    }

    public boolean putCell(int column, int row, int surfaceY,
                           int mapColor, int blockLight, int sideColor, int waterCoverage, int layer) {
        if (waterCoverage < 0 || waterCoverage > 15) {
            throw new IllegalArgumentException("atlas water coverage must be between 0 and 15");
        }
        if (column < 0 || column >= columns || row < 0 || row >= rows) return false;
        if (blockLight < 0 || blockLight > 15) {
            throw new IllegalArgumentException("atlas block light must be between 0 and 15");
        }
        int index = index(column, row);
        Page page = pages[index >>> PAGE_SHIFT];
        short clampedHeight = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, surfaceY));
        // The existing RGB fields have a spare byte each in both the cache and the network tile.
        int rgb = (mapColor & 0xffffff) | (layer >>> 8) << 24;
        int side = (sideColor & 0xffffff) | layer << 24;
        byte light = (byte)(blockLight | waterCoverage << 4);
        boolean changed = !page.present[index & PAGE_MASK] || page.heights[index & PAGE_MASK] != clampedHeight
                || page.colors[index & PAGE_MASK] != rgb || page.blockLights[index & PAGE_MASK] != light
                || page.sideColors[index & PAGE_MASK] != side;
        if (!changed) return false;
        contentFingerprint ^= sampleFingerprint(index, page, index & PAGE_MASK);
        page = writablePage(index);
        if (!page.present[index & PAGE_MASK]) {
            page.present[index & PAGE_MASK] = true;
            changePresence(column, row, 1);
        }
        page.heights[index & PAGE_MASK] = clampedHeight;
        page.colors[index & PAGE_MASK] = rgb;
        page.sideColors[index & PAGE_MASK] = side;
        page.blockLights[index & PAGE_MASK] = light;
        contentFingerprint ^= sampleFingerprint(index, page, index & PAGE_MASK);
        return true;
    }

    /** Persist unavailable dirty samples as missing so the resume cursor revisits them. */
    public boolean invalidateCell(int column, int row) {
        if (!hasCell(column, row)) return false;
        int index = index(Math.floorMod(column, columns), row);
        contentFingerprint ^= sampleFingerprint(index, pages[index >>> PAGE_SHIFT], index & PAGE_MASK);
        writablePage(index).present[index & PAGE_MASK] = false;
        changePresence(Math.floorMod(column, columns), row, -1);
        return true;
    }

    public boolean hasCell(int column, int row) {
        if (row < 0 || row >= rows) return false;
        int index = index(Math.floorMod(column, columns), row);
        Page page = pages[index >>> PAGE_SHIFT];
        return page.present[index & PAGE_MASK];
    }

    /** Raw canonical texel access for the client-side GPU surface texture. */
    public int cellColor(int column, int row) {
        if (row < 0 || row >= rows) return -1;
        int index = index(Math.floorMod(column, columns), row);
        Page page = pages[index >>> PAGE_SHIFT];
        return page.present[index & PAGE_MASK] ? page.colors[index & PAGE_MASK] & 0xffffff : -1;
    }

    public int cellSideColor(int column, int row) {
        if (row < 0 || row >= rows) return -1;
        int index = index(Math.floorMod(column, columns), row);
        Page page = pages[index >>> PAGE_SHIFT];
        return page.present[index & PAGE_MASK] ? page.sideColors[index & PAGE_MASK] & 0xffffff : -1;
    }

    private static int layer(int top, int side) {
        return ((top >>> 24) << 8) | (side >>> 24);
    }

    public int cellLayer(int column, int row) {
        if (row < 0 || row >= rows) return 0;
        int i = index(Math.floorMod(column, columns), row);
        Page page = pages[i >>> PAGE_SHIFT];
        return page.present[i & PAGE_MASK] ? layer(page.colors[i & PAGE_MASK], page.sideColors[i & PAGE_MASK]) : 0;
    }

    /** Raw canonical height access for the low-detail cylindrical mesh. */
    public int cellHeight(int column, int row) {
        if (row < 0 || row >= rows) return (int)RingGeometry.SURFACE_Y;
        int index = index(Math.floorMod(column, columns), row);
        Page page = pages[index >>> PAGE_SHIFT];
        return page.present[index & PAGE_MASK] ? page.heights[index & PAGE_MASK] : (int)RingGeometry.SURFACE_Y;
    }

    /** Raw exposed block-light level, from zero through vanilla's maximum fifteen. */
    public int cellBlockLight(int column, int row) {
        if (row < 0 || row >= rows) return 0;
        int index = index(Math.floorMod(column, columns), row);
        Page page = pages[index >>> PAGE_SHIFT];
        return page.present[index & PAGE_MASK] ? page.blockLights[index & PAGE_MASK] & 15 : 0;
    }

    boolean shorelineCell(int column, int row) {
        int index = index(column, row);
        return shoreline(pages[index >>> PAGE_SHIFT], index & PAGE_MASK);
    }

    private static boolean shoreline(Page page, int offset) {
        return (page.blockLights[offset] & 0xf0) != 0
                || (page.colors[offset] & 0xffffff) == net.minecraft.world.level.material.MapColor.SAND.col
                || (page.colors[offset] & 0xffffff) == RingSurfaceLod.VANILLA_SAND_TOP_RGB;
    }

    public int cellWaterCoverage(int column, int row) {
        if (row < 0 || row >= rows) return 0;
        int index = index(Math.floorMod(column, columns), row);
        Page page = pages[index >>> PAGE_SHIFT];
        return page.present[index & PAGE_MASK] ? (page.blockLights[index & PAGE_MASK] & 255) >>> 4 : 0;
    }

    /**
     * Bilinearly samples the overview. X wraps at the exact ring seam. Missing
     * neighbours are ignored so a partly generated atlas improves the Arch
     * progressively without producing zero-height spikes.
     */
    public SurfaceSample sample(double canonicalX, double canonicalZ) {
        double atlasX = sampleCoordinate(geometry.wrapX(canonicalX));
        double atlasZ = sampleCoordinate(canonicalZ - geometry.minWidthZ());
        int x0 = (int)Math.floor(atlasX);
        int z0 = (int)Math.floor(atlasZ);
        double fx = atlasX - x0;
        double fz = atlasZ - z0;

        double height = 0.0;
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        double blockLight = 0.0;
        double waterCoverage = 0.0;
        double weightTotal = 0.0;
        for (int dz = 0; dz <= 1; dz++) {
            int row = Math.max(0, Math.min(rows - 1, z0 + dz));
            double wz = dz == 0 ? 1.0 - fz : fz;
            for (int dx = 0; dx <= 1; dx++) {
                int column = Math.floorMod(x0 + dx, columns);
                double wx = dx == 0 ? 1.0 - fx : fx;
                double weight = wx * wz;
                int index = index(column, row);
                Page page = pages[index >>> PAGE_SHIFT];
                if (!page.present[index & PAGE_MASK] || weight <= 0.0) continue;
                height += page.heights[index & PAGE_MASK] * weight;
                red += (page.colors[index & PAGE_MASK] >> 16 & 0xFF) * weight;
                green += (page.colors[index & PAGE_MASK] >> 8 & 0xFF) * weight;
                blue += (page.colors[index & PAGE_MASK] & 0xFF) * weight;
                blockLight += (page.blockLights[index & PAGE_MASK] & 15) * weight;
                waterCoverage += ((page.blockLights[index & PAGE_MASK] & 255) >>> 4) / 15.0 * weight;
                weightTotal += weight;
            }
        }
        if (weightTotal <= 0.0) return SurfaceSample.MISSING;
        int color = clampColor(red / weightTotal) << 16
                | clampColor(green / weightTotal) << 8
                | clampColor(blue / weightTotal);
        return new SurfaceSample(height / weightTotal, color,
                blockLight / weightTotal, weightTotal, waterCoverage / weightTotal);
    }

    public byte[] encodeTile(int tileX, int tileZ) {
        checkTile(tileX, tileZ);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAX_TILE_BYTES + 2);
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
                        Page page = pages[index >>> PAGE_SHIFT];
                        output.writeBoolean(page.present[index & PAGE_MASK]);
                        output.writeShort(page.heights[index & PAGE_MASK]);
                        output.writeInt(page.colors[index & PAGE_MASK]);
                        output.writeByte(page.blockLights[index & PAGE_MASK]);
                        output.writeInt(page.sideColors[index & PAGE_MASK]);
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("memory-backed atlas tile encoding failed", impossible);
        }
    }

    /**
     * Applies a server tile without erasing more-complete cached cells.
     *
     * @return true only when at least one present cell changed
     */
    public boolean applyTile(int tileX, int tileZ, byte[] data) throws IOException {
        checkTile(tileX, tileZ);
        if (data.length > MAX_TILE_BYTES + 2) throw new IOException("oversized terrain atlas tile");
        boolean changed = false;
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
                    int blockLight = input.readUnsignedByte();
                    int sideColor = input.readInt();
                    if (incomingPresent) {
                        changed |= putCell(firstX + x, firstZ + z, height, color,
                                blockLight & 15, sideColor, blockLight >>> 4, layer(color, sideColor));
                    }
                }
            }
            if (input.available() != 0) throw new IOException("trailing terrain atlas tile data");
        }
        return changed;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))))) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeLong(worldHash);
            output.writeInt(geometry.widthBlocks());
            output.writeInt(geometry.circumferenceBlocks());
            output.writeInt(sampleStep);
            output.writeInt(columns);
            output.writeInt(rows);
            output.writeLong(revision);
            for (int index = 0; index < cellCount; index++) {
                Page page = pages[index >>> PAGE_SHIFT];
                output.writeBoolean(page.present[index & PAGE_MASK]);
                output.writeShort(page.heights[index & PAGE_MASK]);
                output.writeInt(page.colors[index & PAGE_MASK]);
                output.writeByte(page.blockLights[index & PAGE_MASK]);
                output.writeInt(page.sideColors[index & PAGE_MASK]);
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
        return readCache(path, expectedGeometry, expectedHash, -1L).orElseThrow();
    }

    /** A stale client cache is a normal miss. Its body can be skipped entirely. */
    public static Optional<RingTerrainAtlas> loadIfRevisionMatches(Path path, RingGeometry geometry,
                                                                   long hash, long revision) throws IOException {
        if (revision < 0) throw new IllegalArgumentException("negative expected revision");
        return readCache(path, geometry, hash, revision);
    }

    /** Equal layout and revision can also describe two independently edited copies of a save. */
    public static Optional<RingTerrainAtlas> loadMatchingCache(Path path, RingGeometry geometry, long hash,
            long revision, int step, long fingerprint) throws IOException {
        return loadIfRevisionMatches(path, geometry, hash, revision)
                .filter(atlas -> atlas.sampleStep == step && atlas.contentFingerprint == fingerprint);
    }

    private static Optional<RingTerrainAtlas> readCache(Path path, RingGeometry geometry,
                                                        long expectedHash, long expectedRevision) throws IOException {
        try (var file = Files.newInputStream(path);
             DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(file, 8192)))) {
            if (input.readInt() != MAGIC) throw new IOException("not a RingWorld terrain atlas");
            if (input.readInt() != FORMAT_VERSION) throw new IOException("unsupported terrain atlas version");
            long hash = input.readLong();
            int width = input.readInt();
            int circumference = input.readInt();
            int sampleStep = input.readInt();
            if (width != geometry.widthBlocks() || circumference != geometry.circumferenceBlocks()
                    || hash != expectedHash || sampleStep <= 0 || 16 % sampleStep != 0
                    || input.readInt() != divideCeil(circumference, sampleStep)
                    || input.readInt() != divideCeil(width, sampleStep)) {
                throw new IOException("terrain atlas does not match this ring world");
            }
            long revision = input.readLong();
            if (revision < 0L) throw new IOException("negative terrain atlas revision");
            if (expectedRevision >= 0 && revision != expectedRevision) return Optional.empty();
            RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, hash, sampleStep);
            atlas.revision = revision;
            for (int index = 0; index < atlas.cellCount; index++) {
                if (!input.readBoolean()) {
                    input.skipNBytes(11); // Absent samples still occupy a fixed record in existing saves.
                    continue;
                }
                Page page = atlas.writablePage(index);
                page.present[index & PAGE_MASK] = true;
                page.heights[index & PAGE_MASK] = input.readShort();
                page.colors[index & PAGE_MASK] = input.readInt();
                page.blockLights[index & PAGE_MASK] = input.readByte();
                page.sideColors[index & PAGE_MASK] = input.readInt();
                atlas.contentFingerprint ^= sampleFingerprint(index, page, index & PAGE_MASK);
                atlas.changePresence(index % atlas.columns, index / atlas.columns, 1);
            }
            if (input.read() != -1) throw new IOException("trailing terrain atlas data");
            return Optional.of(atlas);
        }
    }

    /**
     * Loads the authoritative dimension-owned atlas or migrates a validated
     * legacy root atlas exactly once. An invalid authoritative file never
     * falls back to legacy state, and a legacy file is copied only after its
     * geometry and world hash have passed {@link #load}.
     */
    public static StorageLoad loadStorage(Path currentPath, Path legacyPath,
                                          RingGeometry expectedGeometry, long expectedHash) {
        return loadStorage(currentPath, legacyPath, expectedGeometry, expectedHash,
                SAMPLE_STEP_BLOCKS);
    }

    public static StorageLoad loadStorage(Path currentPath, Path legacyPath,
                                          RingGeometry expectedGeometry, long expectedHash,
                                          int expectedSampleStep) {
        if (Files.exists(currentPath)) {
            try {
                return new StorageLoad(
                        load(currentPath, expectedGeometry, expectedHash),
                        StorageStatus.CURRENT);
            } catch (IOException | IllegalArgumentException | ArithmeticException exception) {
                return new StorageLoad(
                        new RingTerrainAtlas(expectedGeometry, expectedHash, expectedSampleStep),
                        StorageStatus.INVALID_CURRENT);
            }
        }
        if (Files.isRegularFile(legacyPath)) {
            try {
                RingTerrainAtlas migrated = load(legacyPath, expectedGeometry, expectedHash);
                migrated.save(currentPath);
                return new StorageLoad(migrated, StorageStatus.MIGRATED_LEGACY);
            } catch (IOException | IllegalArgumentException | ArithmeticException exception) {
                return new StorageLoad(
                        new RingTerrainAtlas(expectedGeometry, expectedHash, expectedSampleStep),
                        StorageStatus.INVALID_LEGACY);
            }
        }
        return new StorageLoad(
                new RingTerrainAtlas(expectedGeometry, expectedHash, expectedSampleStep),
                StorageStatus.FRESH);
    }

    public long firstMissingChunkIndex() {
        int chunksAlong = geometry.circumferenceChunks();
        int chunksAcross = geometry.widthChunks();
        for (int chunkX = 0; chunkX < chunksAlong; chunkX++) {
            for (int chunkRow = 0; chunkRow < chunksAcross; chunkRow++) {
                if (!isChunkPresent(chunkX, chunkRow)) {
                    return (long)chunkX * chunksAcross + chunkRow;
                }
            }
        }
        return (long)chunksAlong * chunksAcross;
    }

    /** First Atlas row with its sample centre at or beyond this world Z boundary. */
    public int firstSampleRowAt(int blockZ) {
        int offset = blockZ - geometry.minWidthZ() - sampleStep / 2;
        return Math.clamp(Math.ceilDiv(offset, sampleStep), 0, rows);
    }

    public boolean isChunkPresent(int chunkX, int chunkRow) {
        if (chunkRow < 0 || chunkRow >= geometry.widthChunks()
                || chunkX < 0 || chunkX >= geometry.circumferenceChunks()) return false;
        return missingByChunk[chunkX * geometry.widthChunks() + chunkRow] == 0;
    }

    public long presentChunkCount() { return completeChunks; }

    private void changePresence(int column, int row, int delta) {
        int chunkX = (column * sampleStep) >> 4;
        int chunkZ = (geometry.minWidthZ() + row * sampleStep + sampleStep / 2) >> 4;
        int chunk = chunkX * geometry.widthChunks() + chunkZ - geometry.minChunkZ();
        if (missingByChunk[chunk] == 0) completeChunks--;
        missingByChunk[chunk] -= delta;
        if (missingByChunk[chunk] == 0) completeChunks++;
        int tile = (row / TILE_SIZE) * tileColumns() + column / TILE_SIZE;
        samplesByTile[tile] += delta;
        populatedTiles.set(tile, samplesByTile[tile] != 0);
        presentCount += delta;
    }

    private void resetCoverage() {
        int across = geometry.widthChunks();
        completeChunks = 0;
        for (int row = 0; row < across; row++) {
            int z = (geometry.minChunkZ() + row) * 16;
            missingByChunk[row] = (short)((firstSampleRowAt(z + 16) - firstSampleRowAt(z)) * (16 / sampleStep));
            if (missingByChunk[row] == 0) completeChunks += geometry.circumferenceChunks();
        }
        for (int i = across; i < missingByChunk.length; i += across) {
            System.arraycopy(missingByChunk, 0, missingByChunk, i, across);
        }
    }

    public void clear() {
        Arrays.fill(pages, EMPTY);
        writable.clear();
        presentCount = 0;
        contentFingerprint = 0;
        populatedTiles.clear();
        Arrays.fill(samplesByTile, (short)0);
        resetCoverage();
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

    public record SurfaceSample(double height, int color, double blockLight, double coverage,
                                double waterCoverage) {
        public SurfaceSample(double height, int color, double blockLight, double coverage) {
            this(height, color, blockLight, coverage, 0.0);
        }
        public static final SurfaceSample MISSING = new SurfaceSample(
                RingGeometry.SURFACE_Y, -1, 0.0, 0.0);
        public boolean present() { return color >= 0 && coverage > 0.0; }
    }

    public record StorageLoad(RingTerrainAtlas atlas, StorageStatus status) { }

    public enum StorageStatus {
        CURRENT,
        MIGRATED_LEGACY,
        FRESH,
        INVALID_CURRENT,
        INVALID_LEGACY
    }
}
