package dev.ringworld.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, transport-safe description of one dimension-owned atlas job.
 * Platform networking adapters encode this record without exposing platform
 * player or world types to the shared model.
 */
public record AtlasPregenerationStatus(
        long worldHash,
        int circumferenceBlocks,
        int widthBlocks,
        int atlasFormat,
        int sampleStep,
        long canonicalChunks,
        long completedCanonicalChunks,
        AtlasPregenerationProgress progress,
        boolean canControl,
        Optional<String> message) {
    public AtlasPregenerationStatus {
        if (circumferenceBlocks <= 0 || widthBlocks <= 0 || atlasFormat <= 0 || sampleStep <= 0) {
            throw new IllegalArgumentException("atlas identity dimensions and formats must be positive");
        }
        if (canonicalChunks < 0 || completedCanonicalChunks < 0 || completedCanonicalChunks > canonicalChunks) {
            throw new IllegalArgumentException("completed canonical chunks must be in [0, canonicalChunks]");
        }
        Objects.requireNonNull(progress, "progress");
        if (progress.totalChunks() != canonicalChunks) {
            throw new IllegalArgumentException("status canonical total must match progress total");
        }
        message = Objects.requireNonNull(message, "message").map(String::strip).filter(value -> !value.isEmpty());
    }

    public boolean matchesWorld(long expectedWorldHash) { return worldHash == expectedWorldHash; }
}
