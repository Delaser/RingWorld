package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loader-neutral presentation model for the immutable layout selected before
 * a RingWorld Overworld is first created.
 *
 * <p>This class deliberately has no configuration or Minecraft client
 * dependencies. The creation screen owns widget state and calls
 * {@link RingWorldConfig#saveBootstrapLayout(int, int, int, boolean)} only
 * after this model has accepted the values.</p>
 */
public final class RingWorldCreationUiModel {
    public static final Preset SAFE_SMALL_TEST = new Preset(
            "Safe-small test", 2_048, 416, 160,
            "A quick, safe layout for local testing and screenshots.");
    public static final Preset PRODUCTION_RECOMMENDED = new Preset(
            "Production (recommended)", RingWorldSettings.DEFAULT_CIRCUMFERENCE,
            RingWorldSettings.DEFAULT_WIDTH, RingWorldSettings.DEFAULT_WALL_HEIGHT,
            "The recommended full-size layout for a normal RingWorld playthrough.");
    public static final String NEXT_NEW_WORLD_COPY =
            "These values apply only to the next new RingWorld Overworld. When that Overworld first loads, "
                    + "its dimensions and monument choice are saved with it and cannot be changed here. "
                    + "Existing worlds are never changed.";
    public static final List<String> NEXT_NEW_WORLD_LINES = List.of(
            "Only the next new RingWorld Overworld uses these values.",
            "First load saves dimensions and monument choice permanently.");
    public static final String MONUMENT_COPY =
            "When enabled, RingWorld searches for one valid ocean-monument location as the new world first "
                    + "loads and saves the result. Unusual seeds can report that no valid location was available.";
    public static final List<String> MONUMENT_LINES = List.of(
            "Ocean monument: search for a valid location at first load.",
            "Result is saved; unusual seeds may have no valid location.");

    private RingWorldCreationUiModel() { }

    public record Preset(String label, int circumferenceBlocks, int widthBlocks,
                         int wallHeightBlocks, String description) {
        public Preset {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("preset label is required");
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("preset description is required");
            }
        }
    }

    /** Parsed and validated state for the three editable layout fields. */
    public record Validation(RingDimensionReport report, List<String> errors) {
        public Validation {
            errors = List.copyOf(errors);
            if (!errors.isEmpty() && report != null && report.isValid()) {
                throw new IllegalArgumentException("a valid report cannot have input errors");
            }
        }

        public boolean canApply() {
            return report != null && report.isValid() && errors.isEmpty();
        }

        /** Plain-language preview appropriate for a compact client screen. */
        public List<String> summaryLines() {
            return report == null ? List.of() : summaryLines(report);
        }

        /** Plain-language preview for a report already retained by a client screen. */
        public static List<String> summaryLines(RingDimensionReport report) {
            if (report == null) return List.of();
            RingGeometry geometry = report.geometry();
            return List.of(
                    String.format(Locale.ROOT, "Ring size: %,d blocks around and %,d blocks wide.",
                            geometry.circumferenceBlocks(), geometry.widthBlocks()),
                    String.format(Locale.ROOT, "Curved horizon radius: %,d blocks; wall height: %,d blocks.",
                            Math.round(geometry.radius()), report.wallHeightBlocks()),
                    String.format(Locale.ROOT, "Entire-ring atlas: %,d cells (about %s MiB).",
                            report.atlasCellCount(), formatMiB(report.estimatedAtlasBytes())),
                    String.format(Locale.ROOT, "Estimated complete generation: %s; generated data: %s MiB.",
                            formatDuration(report.costEstimate().estimatedPregenerationSeconds()),
                            formatMiB(report.costEstimate().estimatedGeneratedWorldBytes())));
        }

        /** User-facing report errors, retained alongside field parsing errors. */
        public List<String> messages() {
            List<String> messages = new ArrayList<>(errors);
            if (report != null) messages.addAll(report.errors());
            return List.copyOf(messages);
        }
    }

    /**
     * Parses every field before constructing the geometry so users can correct
     * all malformed fields at once instead of resolving one exception at a time.
     */
    public static Validation validate(String circumferenceText, String widthText, String wallHeightText) {
        List<String> errors = new ArrayList<>();
        Integer circumference = parseWholeBlocks("Circumference", circumferenceText, errors);
        Integer width = parseWholeBlocks("Width", widthText, errors);
        Integer wallHeight = parseWholeBlocks("Wall height", wallHeightText, errors);

        if (circumference != null) validateCircumference(circumference, errors);
        if (width != null) validateWidth(width, errors);
        if (wallHeight != null && wallHeight < RingDimensionReport.MIN_WALL_HEIGHT_BLOCKS) {
            errors.add("Wall height must be at least "
                    + RingDimensionReport.MIN_WALL_HEIGHT_BLOCKS + " blocks.");
        }
        if (!errors.isEmpty()) return new Validation(null, errors);

        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(width, circumference), wallHeight);
        return new Validation(report, List.of());
    }

    public static String monumentChoice(boolean requested) {
        return requested ? "Ocean monument guarantee: On" : "Ocean monument guarantee: Off";
    }

    public static String confirmationCopy(RingDimensionReport report, boolean requestOceanMonument) {
        if (report == null || !report.isValid()) {
            throw new IllegalArgumentException("a valid RingWorld layout is required for confirmation");
        }
        return String.format(Locale.ROOT,
                "The next new Overworld will use %,d blocks around, %,d blocks wide, and %,d-block rim walls. ",
                report.geometry().circumferenceBlocks(), report.geometry().widthBlocks(),
                report.wallHeightBlocks())
                + monumentChoice(requestOceanMonument) + ". " + NEXT_NEW_WORLD_COPY;
    }

    private static Integer parseWholeBlocks(String field, String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required. Enter a whole number of blocks.");
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            errors.add(field + " must be a whole number of blocks.");
            return null;
        }
    }

    private static void validateWidth(int width, List<String> errors) {
        if (width < RingWorldSettings.MIN_WIDTH) {
            errors.add("Width must be at least " + RingWorldSettings.MIN_WIDTH + " blocks.");
        }
        if (width % 16 != 0) errors.add("Width must be a multiple of 16 blocks.");
    }

    private static void validateCircumference(int circumference, List<String> errors) {
        if (circumference < RingWorldSettings.MIN_CIRCUMFERENCE) {
            errors.add("Circumference must be at least " + RingWorldSettings.MIN_CIRCUMFERENCE + " blocks.");
        }
        if (circumference % 16 != 0) {
            errors.add("Circumference must be a multiple of 16 blocks.");
        }
    }

    private static String formatMiB(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        return hours > 0L ? "%dh %02dm".formatted(hours, minutes)
                : "%dm %02ds".formatted(minutes, seconds % 60L);
    }
}
