package dev.ringworld.world;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** An immutable, display-safe snapshot of an atlas pregeneration job. */
public record AtlasPregenerationProgress(
        AtlasPregenerationState state,
        long completedChunks,
        long totalChunks,
        int presentCells,
        int totalCells,
        double cellsPerSecond,
        Duration elapsed,
        Optional<Duration> eta,
        Optional<String> lastError) {
    public AtlasPregenerationProgress {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(elapsed, "elapsed");
        eta = Objects.requireNonNull(eta, "eta");
        lastError = Objects.requireNonNull(lastError, "lastError");
        if (totalChunks < 0 || completedChunks < 0 || completedChunks > totalChunks) {
            throw new IllegalArgumentException("completed chunks must be in [0, totalChunks]");
        }
        if (totalCells < 0 || presentCells < 0 || presentCells > totalCells) {
            throw new IllegalArgumentException("present cells must be in [0, totalCells]");
        }
        if (!Double.isFinite(cellsPerSecond) || cellsPerSecond < 0.0) {
            throw new IllegalArgumentException("cellsPerSecond must be finite and non-negative");
        }
        if (elapsed.isNegative()) throw new IllegalArgumentException("elapsed must not be negative");
        eta.ifPresent(value -> {
            if (value.isNegative()) throw new IllegalArgumentException("eta must not be negative");
        });
    }

    /**
     * Creates a rate and ETA snapshot without producing an invalid infinity
     * before the first cell has been captured or after a restarted job. The
     * service supplies {@code startingPresentCells} once when it starts or
     * resumes a job, so only cells captured during this run affect the rate.
     */
    public static AtlasPregenerationProgress snapshot(AtlasPregenerationState state,
                                                       long completedChunks, long totalChunks,
                                                       int startingPresentCells,
                                                       int presentCells, int totalCells,
                                                       Duration elapsed,
                                                       Optional<String> lastError) {
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(lastError, "lastError");
        if (startingPresentCells < 0 || startingPresentCells > presentCells
                || presentCells > totalCells) {
            throw new IllegalArgumentException(
                    "starting present cells must be in [0, presentCells] within totalCells");
        }
        double seconds = elapsed.toNanos() / 1_000_000_000.0;
        double rate = seconds > 0.0 ? (presentCells - startingPresentCells) / seconds : 0.0;
        Optional<Duration> eta = estimateEta(presentCells, totalCells, rate);
        return new AtlasPregenerationProgress(state, completedChunks, totalChunks,
                presentCells, totalCells, rate, elapsed, eta, lastError);
    }

    public static Optional<Duration> estimateEta(int presentCells, int totalCells,
                                                  double cellsPerSecond) {
        if (totalCells < 0 || presentCells < 0 || presentCells > totalCells) {
            throw new IllegalArgumentException("present cells must be in [0, totalCells]");
        }
        if (!Double.isFinite(cellsPerSecond) || cellsPerSecond < 0.0) {
            throw new IllegalArgumentException("cellsPerSecond must be finite and non-negative");
        }
        if (presentCells == totalCells) return Optional.of(Duration.ZERO);
        if (cellsPerSecond == 0.0) return Optional.empty();
        double seconds = (totalCells - (double)presentCells) / cellsPerSecond;
        if (seconds >= Long.MAX_VALUE / 1_000_000_000.0) return Optional.empty();
        return Optional.of(Duration.ofNanos((long)Math.ceil(seconds * 1_000_000_000.0)));
    }
}
