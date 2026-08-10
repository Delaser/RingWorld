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
}
