package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.world.RingGeometry;

/** Exact Create 6.0.10 client accumulator repair for periodic gantry motion. */
public final class RingCreate610GantryCoordinates {
    private RingCreate610GantryCoordinates() { }

    /**
     * Projects an X-axis server correction into the nearest presentation image.
     * Invalid or inapplicable inputs preserve Create's original accumulator.
     */
    public static double nearestClientOffset(
            double currentAxisCoord, double clientOffsetDiff,
            RingGeometry geometry, boolean xAxis) {
        if (!xAxis || geometry == null
                || !Double.isFinite(currentAxisCoord)
                || !Double.isFinite(clientOffsetDiff)) {
            return clientOffsetDiff;
        }
        double packetAxisCoord = currentAxisCoord + clientOffsetDiff;
        if (!Double.isFinite(packetAxisCoord)) return clientOffsetDiff;
        double nearestPacketCoord = geometry.nearestImageX(packetAxisCoord, currentAxisCoord);
        double nearestDiff = nearestPacketCoord - currentAxisCoord;
        return Double.isFinite(nearestPacketCoord) && Double.isFinite(nearestDiff)
                ? nearestDiff : clientOffsetDiff;
    }
}
