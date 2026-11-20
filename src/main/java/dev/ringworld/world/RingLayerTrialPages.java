package dev.ringworld.world;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Immutable loopback transport for measuring the trial with a real cold and warm client cache. */
final class RingLayerTrialPages {
    private static final String ROOT = System.getProperty("ringworld.layerTrialManifest");
    static final boolean ENABLED = ROOT != null;
    record Window(int first, int count) {}
    static volatile Window window = new Window(0, 0);
    private static Header manifest;
    record Header(long world, int segments, int bands, float minZ, float maxZ, int pageSegments,
                  boolean[] mask, String[] digests) {}

    static Header header() throws IOException {
        if (manifest != null) return manifest;
        try (var in = new DataInputStream(new ByteArrayInputStream(read(ROOT, 2_000_000)))) {
            if (in.readInt() != 0x52574c32) throw new IOException("Invalid trial manifest");
            long world = in.readLong();
            int segments = in.readInt(), bands = in.readInt();
            float minZ = in.readFloat(), maxZ = in.readFloat();
            int pageSegments = in.readInt();
            if (segments != 8192 || bands != 128 || pageSegments != 16) throw new IOException("Unexpected trial lattice");
            boolean[] mask = new boolean[segments * bands];
            for (int i = 0; i < mask.length; i++) mask[i] = in.readBoolean();
            String[] digests = new String[segments / pageSegments];
            for (int i = 0; i < digests.length; i++) {
                byte[] hash = new byte[32];
                in.readFully(hash);
                digests[i] = HexFormat.of().formatHex(hash);
            }
            manifest = new Header(world, segments, bands, minZ, maxZ, pageSegments, mask, digests);
            return manifest;
        }
    }

    static byte[] read(String hash, int maxUnpacked) throws IOException {
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid layer digest");
        Path dir = Path.of("ringworld-cache", "trial-layers");
        Path path = dir.resolve(hash);
        byte[] bytes = Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        if (bytes == null || !digest(bytes).equals(hash)) {
            var connection = URI.create("http://127.0.0.1:25810/" + hash).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            try (var stream = connection.getInputStream()) { bytes = stream.readNBytes(maxUnpacked + 1024); }
            if (!digest(bytes).equals(hash)) throw new IOException("Layer digest mismatch");
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "layer-", ".part");
            try {
                Files.write(tmp, bytes);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
            System.out.println("Layer trial downloaded bytes=" + bytes.length);
        }
        try (var gz = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] page = gz.readNBytes(maxUnpacked + 1);
            if (page.length > maxUnpacked) throw new IOException("Layer page exceeds trial limit");
            return page;
        }
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
