package dev.ringworld.world;

/** Pure revision policy for the client Atlas height mesh. */
public final class RingSurfaceMeshRefreshPolicy {
    private RingSurfaceMeshRefreshPolicy() { }

    public static boolean shouldRebuild(boolean sameAtlas, boolean hasVertexBuffer,
                                        boolean detailed, boolean bufferedDetailed,
                                        long heightFingerprint, long bufferedHeightFingerprint) {
        return !sameAtlas || !hasVertexBuffer || detailed != bufferedDetailed
                || detailed && heightFingerprint != bufferedHeightFingerprint;
    }
}
