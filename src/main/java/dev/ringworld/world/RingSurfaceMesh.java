package dev.ringworld.world;

import java.util.Objects;

/**
 * Shared-vertex lattice for the atlas-backed complete-ring surface.
 *
 * <p>The GPU format is an unindexed triangle list, so boundary vertices are
 * repeated in the final buffer. They must nevertheless come from exactly one
 * sampled lattice vertex: independently sampling the two sides of a quad
 * boundary risks a visible crack when the final terrain-height mesh replaces
 * the progressive reference-height mesh. X remains periodic while Z has the
 * two finite width edges.</p>
 */
public final class RingSurfaceMesh {
    static final float MINIMUM_BRIDGE_TEXTURE_V = -1.0F;
    static final float MAXIMUM_BRIDGE_TEXTURE_V = 2.0F;
    static final float OUTER_BRIDGE_TEXTURE_V = -2.0F;
    static final float TOP_BRIDGE_TEXTURE_V = 3.0F;
    /** Small hidden overlap that prevents a projection/depth crack at each inner rim face. */
    static final double RIM_SURFACE_OVERLAP_BLOCKS = 0.5;

    private RingSurfaceMesh() { }

    /** Builds the bounded mesh lattice selected by the active render profile. */
    public static Mesh build(RingGeometry geometry, RingTerrainAtlas atlas,
                             boolean detailed, double referenceHeight) {
        return build(geometry, atlas, detailed, referenceHeight, referenceHeight, 1);
    }

    /** Builds the surface plus closed distant-rim geometry when the rim rises above it. */
    public static Mesh build(RingGeometry geometry, RingTerrainAtlas atlas,
                             boolean detailed, double referenceHeight,
                             double wallTopHeight, int rimThicknessBlocks) {
        return build(geometry, atlas, detailed, referenceHeight, wallTopHeight, rimThicknessBlocks,
                RingRenderProfile.create(geometry, 16.0, RingAtlasFidelity.forSampleStep(atlas.sampleStep())));
    }

    public static Mesh build(RingGeometry geometry, RingTerrainAtlas atlas,
                             boolean detailed, double referenceHeight,
                             double wallTopHeight, int rimThicknessBlocks, RingRenderProfile profile) {
        return build(geometry, atlas, detailed, referenceHeight, wallTopHeight,
                wallTopHeight > referenceHeight
                        ? (int)Math.ceil(wallTopHeight - RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y) : 0,
                RingWallStyle.custom(rimThicknessBlocks, RingWallStyle.LEGACY.palette(),
                        RingWallStyle.LEGACY.pattern(), 0), 0L, profile);
    }

    /** All wall inputs are captured with the Atlas snapshot before worker submission. */
    public static Mesh build(RingGeometry geometry, RingTerrainAtlas atlas,
                             boolean detailed, double referenceHeight, double wallTopHeight,
                             int wallHeightBlocks, RingWallStyle style, long seed, RingRenderProfile profile) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(atlas, "atlas");
        if (!geometry.equals(atlas.geometry())) {
            throw new IllegalArgumentException("atlas geometry does not match surface mesh geometry");
        }
        if (!Double.isFinite(referenceHeight)) {
            throw new IllegalArgumentException("reference height must be finite");
        }
        if (!Double.isFinite(wallTopHeight)) {
            throw new IllegalArgumentException("wall top height must be finite");
        }
        if (wallHeightBlocks < 0) throw new IllegalArgumentException("wall height must be non-negative");
        RingCloudBounds innerFaces = RingCloudBounds.betweenInnerRimFaces(
                geometry, style.thicknessBlocks());

