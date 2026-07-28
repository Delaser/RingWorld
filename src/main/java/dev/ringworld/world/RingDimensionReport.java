package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, allocation-safe validation and cost report for a prospective
 * RingWorld layout.
 *
 * <p>{@link RingGeometry} accepts structurally useful small fixtures for pure
 * topology and atlas tests. This report defines whether that geometry is safe
 * to use as a complete playable Overworld.</p>
 */
public record RingDimensionReport(
        RingGeometry geometry,
        int worldBottomY,
        int worldTopYExclusive,
        int wallHeightBlocks,
        int wallTopYExclusive,
        int cloudBaseY,
        double radialClearanceAtHighestPlane,
        double oppositeAngularWidthDegrees,
        long canonicalChunkCount,
        long atlasCellCount,
        long estimatedAtlasBytes,
        long estimatedNoiseCoordinateBytes,
        List<String> errors,
        List<String> warnings) {

    /** Vanilla 1.21.11 Overworld bounds. Revalidate against the live world too. */
    public static final int VANILLA_OVERWORLD_BOTTOM_Y = -64;
    public static final int VANILLA_OVERWORLD_TOP_Y_EXCLUSIVE = 320;
    public static final int MIN_WALL_HEIGHT_BLOCKS = 32;
    public static final int MIN_RADIAL_CLEARANCE_BLOCKS = 64;
    public static final int CLOUD_CLEARANCE_BLOCKS = 8;
    public static final int MIN_PLAYABLE_INTERIOR_BLOCKS = 64;

    /**
     * Technical envelope for the current integer noise cache and float shader
     * phase. The product budget below normally becomes the tighter limit.
     */
    public static final int MAX_AXIS_BLOCKS = 1_048_576;
    public static final long WARN_CANONICAL_CHUNKS = 500_000L;
    public static final long WARN_ATLAS_CELLS = 4_000_000L;
    public static final long MAX_ATLAS_CELLS = 16_000_000L;

    public RingDimensionReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public static RingDimensionReport forVanillaOverworld(RingGeometry geometry,
                                                           int wallHeightBlocks) {
        return evaluate(geometry, wallHeightBlocks,
                VANILLA_OVERWORLD_BOTTOM_Y, VANILLA_OVERWORLD_TOP_Y_EXCLUSIVE,
                RingGenerationBoundary.RIM_THICKNESS,
                RingTerrainAtlas.SAMPLE_STEP_BLOCKS);
    }

    public static RingDimensionReport evaluate(RingGeometry geometry,
                                               int wallHeightBlocks,
                                               int worldBottomY,
                                               int worldTopYExclusive,
                                               int rimThicknessBlocks,
                                               int atlasSampleStepBlocks) {
        if (worldTopYExclusive <= worldBottomY) {
            throw new IllegalArgumentException("world top must be above world bottom");
        }
        if (rimThicknessBlocks <= 0) {
            throw new IllegalArgumentException("rim thickness must be positive");
        }
        if (atlasSampleStepBlocks <= 0 || 16 % atlasSampleStepBlocks != 0) {
            throw new IllegalArgumentException("atlas sample step must divide one chunk");
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int wallTopYExclusive;
        int cloudBaseY;
        try {
            wallTopYExclusive = Math.addExact(worldBottomY, wallHeightBlocks);
            cloudBaseY = Math.addExact(wallTopYExclusive, CLOUD_CLEARANCE_BLOCKS);
        } catch (ArithmeticException exception) {
            wallTopYExclusive = Integer.MAX_VALUE;
            cloudBaseY = Integer.MAX_VALUE;
            errors.add("wall/cloud height arithmetic exceeds the integer coordinate range");
        }

        if (wallHeightBlocks < MIN_WALL_HEIGHT_BLOCKS) {
            errors.add("wall height must be at least " + MIN_WALL_HEIGHT_BLOCKS + " blocks");
        }
        if (wallTopYExclusive > worldTopYExclusive) {
            errors.add("wall top Y=" + wallTopYExclusive
                    + " exceeds Overworld top Y=" + worldTopYExclusive);
        }
        if (cloudBaseY > worldTopYExclusive) {
            errors.add("cloud base Y=" + cloudBaseY
                    + " exceeds Overworld top Y=" + worldTopYExclusive);
        }

        int playableInterior = geometry.widthBlocks() - rimThicknessBlocks * 2;
        if (playableInterior < MIN_PLAYABLE_INTERIOR_BLOCKS) {
            errors.add("width leaves only " + playableInterior
                    + " interior blocks after both " + rimThicknessBlocks + "-block rims");
        }

        int highestPhysicalPlane = Math.max(worldTopYExclusive, cloudBaseY);
        double radialClearance = geometry.physicalRadiusAt(highestPhysicalPlane);
        int requiredCircumference = minimumCircumferenceBlocks(
                highestPhysicalPlane, RingGeometry.SURFACE_Y, MIN_RADIAL_CLEARANCE_BLOCKS);
        if (radialClearance < MIN_RADIAL_CLEARANCE_BLOCKS) {
            errors.add("circumference leaves only " + format(radialClearance)
                    + " radial blocks at Y=" + highestPhysicalPlane
                    + "; at least " + MIN_RADIAL_CLEARANCE_BLOCKS
                    + " are required (minimum aligned circumference "
                    + requiredCircumference + ")");
        }

        if (geometry.widthBlocks() > MAX_AXIS_BLOCKS) {
            errors.add("width exceeds the current " + MAX_AXIS_BLOCKS + "-block technical limit");
        }
        if (geometry.circumferenceBlocks() > MAX_AXIS_BLOCKS) {
            errors.add("circumference exceeds the current " + MAX_AXIS_BLOCKS
                    + "-block technical limit");
        }

        long chunks = Math.multiplyExact(
                (long)geometry.circumferenceChunks(), geometry.widthChunks());
        long atlasColumns = divideCeil(geometry.circumferenceBlocks(), atlasSampleStepBlocks);
        long atlasRows = divideCeil(geometry.widthBlocks(), atlasSampleStepBlocks);
        long atlasCells = Math.multiplyExact(atlasColumns, atlasRows);
        long atlasBytes = Math.multiplyExact(atlasCells, 7L);
        long noiseCoordinateBytes = Math.multiplyExact(
                (long)geometry.circumferenceBlocks(), 8L);
        if (atlasCells > MAX_ATLAS_CELLS) {
            errors.add("terrain atlas requires " + atlasCells + " cells; current limit is "
                    + MAX_ATLAS_CELLS);
        } else if (atlasCells > WARN_ATLAS_CELLS) {
            warnings.add("terrain atlas requires " + atlasCells
                    + " cells (approximately " + atlasBytes + " raw bytes)");
        }
        if (chunks > WARN_CANONICAL_CHUNKS) {
            warnings.add("complete-ring pregeneration requires " + chunks + " canonical chunks");
        }

        double angularWidth = geometry.oppositeAngularWidthDegrees(0.0);
        if (angularWidth < 10.0) {
            warnings.add("opposite ring width is only " + format(angularWidth)
                    + " degrees and may look unusually narrow");
        } else if (angularWidth > 120.0) {
            warnings.add("opposite ring width is " + format(angularWidth)
                    + " degrees and may dominate the sky");
        }

        return new RingDimensionReport(
                geometry, worldBottomY, worldTopYExclusive, wallHeightBlocks,
                wallTopYExclusive, cloudBaseY, radialClearance, angularWidth,
                chunks, atlasCells, atlasBytes, noiseCoordinateBytes, errors, warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void requireValid() {
        if (!isValid()) {
            throw new IllegalArgumentException("Invalid RingWorld dimensions: "
                    + String.join("; ", errors));
        }
    }

    public static int minimumCircumferenceBlocks(int highestPhysicalY,
                                                 double surfaceReferenceY,
                                                 double radialClearanceBlocks) {
        if (!(radialClearanceBlocks >= 0.0) || !Double.isFinite(radialClearanceBlocks)) {
            throw new IllegalArgumentException("radial clearance must be finite and non-negative");
        }
        double requiredRadius = highestPhysicalY - surfaceReferenceY + radialClearanceBlocks;
        if (!(requiredRadius > 0.0) || !Double.isFinite(requiredRadius)) {
            throw new IllegalArgumentException("required physical radius must be finite and positive");
        }
        double blocks = Math.ceil(Math.PI * 2.0 * requiredRadius);
        if (blocks > Integer.MAX_VALUE - 15.0) {
            throw new IllegalArgumentException("minimum circumference exceeds integer coordinates");
        }
        return Math.toIntExact(((long)blocks + 15L) / 16L * 16L);
    }

    private static long divideCeil(int value, int divisor) {
        return ((long)value + divisor - 1L) / divisor;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
