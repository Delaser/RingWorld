package dev.ringworld.server;

import java.util.LinkedHashSet;
import java.util.Set;

/** Server-thread queue that keeps changed tiles visible until the adapter drains them. */
final class RingAtlasDirtyTileQueue {
    private final Set<RingAtlasPregenerationService.TileCoordinate> tiles = new LinkedHashSet<>();

    void publish(RingAtlasPregenerationService.TileCoordinate tile) { tiles.add(tile); }
    boolean hasPending() { return !tiles.isEmpty(); }
    Set<RingAtlasPregenerationService.TileCoordinate> drain() {
        if (tiles.isEmpty()) return Set.of();
        Set<RingAtlasPregenerationService.TileCoordinate> published = Set.copyOf(tiles);
        tiles.clear();
        return published;
    }
}
