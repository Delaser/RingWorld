package dev.ringworld.world;

/**
 * One dimension-aware visual/resource profile shared by Java renderers and
 * shader globals. Distances are intrinsic horizontal blocks.
 */
public record RingRenderProfile(
        int visualProfileVersion,
        double requestedViewDistanceBlocks,
        double effectiveViewDistanceBlocks,
        double halfCircumferenceBlocks,
        double liveFadeStartBlocks,
        double liveFadeEndBlocks,
        double proxyFadeStartBlocks,
        double proxyFadeEndBlocks,
        double detailStartBlocks,
        double detailEndBlocks,
        double revealNear,
        double revealFar,
        double hazeNear,
        double hazeFar,
        double hazeExponent,
        double cloudFadeStartBlocks,
        double cloudFadeEndBlocks,
        int textureColumns,
        int textureRows,
        int circumferenceSegments,
        int widthBands,
        long vertexCount,
        double textureBlocksPerTexelX,
        double textureBlocksPerTexelZ) {

    /**
     * Increment when visual-policy semantics change enough that comparison
     * captures need to identify a different profile.
     */
    public static final int VISUAL_PROFILE_VERSION = 5;
    public static final double LIVE_FADE_START_FACTOR = 0.78;
    public static final double LIVE_FADE_END_FACTOR = 1.02;
    public static final double PROXY_FADE_START_FACTOR = 0.68;
    public static final double PROXY_FADE_END_FACTOR = 0.98;
    public static final double DETAIL_START_FACTOR = 0.76;
    public static final double DETAIL_END_FACTOR = 1.25;
    public static final int MAX_TEXTURE_COLUMNS = 4_096;
    public static final int MAX_TEXTURE_ROWS = 1_024;
    /**
     * Retains the atlas's eight-block height sampling around the default
     * 16,384-block ring. The former 512-segment cap stretched each production
     * triangle across 32 blocks and exposed a visibly faceted proxy at short
     * render distances.
     */
    public static final int MAX_CIRCUMFERENCE_SEGMENTS = 2_048;
    public static final int MAX_WIDTH_BANDS = 128;
    public static final int TARGET_MESH_STEP_BLOCKS = RingTerrainAtlas.SAMPLE_STEP_BLOCKS;
    public static final int POSITION_TEXTURE_COLOR_VERTEX_BYTES = 24;
    public static final double REVEAL_NEAR = 0.52;
    public static final double REVEAL_FAR = 0.98;
    public static final double HAZE_NEAR = 0.04;
    public static final double HAZE_FAR = 0.16;
    public static final double HAZE_EXPONENT = 1.35;

    public static RingRenderProfile create(RingGeometry geometry, double viewDistanceBlocks) {
        if (!Double.isFinite(viewDistanceBlocks) || viewDistanceBlocks <= 0.0) {
            throw new IllegalArgumentException("view distance must be finite and positive");
        }

        double half = geometry.circumferenceBlocks() * 0.5;
        double effective = Math.min(Math.max(16.0, viewDistanceBlocks), half);
        double liveStart = effective * LIVE_FADE_START_FACTOR;
        double liveEnd = Math.min(half,
                Math.max(liveStart + 8.0, effective * LIVE_FADE_END_FACTOR));
        double proxyStart = effective * PROXY_FADE_START_FACTOR;
        double proxyEnd = Math.min(half,
                Math.max(proxyStart + 16.0, effective * PROXY_FADE_END_FACTOR));
        double detailStart = effective * DETAIL_START_FACTOR;
        double detailEnd = Math.min(half,
                Math.max(detailStart + 16.0, effective * DETAIL_END_FACTOR));
        double cloudEnd = Math.min(effective * 0.82,
                geometry.circumferenceBlocks() * 0.12);
        double cloudStart = Math.min(cloudEnd, Math.max(8.0, cloudEnd * 0.55));

        int textureColumns = Math.min(geometry.circumferenceBlocks(), MAX_TEXTURE_COLUMNS);
        int textureRows = Math.min(geometry.widthBlocks(), MAX_TEXTURE_ROWS);
        int circumferenceSegments = Math.min(
                divideCeil(geometry.circumferenceBlocks(), TARGET_MESH_STEP_BLOCKS),
                MAX_CIRCUMFERENCE_SEGMENTS);
        int widthBands = Math.min(
                divideCeil(geometry.widthBlocks(), TARGET_MESH_STEP_BLOCKS),
                MAX_WIDTH_BANDS);
        long vertices = Math.multiplyExact(
                Math.multiplyExact((long)circumferenceSegments, widthBands), 6L);

        return new RingRenderProfile(
                VISUAL_PROFILE_VERSION,
                viewDistanceBlocks, effective, half,
                liveStart, liveEnd, proxyStart, proxyEnd, detailStart, detailEnd,
                REVEAL_NEAR, REVEAL_FAR,
                HAZE_NEAR, HAZE_FAR, HAZE_EXPONENT,
                cloudStart, cloudEnd,
                textureColumns, textureRows, circumferenceSegments, widthBands, vertices,
                (double)geometry.circumferenceBlocks() / textureColumns,
                (double)geometry.widthBlocks() / textureRows);
    }

    public boolean wholeRingViewRequested() {
        return requestedViewDistanceBlocks >= halfCircumferenceBlocks;
    }

    /** RGBA8 base texture plus the renderer's complete filtered mip chain. */
    public long estimatedGpuTextureBytes() {
        int width = textureColumns;
        int height = textureRows;
        long texels = 0L;
        while (true) {
            texels = Math.addExact(texels, Math.multiplyExact((long)width, height));
            if (Math.min(width, height) <= 1) break;
            width = Math.max(1, width >> 1);
            height = Math.max(1, height >> 1);
        }
        return Math.multiplyExact(texels, 4L);
    }

    public long estimatedGpuMeshBytes() {
        return Math.multiplyExact(vertexCount, POSITION_TEXTURE_COLOR_VERTEX_BYTES);
    }

    /**
     * Conservative peak for expanded ARGB pixels, float heights, and one
     * NativeImage upload at base resolution.
     */
    public long estimatedTextureBuildScratchBytes() {
        long texels = Math.multiplyExact((long)textureColumns, textureRows);
        return Math.multiplyExact(texels, 12L);
    }

    private static int divideCeil(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
