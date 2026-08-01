package dev.ringworld.world;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Verified completion data supplied after a durable atlas save. */
public record AtlasPregenerationResult(
        long worldHash,
        long completedChunks,
        int completedCells,
        Duration elapsed,
        Path atlasPath) {
    public AtlasPregenerationResult {
        if (completedChunks < 0) throw new IllegalArgumentException("completedChunks must not be negative");
        if (completedCells < 0) throw new IllegalArgumentException("completedCells must not be negative");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(atlasPath, "atlasPath");
        if (elapsed.isNegative()) throw new IllegalArgumentException("elapsed must not be negative");
    }
}
