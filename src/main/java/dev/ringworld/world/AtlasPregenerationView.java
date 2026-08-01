package dev.ringworld.world;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Pure text and control policy for the player-facing atlas screen. */
public record AtlasPregenerationView(
        String dimensions,
        String chunks,
        String cells,
        String elapsed,
        String rate,
        String eta,
        String state,
        String error,
        Set<AtlasPregenerationAction> actions) {
    public static AtlasPregenerationView from(AtlasPregenerationStatus status) {
        AtlasPregenerationProgress progress = status.progress();
        double percent = progress.totalCells() == 0 ? 100.0
                : progress.presentCells() * 100.0 / progress.totalCells();
        EnumSet<AtlasPregenerationAction> actions = EnumSet.noneOf(AtlasPregenerationAction.class);
        if (status.canControl()) switch (progress.state()) {
            case IDLE, CANCELLED, FAILED -> actions.add(AtlasPregenerationAction.START);
            case RUNNING -> {
                actions.add(AtlasPregenerationAction.PAUSE);
                actions.add(AtlasPregenerationAction.CANCEL);
            }
            case PAUSED -> {
                actions.add(AtlasPregenerationAction.RESUME);
                actions.add(AtlasPregenerationAction.CANCEL);
            }
            case SAVING, COMPLETE -> { }
        }
        return new AtlasPregenerationView(
                String.format(Locale.ROOT, "%d × %d blocks", status.circumferenceBlocks(), status.widthBlocks()),
                String.format(Locale.ROOT, "%,d / %,d canonical chunks", status.completedCanonicalChunks(), status.canonicalChunks()),
                String.format(Locale.ROOT, "%,d / %,d cells (%.1f%%)", progress.presentCells(), progress.totalCells(), percent),
                format(progress.elapsed()),
                progress.cellsPerSecond() > 0.01
                        ? String.format(Locale.ROOT, "%.1f cells/s", progress.cellsPerSecond()) : "Calculating…",
                progress.eta().map(AtlasPregenerationView::format).orElse("Unknown"),
                title(progress.state()),
                progress.lastError().or(() -> status.message()).orElse(""),
                Set.copyOf(actions));
    }

    private static String title(AtlasPregenerationState state) {
        return switch (state) {
            case IDLE -> "Ready";
            case RUNNING -> "Generating";
            case PAUSED -> "Paused";
            case SAVING -> "Saving";
            case COMPLETE -> "Complete";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
        };
    }

    public static String format(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remaining = seconds % 60L;
        if (hours > 0) return "%dh %02dm".formatted(hours, minutes);
        if (minutes > 0) return "%dm %02ds".formatted(minutes, remaining);
        return remaining + "s";
    }
}
