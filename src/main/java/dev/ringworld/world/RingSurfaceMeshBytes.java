package dev.ringworld.world;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Identity of the actual packed vertex bytes, resolved only on the surface worker. */
public record RingSurfaceMeshBytes(int vertexCount, int vertexSize, String sha256) {
    public RingSurfaceMeshBytes {
        if (vertexCount <= 0 || vertexSize <= 0 || sha256 == null
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid packed mesh identity");
        }
        Math.multiplyExact(vertexCount, vertexSize);
    }

    /** Hash only the submitted range without modifying the allocator's buffer view. */
    public static RingSurfaceMeshBytes identify(ByteBuffer vertices, int count, int size) {
        Objects.requireNonNull(vertices, "vertices");
        if (count <= 0 || size <= 0 || vertices.remaining() != Math.multiplyExact(count, size)) {
            throw new IllegalArgumentException("packed vertex length does not match draw state");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(vertices.asReadOnlyBuffer());
            return new RingSurfaceMeshBytes(count, size, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("required SHA-256 provider unavailable", impossible);
        }
    }

    /** Never reuse ownership from another world/layout or a missing GPU resource. */
    public boolean canReuse(boolean sameAtlas, boolean hasMesh, RingSurfaceMeshBytes buffered) {
        return sameAtlas && hasMesh && equals(buffered);
    }
}
