package dev.ringworld.world;

/** Canonical chunk-index operations for the periodic circumference axis. */
public final class RingChunkCoordinates {
    private RingChunkCoordinates() { }

    public static int circumferenceChunks(RingGeometry geometry) {
        return geometry.circumferenceChunks();
    }

    public static int wrapChunkX(int chunkX, RingGeometry geometry) {
        return Math.floorMod(chunkX, circumferenceChunks(geometry));
    }

    /**
     * Mirrors vanilla's square simulation-distance test while joining the two
     * circumference edges. The chunk-level propagator remains authoritative;
     * this helper is also used as a loaded-entity fallback while its queued
     * player-ticket update settles at a natural seam crossing.
     */
    public static boolean isWithinSimulationDistance(int entityChunkX, int entityChunkZ,
                                                     int playerChunkX, int playerChunkZ,
                                                     int simulationDistance,
                                                     RingGeometry geometry) {
        int circumference = circumferenceChunks(geometry);
        int canonicalEntityX = Math.floorMod(entityChunkX, circumference);
        int canonicalPlayerX = Math.floorMod(playerChunkX, circumference);
        int directX = Math.abs(canonicalEntityX - canonicalPlayerX);
        int periodicX = Math.min(directX, circumference - directX);
        long deltaZ = Math.abs((long) entityChunkZ - playerChunkZ);
        return periodicX <= simulationDistance && deltaZ <= simulationDistance;
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
