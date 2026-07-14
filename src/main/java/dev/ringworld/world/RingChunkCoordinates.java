package dev.ringworld.world;

/** Canonical chunk-index operations for the periodic circumference axis. */
public final class RingChunkCoordinates {
    private RingChunkCoordinates() { }

    public static int circumferenceChunks(RingGeometry geometry) {
        return geometry.circumferenceBlocks() / 16;
    }

    public static int wrapChunkX(int chunkX, RingGeometry geometry) {
        return Math.floorMod(chunkX, circumferenceChunks(geometry));
    }

    /**
     * Returns the periodic copy of a canonical chunk nearest a client's
     * logical chunk coordinate. This is what keeps client chunk streaming
     * continuous while server storage remains finite.
     */
    public static int nearestImageChunkX(int canonicalChunkX, int referenceChunkX, RingGeometry geometry) {
        int circumference = circumferenceChunks(geometry);
        int wrapped = Math.floorMod(canonicalChunkX, circumference);
        long imageIndex = Math.round((referenceChunkX - (double) wrapped) / circumference);
        long result = wrapped + imageIndex * (long) circumference;
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("ring chunk image exceeds vanilla coordinate range");
        }
        return (int) result;
    }
}
