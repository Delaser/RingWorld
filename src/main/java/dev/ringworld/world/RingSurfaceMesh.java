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
        RingCloudBounds innerFaces = RingCloudBounds.betweenInnerRimFaces(
                geometry, rimThicknessBlocks);

        int segments = Math.min(atlas.columns(), profile.circumferenceSegments());
        int bands = Math.min(atlas.rows(), profile.widthBands());
        return new Mesh(geometry, atlas, detailed, referenceHeight, wallTopHeight,
                innerFaces, segments, bands);
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
        private final float bridgeTopY;
        private final float bridgeMinimumZ;
        private final float bridgeMaximumZ;
        private final float outerMinimumZ;
        private final float outerMaximumZ;

        private Mesh(RingGeometry geometry, RingTerrainAtlas atlas, boolean detailed,
                     double referenceHeight, double wallTopHeight,
                     RingCloudBounds innerFaces, int segments, int bands) {
            this.geometry = geometry;
            this.segments = segments;
            this.bands = bands;
            // Rims are vertical structures, while the terrain Atlas stores one
            // exposed top sample per cell. Stretching the detailed terrain mesh
            // through those high rim samples produces a broad ramp once the
            // Atlas completes. Keep the style-derived closed rim at every Atlas
            // stage and terminate terrain at its inner faces instead.
            bridgeRims = wallTopHeight > referenceHeight;
            minimumRimBottom = new float[segments + 1];
            maximumRimBottom = new float[segments + 1];
            bridgeTopY = (float)wallTopHeight;
            bridgeMinimumZ = (float)innerFaces.minimumZ();
            bridgeMaximumZ = (float)innerFaces.maximumZ();
            outerMinimumZ = (float)geometry.minWidthZ();
            outerMaximumZ = (float)geometry.maxWidthZ();
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
            // Two rims, each closed above the reference surface with an inner
            // face, an outer face, and a top face: six quads per segment.
            return bridgeRims ? Math.addExact(surface, Math.multiplyExact(segments, 36)) : surface;
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
                    // V outside the surface's [0,1] range is a shader-stable
                    // wall marker. The temporary wall is a closed prism rather
                    // than the old pair of inner-face curtains.
                    emitVerticalBridgeQuad(consumer, segment, bridgeMinimumZ,
                            MINIMUM_BRIDGE_TEXTURE_V);
                    emitVerticalBridgeQuad(consumer, segment, bridgeMaximumZ,
                            MAXIMUM_BRIDGE_TEXTURE_V);
                    emitVerticalBridgeQuad(consumer, segment, outerMinimumZ,
                            OUTER_BRIDGE_TEXTURE_V);
                    emitVerticalBridgeQuad(consumer, segment, outerMaximumZ,
                            OUTER_BRIDGE_TEXTURE_V);
                    emitTopBridgeQuad(consumer, segment, outerMinimumZ, bridgeMinimumZ);
                    emitTopBridgeQuad(consumer, segment, bridgeMaximumZ, outerMaximumZ);
                }
            }
        }

        private void emitVerticalBridgeQuad(VertexConsumer consumer, int segment, float z, float v) {
            float u0 = (float)segment / segments;
            float u1 = (float)(segment + 1) / segments;
            float[] bottom = z < 0 ? minimumRimBottom : maximumRimBottom;
            emitBridgeVertex(consumer, segment, bottom[segment], z, u0, v);
            emitBridgeVertex(consumer, segment + 1, bottom[segment + 1], z, u1, v);
            emitBridgeVertex(consumer, segment + 1, bridgeTopY, z, u1, v);
            emitBridgeVertex(consumer, segment, bottom[segment], z, u0, v);
            emitBridgeVertex(consumer, segment + 1, bridgeTopY, z, u1, v);
            emitBridgeVertex(consumer, segment, bridgeTopY, z, u0, v);
        }

        private void emitTopBridgeQuad(VertexConsumer consumer, int segment,
                                       float z0, float z1) {
            float u0 = (float)segment / segments;
            float u1 = (float)(segment + 1) / segments;
            emitBridgeVertex(consumer, segment, bridgeTopY, z0, u0, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment + 1, bridgeTopY, z0, u1, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment + 1, bridgeTopY, z1, u1, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment, bridgeTopY, z0, u0, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment + 1, bridgeTopY, z1, u1, TOP_BRIDGE_TEXTURE_V);
            emitBridgeVertex(consumer, segment, bridgeTopY, z1, u0, TOP_BRIDGE_TEXTURE_V);
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
