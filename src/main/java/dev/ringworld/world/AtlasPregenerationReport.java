package dev.ringworld.world;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable, loader-neutral terminal evidence for a headless atlas run. */
public record AtlasPregenerationReport(
        int schemaVersion,
        AtlasPregenerationReportStatus status,
        boolean identityAvailable,
        long worldHash,
        long layoutFingerprint,
        long completedChunks,
        long totalChunks,
        int completedCells,
        int totalCells,
        Duration elapsed,
        Optional<Path> atlasPath,
        Optional<String> failureReason) {
    public AtlasPregenerationReport {
        if (schemaVersion != 1) throw new IllegalArgumentException("unsupported report schema version: " + schemaVersion);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(elapsed, "elapsed");
        atlasPath = Objects.requireNonNull(atlasPath, "atlasPath");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        if (completedChunks < 0 || totalChunks < 0 || completedChunks > totalChunks
                || completedCells < 0 || totalCells < 0 || completedCells > totalCells
                || elapsed.isNegative()) {
            throw new IllegalArgumentException("invalid atlas pregeneration report totals");
        }
        if ((status == AtlasPregenerationReportStatus.FAILED || status == AtlasPregenerationReportStatus.REJECTED)
                && failureReason.isEmpty()) {
            throw new IllegalArgumentException("failed or rejected reports require a reason");
        }
        if (!identityAvailable && (worldHash != 0L || layoutFingerprint != 0L
                || completedChunks != 0L || totalChunks != 0L || completedCells != 0
                || totalCells != 0 || atlasPath.isPresent())) {
            throw new IllegalArgumentException("unavailable atlas identity must use documented zero/null sentinels");
        }
    }
}
