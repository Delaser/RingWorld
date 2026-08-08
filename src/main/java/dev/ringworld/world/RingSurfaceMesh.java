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
    private RingSurfaceMesh() { }

    /** Builds the bounded mesh lattice selected by the active render profile. */
    public static Mesh build(RingGeometry geometry, RingTerrainAtlas atlas,
                             boolean detailed, double referenceHeight) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(atlas, "atlas");
        if (!geometry.equals(atlas.geometry())) {
            throw new IllegalArgumentException("atlas geometry does not match surface mesh geometry");
        }
        if (!Double.isFinite(referenceHeight)) {
            throw new IllegalArgumentException("reference height must be finite");
        }

        RingRenderProfile profile = RingRenderProfile.create(geometry, 16.0);
        int segments = Math.min(atlas.columns(), profile.circumferenceSegments());
        int bands = Math.min(atlas.rows(), profile.widthBands());
        return new Mesh(geometry, atlas, detailed, referenceHeight, segments, bands);
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

        private Mesh(RingGeometry geometry, RingTerrainAtlas atlas, boolean detailed,
                     double referenceHeight, int segments, int bands) {
            this.segments = segments;
            this.bands = bands;
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
                    double z = geometry.minWidthZ()
                            + (double)band * geometry.widthBlocks() / bands;
                    double surfaceHeight = detailed
                            ? atlas.sample(canonicalX, z).height()
                            : referenceHeight;
                    double radius = geometry.physicalRadiusAt(surfaceHeight);
                    int index = index(segment, band);
                    positionsX[index] = (float)(radius * Math.sin(angle));
                    positionsY[index] = (float)(-radius * Math.cos(angle));
                    positionsZ[index] = (float)z;
                    textureU[index] = u;
                    textureV[index] = (float)((z - geometry.minWidthZ())
                            / geometry.widthBlocks());
                }
            }
        }

        public int segments() { return segments; }
        public int bands() { return bands; }
        public int vertexCount() {
            return Math.multiplyExact(Math.multiplyExact(segments, bands), 6);
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
