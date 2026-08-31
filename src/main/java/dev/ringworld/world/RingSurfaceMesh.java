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

        RingRenderProfile profile = RingRenderProfile.create(
                geometry, 16.0, RingAtlasFidelity.forSampleStep(atlas.sampleStep()));
        int segments = Math.min(atlas.columns(), profile.circumferenceSegments());
        int bands = Math.min(atlas.rows(), profile.widthBands());
        return new Mesh(geometry, atlas, detailed, referenceHeight, wallTopHeight,
                innerFaces, segments, bands);
    }

    /** A consumer of the exact float vertex values written to the GPU buffer. */
    @FunctionalInterface
    public interface VertexConsumer {
        void vertex(float x, float y, float z, float u, float v);
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
        private final RingGeometry geometry;
        private final boolean bridgeRims;
        private final float bridgeBottomY;
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
            bridgeBottomY = (float)referenceHeight;
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
                    double radius = geometry.physicalRadiusAt(surfaceHeight);
                    int index = index(segment, band);
                    positionsX[index] = (float)(radius * Math.sin(angle));
                    positionsY[index] = (float)(-radius * Math.cos(angle));
                    positionsZ[index] = (float)z;
                    textureU[index] = u;
                    textureV[index] = (float)((sampleZ - geometry.minWidthZ())
                            / geometry.widthBlocks());
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
                    emitTriangleVertex(consumer, segment, band, 0);
                    emitTriangleVertex(consumer, segment, band, 1);
                    emitTriangleVertex(consumer, segment, band, 2);
                    emitTriangleVertex(consumer, segment, band, 0);
                    emitTriangleVertex(consumer, segment, band, 2);
                    emitTriangleVertex(consumer, segment, band, 3);
                }
            }
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
            emitBridgeVertex(consumer, segment, bridgeBottomY, z, u0, v);
            emitBridgeVertex(consumer, segment + 1, bridgeBottomY, z, u1, v);
            emitBridgeVertex(consumer, segment + 1, bridgeTopY, z, u1, v);
            emitBridgeVertex(consumer, segment, bridgeBottomY, z, u0, v);
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
            return switch (vertex) {
                case 0, 3 -> vertex(segment, band);
                case 1 -> vertex(segment + 1, band);
                case 2, 4 -> vertex(segment + 1, band + 1);
                case 5 -> vertex(segment, band + 1);
                default -> throw new AssertionError("validated triangle vertex");
            };
        }

        private void emitTriangleVertex(VertexConsumer consumer, int segment, int band,
                                        int corner) {
            int index = switch (corner) {
                case 0 -> index(segment, band);
                case 1 -> index(segment + 1, band);
                case 2 -> index(segment + 1, band + 1);
                case 3 -> index(segment, band + 1);
                default -> throw new AssertionError("mesh corner");
            };
            consumer.vertex(positionsX[index], positionsY[index], positionsZ[index],
                    textureU[index], textureV[index]);
        }

        private Vertex vertex(int segment, int band) {
            int index = index(segment, band);
            return new Vertex(positionsX[index], positionsY[index], positionsZ[index],
                    textureU[index], textureV[index]);
        }

        private int index(int segment, int band) {
            return Math.addExact(Math.multiplyExact(band, columns), segment);
        }
    }

    /** Exact float values shared by all triangles incident on one lattice point. */
    public record Vertex(float x, float y, float z, float u, float v) { }
}
