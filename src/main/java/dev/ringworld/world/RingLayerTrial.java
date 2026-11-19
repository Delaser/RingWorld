package dev.ringworld.world;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Opt-in development capture of a saved world's two-layer patches. */
public final class RingLayerTrial {
    static final float ORIGINAL = 4f;
    static final float REPLACEMENT = 8f;
    private final long worldHash;
    private final int segments, bands;
    private final float minZ, maxZ;
    private final boolean[] replaced;
    private final int[] colors;
    private final float[] corners;

    private RingLayerTrial(DataInputStream in) throws IOException {
        if (in.readInt() != 0x52574c31) throw new IOException("Unknown two-layer capture format");
        worldHash = in.readLong();
        segments = in.readInt(); bands = in.readInt();
        if (segments < 1 || segments > 4096 || bands < 1 || bands > 1024) throw new IOException("Invalid lattice");
        minZ = in.readFloat(); maxZ = in.readFloat();
        replaced = new boolean[segments * bands];
        for (int i = 0; i < replaced.length; i++) replaced[i] = in.readBoolean();
        int faces = in.readInt();
        if (faces < 0 || faces > 500_000) throw new IOException("Invalid trial face count");
        colors = new int[faces];
        corners = new float[faces * 12];
        for (int i = 0; i < faces; i++) {
            colors[i] = in.readInt();
            for (int j = 0; j < 12; j++) corners[i*12+j] = in.readFloat();
        }
    }

    private RingLayerTrial(RingLayerTrialPages.Header header, boolean[] mask, byte[][] pages) throws IOException {
        worldHash = header.world();
        segments = header.segments();
        bands = header.bands();
        minZ = header.minZ();
        maxZ = header.maxZ();
        replaced = mask;
        int faces = 0;
        for (byte[] page : pages) {
            if (page.length % 52 != 0) throw new IOException("Invalid page length");
            faces = Math.addExact(faces, page.length / 52);
        }
        colors = new int[faces];
        corners = new float[Math.multiplyExact(faces, 12)];
        int pos = 0;
        for (byte[] page : pages) {
            var data = java.nio.ByteBuffer.wrap(page);
            while (data.hasRemaining()) {
                colors[pos] = data.getInt();
                for (int j = 0; j < 12; j++) corners[pos*12+j] = data.getFloat();
                pos++;
            }
        }
    }

    public static int cameraWindow(double x, int circumference) {
        if (!RingLayerTrialPages.ENABLED) return 0;
        return Math.floorMod((int)Math.floor(x / 256), Math.max(1, circumference / 256));
    }

    public static void selectWindow(int window) {
        RingLayerTrialPages.window = window;
    }

    private static final class Capture {
        static final RingLayerTrial DATA = load();
        private static RingLayerTrial load() {
            String path = System.getProperty("ringworld.layerTrial");
            if (path == null) return null;
            try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(Path.of(path)))))) {
                return new RingLayerTrial(in);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read two-layer trial capture", e);
            }
        }
    }

    static RingLayerTrial matching(RingTerrainAtlas atlas, int segments, int bands, double minZ, double maxZ) {
        if (RingLayerTrialPages.ENABLED) {
            try {
                var header = RingLayerTrialPages.header();
                if (header.world() != atlas.worldHash() || segments != header.segments() || bands != header.bands()
                        || header.minZ() != minZ || header.maxZ() != maxZ) return null;
                int first = RingLayerTrialPages.window * 4 - 6;
                byte[][] pages = new byte[16][];
                boolean[] mask = new boolean[segments * bands];
                for (int i = 0; i < pages.length; i++) {
                    int page = Math.floorMod(first+i, header.digests().length);
                    pages[i] = RingLayerTrialPages.read(header.digests()[page], 8_000_000);
                    int stride = header.pageSegments() * bands;
                    System.arraycopy(header.mask(), page * stride, mask, page * stride, stride);
                }
                var result = new RingLayerTrial(header, mask, pages);
                System.out.println("Layer trial page window " + first + " faces=" + result.colors.length);
                return result;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read trial layer pages", e);
            }
        }
        var data = Capture.DATA;
        if (data == null || data.worldHash != atlas.worldHash() || data.segments != segments || data.bands != bands
                || data.minZ != minZ || data.maxZ != maxZ) return null;
        return data;
    }

    boolean replaces(int segment, int band) { return replaced[segment * bands + band]; }
    int vertexCount() { return colors.length * 6; }

    void emit(RingSurfaceMesh.VertexConsumer out, RingGeometry geometry) {
        for (int i = 0; i < colors.length; i++) {
            out.sideColor(colors[i]);
            for (int corner : TRIANGLES) {
                int offset = i*12 + corner*3;
                double angle = corners[offset] * (2 * Math.PI / geometry.circumferenceBlocks());
                double radius = geometry.physicalRadiusAt(corners[offset+1]);
                out.vertex((float)(radius * Math.sin(angle)), (float)(-radius * Math.cos(angle)),
                        corners[offset+2], REPLACEMENT, 0.5f);
            }
        }
    }

    private static final int[] TRIANGLES = {0, 1, 2, 0, 2, 3};
}
