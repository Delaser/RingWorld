package dev.ringworld.world;

import java.util.Objects;

/**
 * Session-owned storage for serial surface builds. Only an open exclusive lease
 * may access the captured Atlas and scratch arrays. A renderer drops the entire
 * workspace on teardown; an abandoned worker can finish only its old lease.
 */
public final class RingSurfaceBuildWorkspace {
    private RingTerrainAtlas snapshot;
    private RingTerrainAtlas reducedSnapshot;
    private int[] pixels, blockLights;
    private final int[][] mipPixels = new int[32][];
    private float[] heights;
    private Lease active;

    /** Caller thread captures live data before handing this lease to its worker. */
    public synchronized Lease capture(RingTerrainAtlas source) {
        Objects.requireNonNull(source, "source");
        if (active != null) throw new IllegalStateException("surface workspace is still leased");
        if (snapshot == null || !snapshot.geometry().equals(source.geometry())
                || snapshot.worldHash() != source.worldHash()
                || snapshot.sampleStep() != source.sampleStep()) {
            snapshot = new RingTerrainAtlas(source.geometry(), source.worldHash(), source.sampleStep());
        }
        source.copySnapshotTo(snapshot);
        active = new Lease();
        return active;
    }

    public final class Lease implements AutoCloseable {
        private boolean closed;

        private Lease() { }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("surface workspace lease is closed");
        }

        public RingTerrainAtlas atlas() {
            requireOpen();
            return snapshot;
        }

        /** Worker-only, under this lease; the public independent display-copy API is unchanged. */
        public RingTerrainAtlas displayAtlas(RingLodQuality quality) {
            requireOpen();
            if (quality == null || !snapshot.isComplete() || quality.sampleStep() <= snapshot.sampleStep()) return snapshot;
            if (reducedSnapshot == null || !reducedSnapshot.geometry().equals(snapshot.geometry())
                    || reducedSnapshot.worldHash() != snapshot.worldHash()
                    || reducedSnapshot.sampleStep() != quality.sampleStep()) {
                reducedSnapshot = new RingTerrainAtlas(snapshot.geometry(), snapshot.worldHash(), quality.sampleStep());
            }
            quality.copyDisplaySnapshotTo(snapshot, reducedSnapshot);
            return reducedSnapshot;
        }

        public int[] mipPixels(int level, int count) {
            requireOpen();
            if (level <= 0 || level >= mipPixels.length || count <= 0) throw new IllegalArgumentException("invalid mip scratch dimensions");
            if (mipPixels[level] == null || mipPixels[level].length != count) mipPixels[level] = new int[count];
            return mipPixels[level];
        }

        /** Each build overwrites every element before reading these scratch arrays. */
        public int[] pixels(int count) {
            requireOpen();
            if (count <= 0) throw new IllegalArgumentException("positive pixel count required");
            if (pixels == null || pixels.length != count) pixels = new int[count];
            return pixels;
        }

        public int[] blockLights(int count) {
            requireOpen();
            if (count <= 0) throw new IllegalArgumentException("positive pixel count required");
            if (blockLights == null || blockLights.length != count) blockLights = new int[count];
            return blockLights;
        }

        public float[] heights(int count) {
            requireOpen();
            if (count <= 0) throw new IllegalArgumentException("positive pixel count required");
            if (heights == null || heights.length != count) heights = new float[count];
            return heights;
        }

        @Override
        public void close() {
            synchronized (RingSurfaceBuildWorkspace.this) {
                if (closed) return;
                if (active != this) throw new IllegalStateException("surface lease ownership differs");
                closed = true;
                active = null;
            }
        }
    }
}
