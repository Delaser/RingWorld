package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtlasPregenerationReportTest {
    @Test
    void terminalHeadlessReportRetainsOnlyPortableEvidence() {
        assertDoesNotThrow(() -> new AtlasPregenerationReport(2, AtlasPregenerationReportStatus.COMPLETE, true,
                1L, 2L, RingTerrainNoiseMapping.ANNULAR, 3L, 3L, 12, 12,
                Duration.ofSeconds(2), Optional.of(Path.of("atlas.rwat.gz")), Optional.empty()));
        assertDoesNotThrow(() -> new AtlasPregenerationReport(2, AtlasPregenerationReportStatus.COMPLETE, true,
                1L, 2L, RingTerrainNoiseMapping.LEGACY_AXIAL, 3L, 3L, 12, 12,
                Duration.ofSeconds(2), Optional.of(Path.of("atlas.rwat.gz")), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationReport(
                2, AtlasPregenerationReportStatus.FAILED, true, 1L, 2L,
                RingTerrainNoiseMapping.ANNULAR, 0L, 3L, 0, 12,
                Duration.ZERO, Optional.of(Path.of("atlas.rwat.gz")), Optional.empty()));
        assertDoesNotThrow(() -> new AtlasPregenerationReport(2, AtlasPregenerationReportStatus.REJECTED, false,
                0L, 0L, 0, 0L, 0L, 0, 0, Duration.ZERO, Optional.empty(),
                Optional.of("missing settings")));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationReport(2,
                AtlasPregenerationReportStatus.REJECTED, false, 0L, 0L,
                RingTerrainNoiseMapping.ANNULAR, 0L, 0L, 0, 0, Duration.ZERO,
                Optional.empty(), Optional.of("missing settings")));
    }

    @Test
    void completeReportsRequireVerifiedCompleteIdentityAndEveryOtherStatusRequiresReason() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationReport(2,
                AtlasPregenerationReportStatus.COMPLETE, true, 1L, 2L,
                RingTerrainNoiseMapping.ANNULAR, 2L, 3L, 12, 12,
                Duration.ZERO, Optional.of(Path.of("atlas.rwat.gz")), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationReport(2,
                AtlasPregenerationReportStatus.COMPLETE, false, 0L, 0L, 0, 0L, 0L, 0, 0,
                Duration.ZERO, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationReport(2,
                AtlasPregenerationReportStatus.INTERRUPTED, true, 1L, 2L,
                RingTerrainNoiseMapping.ANNULAR, 1L, 3L, 4, 12,
                Duration.ZERO, Optional.of(Path.of("atlas.rwat.gz")), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPregenerationReport(2,
                AtlasPregenerationReportStatus.FAILED, true, 1L, 2L, 99, 0L, 3L, 0, 12,
                Duration.ZERO, Optional.of(Path.of("atlas.rwat.gz")), Optional.of("failed")));
    }
}
