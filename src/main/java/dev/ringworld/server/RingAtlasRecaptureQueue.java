package dev.ringworld.server;

import dev.ringworld.world.RingAtlasSurfaceInvalidation;
import dev.ringworld.world.RingTerrainAtlas;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounded exact-cell queue that collapses extreme bulk edits into atlas tiles. */
final class RingAtlasRecaptureQueue {
    static final int MAX_EXACT_CELLS = 4_096;
    private final Set<RingAtlasSurfaceInvalidation.Cell> exact = new LinkedHashSet<>();
    private final Set<RingAtlasPregenerationService.TileCoordinate> overflowTiles =
            new LinkedHashSet<>();
    private RingAtlasPregenerationService.TileCoordinate activeTile;
    private int activeTileOffset;
    private boolean overflowTurn;
    // Park unloaded samples until their chunk's normal full capture, or persist them as missing at stop.
    private final BitSet deferred = new BitSet();
    private int finalDrainCursor = -1;

    void defer(RingAtlasSurfaceInvalidation.Cell cell, int columns) {
        deferred.set(cell.row() * columns + cell.column());
    }

    void capturedRange(int first, int count) {
        if (!deferred.isEmpty()) deferred.clear(first, first + count);
    }

    void beginFinalDrain() { finalDrainCursor = 0; }

    void enqueue(RingAtlasSurfaceInvalidation.Cell cell) {
        if (exact.size() < MAX_EXACT_CELLS) {
            exact.add(cell);
            return;
        }
        overflowTiles.add(new RingAtlasPregenerationService.TileCoordinate(
                cell.column() / RingTerrainAtlas.TILE_SIZE,
                cell.row() / RingTerrainAtlas.TILE_SIZE));
    }

    List<RingAtlasSurfaceInvalidation.Cell> drain(int maximum, int columns, int rows) {
        if (maximum <= 0 || columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("recapture drain bounds must be positive");
        }
        List<RingAtlasSurfaceInvalidation.Cell> drained = new ArrayList<>(maximum);
        int exactLimit = maximum;
        if (!exact.isEmpty() && (activeTile != null || !overflowTiles.isEmpty())) {
            // Unloaded cells may re-enter exact every tick. Give both queues a share, even with a budget of one.
            exactLimit = maximum / 2 + (overflowTurn ? 0 : maximum % 2);
            overflowTurn = !overflowTurn;
        }
        Iterator<RingAtlasSurfaceInvalidation.Cell> exactIterator = exact.iterator();
        while (exactIterator.hasNext() && drained.size() < exactLimit) {
            drained.add(exactIterator.next());
            exactIterator.remove();
        }
        while (drained.size() < maximum) {
            if (activeTile == null) {
                Iterator<RingAtlasPregenerationService.TileCoordinate> tiles = overflowTiles.iterator();
                if (!tiles.hasNext()) break;
                activeTile = tiles.next();
                tiles.remove();
                activeTileOffset = 0;
            }
            int localX = activeTileOffset % RingTerrainAtlas.TILE_SIZE;
            int localZ = activeTileOffset / RingTerrainAtlas.TILE_SIZE;
            int column = activeTile.x() * RingTerrainAtlas.TILE_SIZE + localX;
            int row = activeTile.z() * RingTerrainAtlas.TILE_SIZE + localZ;
            activeTileOffset++;
            if (activeTileOffset == RingTerrainAtlas.TILE_SIZE * RingTerrainAtlas.TILE_SIZE) {
                activeTile = null;
                activeTileOffset = 0;
            }
            if (column < columns && row < rows) {
                drained.add(new RingAtlasSurfaceInvalidation.Cell(column, row));
            }
        }
        while (finalDrainCursor >= 0 && drained.size() < maximum) {
            int index = deferred.nextSetBit(finalDrainCursor);
            if (index < 0) break;
            deferred.clear(index);
            drained.add(new RingAtlasSurfaceInvalidation.Cell(index % columns, index / columns));
            finalDrainCursor = index + 1;
        }
        return drained;
    }

    boolean hasPending() { return !exact.isEmpty() || activeTile != null || !overflowTiles.isEmpty() || finalDrainCursor >= 0 && !deferred.isEmpty(); }
    int exactCount() { return exact.size(); }
    int overflowTileCount() { return overflowTiles.size() + (activeTile == null ? 0 : 1); }
}
