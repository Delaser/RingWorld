package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingSeamTerrainAuditTest {
    @Test
    void allowsIsolatedNaturalCliffs() {
        int[] high = new int[128];
        int[] low = new int[128];
        java.util.Arrays.fill(high, 64);
        java.util.Arrays.fill(low, 65);
        low[31] = 90;
        low[78] = 42;

        RingSeamTerrainAudit.Report report = RingSeamTerrainAudit.inspect(high, low);
        assertTrue(report.passes());
        assertTrue(report.passesSmoothJoin(), "isolated natural cliffs are not a map boundary wall");
        assertEquals(1, report.longestCliffRun());
    }

    @Test
    void rejectsAContinuousMapBoundaryWall() {
        int[] high = new int[128];
        int[] low = new int[128];
        java.util.Arrays.fill(high, 64);
        java.util.Arrays.fill(low, 64);
        java.util.Arrays.fill(low, 40, 72, 96);

        RingSeamTerrainAudit.Report report = RingSeamTerrainAudit.inspect(high, low);
        assertFalse(report.passes());
        assertEquals(32, report.longestCliffRun());
    }

    @Test
    void smoothJoinGateRejectsAVisibleWallBelowTheOldCliffThreshold() {
        int[] high = new int[246];
        int[] low = new int[246];
        java.util.Arrays.fill(low, 0, 100, 9);

        RingSeamTerrainAudit.Report report = RingSeamTerrainAudit.inspect(high, low);
        assertTrue(report.passes(), "nine-block wall stays below the historic cliff threshold");
        assertFalse(report.passesSmoothJoin(), "a broad nine-block join must still fail");

        java.util.Arrays.fill(low, 0);
        low[30] = 8;
        assertTrue(RingSeamTerrainAudit.inspect(high, low).passesSmoothJoin(),
                "one isolated natural feature is not a seam wall");
    }

    @Test
    void acceptsMeasuredProductionJoinWithOneIsolatedTwelveBlockStep() {
        RingSeamTerrainAudit.Report measured = new RingSeamTerrainAudit.Report(
                12, 1, 1, 1.1991869918699187, true);

        assertTrue(measured.passesSmoothJoin(),
                "a single natural step across the full ring width is not the old flat seam wall");
    }
}