        int segments = Math.min(atlas.columns(), profile.circumferenceSegments());
        int bands = Math.min(atlas.rows(), profile.widthBands());
        return new Mesh(geometry, atlas, detailed, referenceHeight, wallTopHeight,
                innerFaces, segments, bands, wallHeightBlocks, style, seed);
    }

    /** A consumer of the exact float vertex values written to the GPU buffer. */
    @FunctionalInterface
    public interface VertexConsumer {
        void vertex(float x, float y, float z, float u, float v);
        default void sideColor(int rgb) { }
    }

    /** Immutable, shared-vertex mesh data; triangles are emitted without indices. */
    public static final class Mesh {
        private final int segments;
        private final int bands;
        private final int columns;
        private final float[] positionsX;
        private final float[] positionsY;
        private final float[] positionsZ;
        private final float[] textureU;
        private final float[] textureV;
        private final float[] heights;
        private final float[] upperU;
        private final float[] upperV;
        private final int[] upperSideColor;
        private final double steepHeightThreshold;
        private final RingGeometry geometry;
        private final boolean bridgeRims;
        private final float[] minimumRimBottom;
        private final float[] maximumRimBottom;
        private final float wallBottomY;
        private final int capBands;
        private final float[][] wallTops;
        private final int[] edgeColors;
        private final float bridgeMinimumZ;
        private final float bridgeMaximumZ;
        private final float outerMinimumZ;
        private final float outerMaximumZ;

        private Mesh(RingGeometry geometry, RingTerrainAtlas atlas, boolean detailed,
                     double referenceHeight, double wallTopHeight,
                     RingCloudBounds innerFaces, int segments, int bands,
                     int wallHeightBlocks, RingWallStyle style, long seed) {
            this.geometry = geometry;
            this.segments = segments;
            this.bands = bands;
            // Rims are vertical structures, while the terrain Atlas stores one
            // exposed top sample per cell. Stretching the detailed terrain mesh
            // through those high rim samples produces a broad ramp once the
            // Atlas completes. Keep the style-derived closed rim at every Atlas
            // stage and terminate terrain at its inner faces instead.
            bridgeRims = wallHeightBlocks > 0;
            minimumRimBottom = new float[segments + 1];
            maximumRimBottom = new float[segments + 1];
            // The nominal top is the alignment plane. The saved height controls
            // downward extent; terrain elevation never changes that depth.
            this.wallBottomY = (float)(wallTopHeight - wallHeightBlocks);
            // A bounded cross-depth height field closes the broken crest. Decay
            // belongs to geometry rather than transparent holes in flat curtains.
            capBands = Math.min(4, style.thicknessBlocks());
            wallTops = new float[capBands + 1][segments + 1];
            for (int depthBand = 0; depthBand <= capBands; depthBand++) {
                int depth = Math.min(style.thicknessBlocks() - 1,
                        depthBand * style.thicknessBlocks() / capBands);
                for (int segment = 0; segment <= segments; segment++) {
                    int x = segment == segments ? 0
                            : (int)((long)segment * geometry.circumferenceBlocks() / segments);
                    wallTops[depthBand][segment] = Math.max(wallBottomY,
                            (float)wallTopHeight - RingWallPattern.topCollapseDepth(
                                    style, x, depth, geometry.circumferenceBlocks(), seed));
                }
            }
            bridgeMinimumZ = (float)innerFaces.minimumZ();
            bridgeMaximumZ = (float)innerFaces.maximumZ();
            outerMinimumZ = (float)geometry.minWidthZ();
            outerMaximumZ = (float)geometry.maxWidthZ() + 1.0F;
            this.columns = Math.addExact(segments, 1);
            int rows = Math.addExact(bands, 1);
            int vertices = Math.multiplyExact(columns, rows);
            this.positionsX = new float[vertices];
            this.positionsY = new float[vertices];
            this.positionsZ = new float[vertices];
            this.textureU = new float[vertices];
            this.textureV = new float[vertices];
            this.heights = new float[vertices];
            this.upperU = new float[vertices];
            this.upperV = new float[vertices];
            this.upperSideColor = new int[vertices];
            this.edgeColors = new int[vertices];
            java.util.Arrays.fill(upperSideColor, -1);
            this.steepHeightThreshold = Math.max(3.0, Math.max(
                    (double)geometry.circumferenceBlocks() / segments,
                    (innerFaces.maximumZ() - innerFaces.minimumZ()) / bands));

            for (int segment = 0; segment <= segments; segment++) {
                double canonicalX = (double)segment * geometry.circumferenceBlocks() / segments;
                // The final U remains one at the periodic seam, but its
                // physical position must be bit-identical to X=0. sin(2*pi)
                // is only approximately zero and used to leave a microscopic
                // open edge after float conversion.
                double angle = segment == segments ? 0.0
                        : Math.PI * 2.0 * canonicalX / geometry.circumferenceBlocks();
                float u = (float)(canonicalX / geometry.circumferenceBlocks());
                for (int band = 0; band <= bands; band++) {
                    double surfaceMinimumZ = bridgeRims
                            ? innerFaces.minimumZ() - RIM_SURFACE_OVERLAP_BLOCKS
                            : geometry.minWidthZ();
                    double surfaceMaximumZ = bridgeRims
                            ? innerFaces.maximumZ() + RIM_SURFACE_OVERLAP_BLOCKS
                            : geometry.maxWidthZ();
                    double z = surfaceMinimumZ
                            + (double)band * (surfaceMaximumZ - surfaceMinimumZ) / bands;
                    // Sample at least one Atlas cell inside the playable band.
                    // This prevents the first terrain vertex/texel from
                    // bilinearly inheriting the adjacent wall-top sample.
                    double sampleMinimumZ = bridgeRims
                            ? Math.min(innerFaces.maximumZ(),
                                    innerFaces.minimumZ() + atlas.sampleStep())
                            : surfaceMinimumZ;
                    double sampleMaximumZ = bridgeRims
                            ? Math.max(innerFaces.minimumZ(),
                                    innerFaces.maximumZ() - atlas.sampleStep())
                            : surfaceMaximumZ;
                    double sampleZ = Math.max(sampleMinimumZ, Math.min(sampleMaximumZ, z));
                    double surfaceHeight = detailed
                            ? atlas.sample(canonicalX, sampleZ).height()
                            : referenceHeight;
                    if (band == 0) minimumRimBottom[segment] = (float)(Math.min(referenceHeight, surfaceHeight) - 2.0);
                    if (band == bands) maximumRimBottom[segment] = (float)(Math.min(referenceHeight, surfaceHeight) - 2.0);
                    double radius = geometry.physicalRadiusAt(surfaceHeight);
                    int index = index(segment, band);
                    positionsX[index] = (float)(radius * Math.sin(angle));
                    positionsY[index] = (float)(-radius * Math.cos(angle));
                    positionsZ[index] = (float)z;
                    textureU[index] = u;
                    textureV[index] = (float)((sampleZ - geometry.minWidthZ())
                            / geometry.widthBlocks());
                    heights[index] = (float)surfaceHeight;
                    edgeColors[index] = atlas.sample(canonicalX, sampleZ).color();
                    upperU[index] = u;
                    upperV[index] = textureV[index];
                    if (detailed) {
                        double ax = geometry.wrapX(canonicalX) / atlas.sampleStep() - 0.5;
                        double az = (sampleZ - geometry.minWidthZ()) / atlas.sampleStep() - 0.5;
                        int x0 = (int)Math.floor(ax), z0 = (int)Math.floor(az);
                        int highest = Integer.MIN_VALUE;
                        for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) {
                            double weight = (dx == 0 ? 1.0 - (ax - x0) : ax - x0)
                                    * (dz == 0 ? 1.0 - (az - z0) : az - z0);
                            int col = Math.floorMod(x0 + dx, atlas.columns());
                            int row = Math.max(0, Math.min(atlas.rows() - 1, z0 + dz));
                            if (weight <= 0.0001 || !atlas.hasCell(col, row)) continue;
                            int height = atlas.cellHeight(col, row);
                            if (height > highest) {
                                highest = height;
                                upperU[index] = (col + 0.5F) / atlas.columns();
                                upperV[index] = (row + 0.5F) / atlas.rows();
                                int side = atlas.cellSideColor(col, row);
                                edgeColors[index] = side;
                                upperSideColor[index] = side == atlas.cellColor(col, row) ? -1 : side;
                            }
                        }
                    }
                }
            }
        }

        public int segments() { return segments; }
        public int bands() { return bands; }
        public int vertexCount() {
            int surface = Math.multiplyExact(Math.multiplyExact(segments, bands), 6);
            // Four wall faces, bounded cap bands on both rims, two terrain edges.
            return bridgeRims ? Math.addExact(surface,
                    Math.multiplyExact(segments, 36 + 12 * capBands)) : surface;
        }

        /** Emits the two consistently wound triangles for every finite quad. */
        public void emitTriangles(VertexConsumer consumer) {
            Objects.requireNonNull(consumer, "consumer");
            for (int segment = 0; segment < segments; segment++) {
                for (int band = 0; band < bands; band++) {
                    int a = index(segment, band), b = index(segment + 1, band);
                    int c = index(segment + 1, band + 1), d = index(segment, band + 1);
                    emitTriangle(consumer, a, b, c);
                    emitTriangle(consumer, a, c, d);
                }
            }
            consumer.sideColor(-1);
            if (bridgeRims) {
                for (int segment = 0; segment < segments; segment++) {
                    emitVerticalBridgeQuad(consumer, segment, bridgeMinimumZ,
                            MINIMUM_BRIDGE_TEXTURE_V, false);
                    emitVerticalBridgeQuad(consumer, segment, bridgeMaximumZ,
                            MAXIMUM_BRIDGE_TEXTURE_V, false);
                    emitVerticalBridgeQuad(consumer, segment, outerMinimumZ,
                            OUTER_BRIDGE_TEXTURE_V, true);
                    emitVerticalBridgeQuad(consumer, segment, outerMaximumZ,
                            OUTER_BRIDGE_TEXTURE_V, true);
                    for (int depth = 0; depth < capBands; depth++) {
                        emitTopBridgeQuad(consumer, segment, depth, outerMinimumZ, bridgeMinimumZ);
                        emitTopBridgeQuad(consumer, segment, depth, outerMaximumZ, bridgeMaximumZ);
                    }
                    emitTerrainEdge(consumer, segment, 0);
                    emitTerrainEdge(consumer, segment, bands);
                    consumer.sideColor(-1);
                }
            }
        }

        private void emitVerticalBridgeQuad(VertexConsumer consumer, int segment, float z, float v,
                                            boolean outer) {
            float u0 = (float)segment / segments;
            float u1 = (float)(segment + 1) / segments;
            float[] bottom = z < 0 ? minimumRimBottom : maximumRimBottom;
            float[] top = wallTops[outer ? 0 : capBands];
            float y0 = outer ? wallBottomY : Math.min(bottom[segment], top[segment]);
            float y1 = outer ? wallBottomY : Math.min(bottom[segment + 1], top[segment + 1]);
            emitBridgeVertex(consumer, segment, y0, z, u0, v);
            emitBridgeVertex(consumer, segment + 1, y1, z, u1, v);
            emitBridgeVertex(consumer, segment + 1, top[segment + 1], z, u1, v);
            emitBridgeVertex(consumer, segment, y0, z, u0, v);
            emitBridgeVertex(consumer, segment + 1, top[segment + 1], z, u1, v);
            emitBridgeVertex(consumer, segment, top[segment], z, u0, v);
        }

        private void emitTopBridgeQuad(VertexConsumer consumer, int segment, int depth,
                                       float outerZ, float innerZ) {
            float z0 = outerZ + (innerZ - outerZ) * depth / capBands;
            float z1 = outerZ + (innerZ - outerZ) * (depth + 1) / capBands;
            float u0 = (float)segment / segments, u1 = (float)(segment + 1) / segments;
            emitBridgeVertex(consumer, segment, wallTops[depth][segment], z0, u0, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment + 1, wallTops[depth][segment + 1], z0, u1, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment + 1, wallTops[depth + 1][segment + 1], z1, u1, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment, wallTops[depth][segment], z0, u0, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment + 1, wallTops[depth + 1][segment + 1], z1, u1, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment, wallTops[depth + 1][segment], z1, u0, TOP_BRIDGE_TEXTURE_V);
        }

        private float lowestCrest(int segment) {
            float lowest = wallTops[0][segment];
            for (int depth = 1; depth <= capBands; depth++) lowest = Math.min(lowest, wallTops[depth][segment]);
            return lowest;
        }

        private void emitTerrainEdge(VertexConsumer consumer, int segment, int band) {
            int a = index(segment, band), b = index(segment + 1, band);
            int color = heights[a] >= heights[b] ? a : b;
            consumer.sideColor(edgeColors[color]);
            float u = upperU[color] + 2.0F, v = upperV[color];
            // Share the exact top lattice. The lower edge is hidden inside the
            // crest, including decay, so mountains cannot leave an open sky slit.
            float y0 = Math.min(heights[a], lowestCrest(segment) - 2.0F);
            float y1 = Math.min(heights[b], lowestCrest(segment + 1) - 2.0F);
            float z = positionsZ[a];
            consumer.vertex(positionsX[a], positionsY[a], z, u, v);
            consumer.vertex(positionsX[b], positionsY[b], z, u, v);
            emitBridgeVertex(consumer, segment + 1, y1, z, u, v);
            consumer.vertex(positionsX[a], positionsY[a], z, u, v);
            emitBridgeVertex(consumer, segment + 1, y1, z, u, v);
            emitBridgeVertex(consumer, segment, y0, z, u, v);
        }

        private void emitBridgeVertex(VertexConsumer consumer, int segment, float y, float z,
                                      float u, float v) {
            double canonicalX = (double)segment * geometry.circumferenceBlocks() / segments;
            double angle = segment == segments ? 0.0
                    : Math.PI * 2.0 * canonicalX / geometry.circumferenceBlocks();
            double radius = geometry.physicalRadiusAt(y);
            consumer.vertex((float)(radius * Math.sin(angle)), (float)(-radius * Math.cos(angle)),
                    z, u, v);
        }

        /**
         * Returns one emitted triangle-list vertex for focused continuity
         * tests. The ordering is north-west, north-east, south-east,
         * north-west, south-east, south-west.
         */
        public Vertex triangleVertex(int segment, int band, int vertex) {
            if (segment < 0 || segment >= segments || band < 0 || band >= bands) {
                throw new IndexOutOfBoundsException("mesh cell outside surface lattice");
            }
            if (vertex < 0 || vertex >= 6) {
                throw new IndexOutOfBoundsException("triangle vertex must be in [0, 6)");
            }
            int a = index(segment, band), b = index(segment + 1, band);
            int c = index(segment + 1, band + 1), d = index(segment, band + 1);
            int point = switch (vertex) { case 0, 3 -> a; case 1 -> b; case 2, 4 -> c; default -> d; };
            int color = vertex < 3 ? triangleColorIndex(a, b, c) : triangleColorIndex(a, c, d);
            return new Vertex(positionsX[point], positionsY[point], positionsZ[point],
                    color < 0 ? textureU[point] : upperU[color] + (upperSideColor[color] < 0 ? 0 : 2),
                    color < 0 ? textureV[point] : upperV[color]);
        }

        private int triangleColorIndex(int a, int b, int c) {
            int highest = heights[a] >= heights[b] ? a : b;
            if (heights[c] > heights[highest]) highest = c;
            float lowest = Math.min(heights[a], Math.min(heights[b], heights[c]));
            return heights[highest] - lowest >= steepHeightThreshold ? highest : -1;
        }

        private void emitTriangle(VertexConsumer consumer, int a, int b, int c) {
            int color = triangleColorIndex(a, b, c);
            consumer.sideColor(color < 0 ? -1 : upperSideColor[color]);
            emitVertex(consumer, a, color);
            emitVertex(consumer, b, color);
            emitVertex(consumer, c, color);
        }

        private void emitVertex(VertexConsumer consumer, int point, int color) {
            // Constant texel-centre UVs on a steep face take the upper sample's
            // colour down the face. Zero UV derivatives also avoid mip filtering
            // the lower sand/water back into that face. Gentle slopes retain UVs.
            consumer.vertex(positionsX[point], positionsY[point], positionsZ[point],
                    color < 0 ? textureU[point] : upperU[color] + (upperSideColor[color] < 0 ? 0 : 2),
                    color < 0 ? textureV[point] : upperV[color]);
        }

        private int index(int segment, int band) {
            return Math.addExact(Math.multiplyExact(band, columns), segment);
        }
    }

    /** Physical coordinates are shared; steep faces may select independent upper-sample UVs. */
    public record Vertex(float x, float y, float z, float u, float v) { }
}
