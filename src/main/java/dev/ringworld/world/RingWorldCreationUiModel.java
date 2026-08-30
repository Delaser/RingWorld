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
    public static final Preset SMALL = new Preset(
            "Small", 2_048, 128, 160,
            "Strong curvature; monuments unavailable.");
    public static final Preset MEDIUM = new Preset(
            "Medium", RingWorldSettings.DEFAULT_CIRCUMFERENCE,
            RingWorldSettings.DEFAULT_WIDTH, RingWorldSettings.DEFAULT_WALL_HEIGHT,
            "Balanced default.");
    public static final Preset LARGE = new Preset(
            "Large", 32_768, 512, 160,
            "Long lap; slower generation.");
    public static final String NEXT_NEW_WORLD_COPY =
            "Applies to the next new world only. Layout locks on first load.";
    public static final List<String> NEXT_NEW_WORLD_LINES = List.of(
            "Next new world only.",
            "Layout locks on first load.");
    public static final String MONUMENT_COPY =
            "One monument search runs on first load; its result is saved.";
    public static final List<String> MONUMENT_LINES = List.of(
            "Monument search runs once.",
            "The result is saved.");

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

        /** Deterministic live layout metrics appropriate for a compact client screen. */
        public List<String> metricLines() {
            return report == null ? List.of() : metricLines(report);
        }

        /** Compatibility alias for callers not yet migrated to {@link #metricLines()}. */
        public List<String> summaryLines() {
            return metricLines();
        }

        /** Compatibility alias for callers not yet migrated to {@link #metricLines(RingDimensionReport)}. */
        public static List<String> summaryLines(RingDimensionReport report) {
            return metricLines(report);
        }

        /** Deterministic live layout metrics for a report already retained by a client screen. */
        public static List<String> metricLines(RingDimensionReport report) {
            if (report == null) return List.of();
            RingGeometry geometry = report.geometry();
            String costLabel = report.hasHighGenerationCost() ? "High cost" : "Pregen";
            List<String> metrics = new ArrayList<>(List.of(
                    String.format(Locale.ROOT, "Lap: %,d÷%.3f = %s",
                            geometry.circumferenceBlocks(),
                            RingDimensionReport.NORMAL_WALKING_SPEED_BLOCKS_PER_SECOND,
                            formatDuration(Math.round(report.normalWalkingLapSeconds()))),
                    String.format(Locale.ROOT, "Radius: %,d÷2π ≈ %,.1f; D ≈ %,.1f",
                            geometry.circumferenceBlocks(), geometry.radius(),
                            report.diameterBlocks()),
                    String.format(Locale.ROOT, "Opposite width: 2atan(%,d÷%,d) = %.2f°",
                            geometry.widthBlocks(), Math.round(report.diameterBlocks() * 2.0),
                            report.oppositeAngularWidthDegrees()),
                    String.format(Locale.ROOT, "Chunks: %,d×%,d = %,d",
                            geometry.circumferenceChunks(), geometry.widthChunks(),
                            report.canonicalChunkCount()),
                    String.format(Locale.ROOT, "Playable: %,d×%,d = %,d; rims %d",
                            geometry.circumferenceBlocks(), report.playableInteriorBlocks(),
                            report.playableInteriorAreaBlocks(), report.rimThicknessBlocks()),
                    String.format(Locale.ROOT, "Atlas: %,d×%,d = %,d; %s",
                            report.atlasColumns(), report.atlasRows(), report.atlasCellCount(),
                            formatDataSize(report.estimatedAtlasBytes())),
                    String.format(Locale.ROOT, "Heights: rim top Y%,d; clouds Y%,d",
                            report.wallTopYExclusive() - 1, report.cloudBaseY()),
                    String.format(Locale.ROOT, "%s: %,d÷%,d×%,ds = %s; disk %s",
                            costLabel,
                            report.canonicalChunkCount(),
                            RingDimensionCostEstimate.REFERENCE_CANONICAL_CHUNKS,
                            RingDimensionCostEstimate.REFERENCE_PREGENERATION_SECONDS,
                            formatDuration(report.costEstimate().estimatedPregenerationSeconds()),
                            formatDataSize(report.costEstimate().estimatedGeneratedWorldBytes()))));
            if (!monumentAvailable(geometry)) {
                metrics.add("Small is experimental: portal may need mining");
            }
            return List.copyOf(metrics);
        }

        /** One concise warning suitable for the compact screen's final line. */
        public static String compactWarning(RingDimensionReport report) {
            return report == null || report.warnings().isEmpty() ? "" : report.warnings().getFirst();
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
        return validate(circumferenceText, widthText, wallHeightText, RingWallStyle.DEFAULT);
    }

    public static Validation validate(String circumferenceText, String widthText,
                                      String wallHeightText, RingWallStyle wallStyle) {
        if (wallStyle == null) throw new IllegalArgumentException("wall style is required");
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
                new RingGeometry(width, circumference), wallHeight,
                wallStyle.thicknessBlocks());
        return new Validation(report, List.of());
    }

    public static String monumentChoice(boolean requested) {
        return requested ? "Monument: On" : "Monument: Off";
    }

    public static String monumentChoice(boolean requested, RingGeometry geometry) {
        return monumentAvailable(geometry) ? monumentChoice(requested) : "Monument: unavailable";
    }

    /** Concise geometry-specific monument state for the creation screen. */
    public static String monumentAvailabilityCopy(RingGeometry geometry) {
        return RingMonumentPlacement.hasCandidateSpace(geometry)
                ? "Monument: available. " + MONUMENT_COPY
                : "Monument unavailable: this band is too narrow for 64-block rim clearance.";
    }

    public static boolean monumentAvailable(RingGeometry geometry) {
        return RingMonumentPlacement.hasCandidateSpace(geometry);
    }

    public static String confirmationCopy(RingDimensionReport report, boolean requestOceanMonument) {
        return confirmationCopy(report, requestOceanMonument, RingWallStyle.DEFAULT);
    }

    public static String confirmationCopy(RingDimensionReport report, boolean requestOceanMonument,
                                          RingWallStyle wallStyle) {
        return confirmationCopy(report, requestOceanMonument, wallStyle, RingSkyProfile.DEFAULT);
    }

    public static String confirmationCopy(RingDimensionReport report, boolean requestOceanMonument,
                                          RingWallStyle wallStyle, RingSkyProfile skyProfile) {
        if (report == null || !report.isValid()) {
            throw new IllegalArgumentException("a valid RingWorld layout is required for confirmation");
        }
        return String.format(Locale.ROOT,
                "New world: %,d × %,d; %s rims, %d thick; %s sky; %s. Layout locks on first load.",
                report.geometry().circumferenceBlocks(), report.geometry().widthBlocks(),
                RingWallStyle.Preset.matching(wallStyle).label(), wallStyle.thicknessBlocks(),
                RingSkyProfile.Preset.matching(skyProfile).label(),
                monumentChoice(requestOceanMonument, report.geometry()));
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
        if (circumference < RingWorldSettings.MIN_NEW_WORLD_CIRCUMFERENCE) {
            errors.add("Circumference must be at least "
                    + RingWorldSettings.MIN_NEW_WORLD_CIRCUMFERENCE + " blocks for a new world.");
        }
        if (circumference % 16 != 0) {
            errors.add("Circumference must be a multiple of 16 blocks.");
        }
    }

    private static String formatDataSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        return hours > 0L ? "%dh %02dm".formatted(hours, minutes)
                : "%dm %02ds".formatted(minutes, seconds % 60L);
    }
}
