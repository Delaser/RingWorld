package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSurfaceBuildSnapshotTest {
    private static final long HASH = 0x534E_4150L;

    @Test
    void retainsExactContentAndDetectsHeightChangesAfterLiveAtlasAdvances() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        RingTerrainAtlas live = completeAtlas(geometry, 71);
        RingSurfaceBuildSnapshot build = new RingSurfaceBuildSnapshot(live.snapshot(), 4)
                .resolveDetailedHeightFingerprint();

        live.putCell(0, 0, 129, 0x112233);
        RingSurfaceBuildSnapshot advanced = new RingSurfaceBuildSnapshot(live.snapshot(), 5)
                .resolveDetailedHeightFingerprint();

        assertEquals(71, build.atlas().cellHeight(0, 0));
        assertEquals(129, live.cellHeight(0, 0));
        assertNotEquals(build.heightFingerprint(), advanced.heightFingerprint());
        assertTrue(build.matches(geometry, HASH, 4));
    }

    @Test
    void partialBuildUsesSentinelWithoutInvokingHeightScan() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        RingTerrainAtlas partial = new RingTerrainAtlas(geometry, HASH);
        partial.putCell(0, 0, 71, 0x336699);
        RingSurfaceBuildSnapshot build = new RingSurfaceBuildSnapshot(partial.snapshot(), 4);
        AtomicInteger scans = new AtomicInteger();

        RingSurfaceBuildSnapshot prepared = build.resolveDetailedHeightFingerprint(() -> {
            scans.incrementAndGet();
            return 7L;
        });

        assertSame(build, prepared);
        assertEquals(RingSurfaceBuildSnapshot.NO_DETAILED_HEIGHT_FINGERPRINT,
                prepared.heightFingerprint());
        assertEquals(0, scans.get());
        assertThrows(IllegalArgumentException.class,
                () -> new RingSurfaceBuildSnapshot(partial.snapshot(), 4, 7L));
    }

    @Test
    void colorOnlyChangesDoNotInvalidateTheHeightMeshFingerprint() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        RingTerrainAtlas atlas = completeAtlas(geometry, 71);
        long before = new RingSurfaceBuildSnapshot(atlas.snapshot(), 4)
                .resolveDetailedHeightFingerprint().heightFingerprint();

        atlas.putCell(0, 0, 71, 0x112233);

        long after = new RingSurfaceBuildSnapshot(atlas.snapshot(), 5)
                .resolveDetailedHeightFingerprint().heightFingerprint();
        assertEquals(before, after);
    }

    @Test
    void rejectsACompletedTextureWhenLiveIdentityOrRevisionHasAdvanced() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        RingSurfaceBuildSnapshot build = new RingSurfaceBuildSnapshot(
                completeAtlas(geometry, 71).snapshot(), 4);

        assertFalse(build.matches(geometry, HASH, 5));
        assertFalse(build.matches(geometry, HASH + 1, 4));
        assertFalse(build.matches(new RingGeometry(256, 4_096), HASH, 4));
    }

    private static RingTerrainAtlas completeAtlas(RingGeometry geometry, int height) {
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, HASH);
        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                atlas.putCell(column, row, height, 0x336699);
            }
        }
        return atlas;
    }
}
