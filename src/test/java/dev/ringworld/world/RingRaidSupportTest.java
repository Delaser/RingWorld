package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RingRaidSupportTest {
    private final RingGeometry geometry = new RingGeometry(416, 2_048);

    @Test
    void periodicThreeDimensionalDistanceKeepsSeamTargetsLocal() {
        assertEquals(17.0, RingRaidSupport.periodicDistanceSquared(
                geometry, 2_047.0, 70.0, -3.0, 1.0, 72.0, 0.0), 1.0e-9);
    }

    @Test
    void nearestActiveCenterUsesPeriodicDistanceAndStableTies() {
        RingRaidSupport.Center seamCenter = new RingRaidSupport.Center(1, 64, 0);
        RingRaidSupport.Center distantCenter = new RingRaidSupport.Center(1_000, 64, 0);
        assertEquals(seamCenter, RingRaidSupport.nearestActiveCenter(
                geometry, 2_046.0, 64.0, 0.0, List.of(seamCenter, distantCenter)).orElseThrow());

        RingRaidSupport.Center firstTie = new RingRaidSupport.Center(2_047, 64, 0);
        RingRaidSupport.Center secondTie = new RingRaidSupport.Center(1, 64, 0);
        assertEquals(firstTie, RingRaidSupport.nearestActiveCenter(
                geometry, 0.0, 64.0, 0.0, List.of(firstTie, secondTie)).orElseThrow());
    }

    @Test
    void canonicalizesAndDeduplicatesNearestImagePoiCentres() {
        assertEquals(new RingRaidSupport.Center(2_047, 70, 4),
                RingRaidSupport.canonicalCenter(geometry, -1, 70, 4));
        assertEquals(new RingRaidSupport.Center(0, 70, 4),
                RingRaidSupport.canonicalCenter(geometry, 2_048, 70, 4));
        assertEquals(List.of(new RingRaidSupport.Center(2_047, 70, 4),
                        new RingRaidSupport.Center(0, 70, 4)),
                RingRaidSupport.canonicalDistinctCenters(geometry, List.of(
                        new RingRaidSupport.Center(-1, 70, 4),
                        new RingRaidSupport.Center(2_047, 70, 4),
                        new RingRaidSupport.Center(2_048, 70, 4),
                        new RingRaidSupport.Center(0, 70, 4))));
    }

    @Test
    void averagesASeamVillageInNearestImagesBeforeCanonicalizing() {
        assertEquals(List.of(2_046, -2, 4_094), RingRaidSupport.periodicQueryXs(geometry, 2_046));
        assertEquals(new RingRaidSupport.Center(2_047, 65, 0),
                RingRaidSupport.averageCanonicalPoiCenter(geometry, 2_046.0, List.of(
                        new RingRaidSupport.Center(2_044, 64, -1),
                        new RingRaidSupport.Center(-4, 64, -1),
                        new RingRaidSupport.Center(2, 66, 1),
                        new RingRaidSupport.Center(2_050, 66, 1))).orElseThrow());
    }

    @Test
    void emptyPoiCollectionHasNoAverageCenter() {
        assertEquals(java.util.Optional.empty(),
                RingRaidSupport.averageCanonicalPoiCenter(geometry, 0.0, List.of()));
    }

    @Test
    void waveReadinessWindowsSplitAtTheSeamAndCoverTheWholeRingOnce() {
        assertEquals(List.of(new RingRaidSupport.XWindow(2_046, 2_047),
                        new RingRaidSupport.XWindow(0, 2)),
                RingRaidSupport.canonicalBlockWindows(geometry, -2, 2));
        assertEquals(List.of(new RingRaidSupport.XWindow(127, 127),
                        new RingRaidSupport.XWindow(0, 0)),
                RingRaidSupport.canonicalChunkWindows(geometry, -2, 2));
        assertEquals(List.of(new RingRaidSupport.XWindow(0, 127)),
                RingRaidSupport.canonicalChunkWindows(geometry, -100, 3_000));
        assertEquals(1, RingRaidSupport.canonicalBlockWindows(geometry, 0, 0).size());
    }
}
