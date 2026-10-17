package dev.ringworld.world;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/** Exclusive cache encoding storage; it never provides terrain or renderer sampling. */
final class RingAtlasCacheImage implements AutoCloseable {
    private final RingGeometry geometry;
    private final long worldHash;
    private final int sampleStep, columns, rows, cells;
    private final RingAtlasCacheWriter.Memory memory;
    private ShortBuffer heights;
    private IntBuffer colors, sideColors;
    private ByteBuffer lights, presence;
    private long revision;
    private boolean closed;

    RingAtlasCacheImage(RingTerrainAtlas source, RingAtlasCacheWriter.MemoryFactory factory) {
        geometry=source.geometry(); worldHash=source.worldHash(); sampleStep=source.sampleStep();
        columns=source.columns(); rows=source.rows(); cells=source.cellCount();
        int bytes=Math.multiplyExact(cells,12);
        memory=Objects.requireNonNull(factory.allocate(bytes),"cache memory");
        try {
            ByteBuffer buffer=Objects.requireNonNull(memory.buffer(),"cache buffer");
            if(buffer.isReadOnly() || buffer.position()!=0 || buffer.limit()!=bytes || buffer.capacity()!=bytes)
                throw new IllegalArgumentException("exclusive full-range cache storage required");
            heights=slice(buffer,0,Math.multiplyExact(cells,2)).asShortBuffer();
            colors=slice(buffer,Math.multiplyExact(cells,2),Math.multiplyExact(cells,4)).asIntBuffer();
            sideColors=slice(buffer,Math.multiplyExact(cells,6),Math.multiplyExact(cells,4)).asIntBuffer();
            lights=slice(buffer,Math.multiplyExact(cells,10),cells);
            presence=slice(buffer,Math.multiplyExact(cells,11),cells);
        } catch(RuntimeException | Error failure) { memory.close(); throw failure; }
    }

    private static ByteBuffer slice(ByteBuffer source,int offset,int length) {
        ByteBuffer view=source.duplicate();view.position(offset).limit(Math.addExact(offset,length));
        return view.slice().order(ByteOrder.nativeOrder());
    }

    boolean matches(RingTerrainAtlas source) {
        return !closed && geometry.equals(source.geometry()) && worldHash==source.worldHash()
                && sampleStep==source.sampleStep();
    }

    /** Caller-owned source capture; the writer's lock excludes active encoders. */
    void capture(short[] sourceHeights,int[] sourceColors,int[] sourceSides,byte[] sourceLights,
                 boolean[] sourcePresence,long sourceRevision) {
        requireOpen();
        if(sourceHeights.length!=cells || sourceColors.length!=cells || sourceSides.length!=cells
                || sourceLights.length!=cells || sourcePresence.length!=cells || sourceRevision<0)
            throw new IllegalArgumentException("cache capture dimensions/revision differ");
        heights.clear();heights.put(sourceHeights);
        colors.clear();colors.put(sourceColors);
        sideColors.clear();sideColors.put(sourceSides);
        lights.clear();lights.put(sourceLights);
        for(int i=0;i<cells;i++) presence.put(i,sourcePresence[i]?(byte)1:(byte)0);
        revision=sourceRevision;
    }

    RingGeometry geometry() { return geometry; }
    long worldHash() { return worldHash; }
    int sampleStep() { return sampleStep; }
    int cellCount() { return cells; }
    long revision() { return revision; }
    int cellHeight(int column,int row) {
        requireOpen();
        return heights.get(Math.floorMod(column,columns)+Math.max(0,Math.min(rows-1,row))*columns);
    }

    /** Byte-identical format11 encoding, on the cache worker only. */
    void save(Path path) throws IOException {
        requireOpen();
        Files.createDirectories(path.getParent());
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        try(DataOutputStream output=new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))))) {
            output.writeInt(0x52574154);
            output.writeInt(RingTerrainAtlas.FORMAT_VERSION);
            output.writeLong(worldHash);
            output.writeInt(geometry.widthBlocks());output.writeInt(geometry.circumferenceBlocks());
            output.writeInt(sampleStep);output.writeInt(columns);output.writeInt(rows);output.writeLong(revision);
            // The image and output belong exclusively to this worker. Batch
            // fixed-width records instead of locking the output stream for
            // each of the five primitive values in every cell. Native storage
            // remains in its capture-friendly channel layout; all interleaving
            // and big-endian conversion happen here, away from the caller.
            byte[] bytes=new byte[4096*12];
            ByteBuffer encoded=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            for(int first=0;first<cells;first+=4096) {
                encoded.clear();
                int end=first+Math.min(4096,cells-first);
                for(int i=first;i<end;i++)
                    encoded.put(presence.get(i)).putShort(heights.get(i))
                            .putInt(colors.get(i)).put(lights.get(i)).putInt(sideColors.get(i));
                output.write(bytes,0,encoded.position());
            }
        }
        try { Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException unsupported) { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
    }

    private void requireOpen() { if(closed)throw new IllegalStateException("cache image is closed"); }

    @Override public void close() {
        if(closed)return;
        closed=true;
        heights=null;colors=null;sideColors=null;lights=null;presence=null;
        memory.close();
    }

    static RingAtlasCacheWriter.Memory allocateHeap(int bytes) {
        return new HeapMemory(ByteBuffer.allocate(bytes));
    }

    private static final class HeapMemory implements RingAtlasCacheWriter.Memory {
        private ByteBuffer buffer;
        HeapMemory(ByteBuffer buffer) { this.buffer=buffer; }
        @Override public ByteBuffer buffer() { return Objects.requireNonNull(buffer,"closed cache memory"); }
        @Override public void close() { buffer=null; }
    }
}
