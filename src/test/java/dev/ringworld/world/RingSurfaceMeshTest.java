package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingSurfaceMeshTest {
    private static final long HASH = 0x4D45_5348L;

    @Test
    void detailedProductionMeshSharesEveryAdjacentBandBoundaryExactly() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(
                geometry, variedCompleteAtlas(geometry), true, RingGeometry.SURFACE_Y);

        assertEquals(2_048, mesh.segments());
        assertEquals(32, mesh.bands());
        assertEquals(393_216, mesh.vertexCount());
        for (int segment = 0; segment < mesh.segments(); segment++) {
            for (int band = 0; band < mesh.bands() - 1; band++) {
                assertEquals(mesh.triangleVertex(segment, band, 5),
                        mesh.triangleVertex(segment, band + 1, 0));
                assertEquals(mesh.triangleVertex(segment, band, 2),
                        mesh.triangleVertex(segment, band + 1, 1));
            }
        }
    }

    @Test
    void detailedMeshSharesSegmentEdgesAndClosesPhysicalSeamWhileKeepingUvWrap() {
        RingGeometry geometry = new RingGeometry(416, 2_048);
        RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(
                geometry, variedCompleteAtlas(geometry), true, RingGeometry.SURFACE_Y);
        List<RingSurfaceMesh.Vertex> triangles = emitted(mesh);

        for (int segment = 0; segment < mesh.segments() - 1; segment++) {
            for (int band = 0; band < mesh.bands(); band++) {
                assertEquals(triangleVertex(triangles, mesh, segment, band, 1),
                        triangleVertex(triangles, mesh, segment + 1, band, 0));
                assertEquals(triangleVertex(triangles, mesh, segment, band, 2),
                        triangleVertex(triangles, mesh, segment + 1, band, 5));
            }
        }

        for (int band = 0; band < mesh.bands(); band++) {
            RingSurfaceMesh.Vertex seamEnd = triangleVertex(
                    triangles, mesh, mesh.segments() - 1, band, 1);
            RingSurfaceMesh.Vertex seamStart = triangleVertex(triangles, mesh, 0, band, 0);
            assertEquals(seamStart.x(), seamEnd.x());
            assertEquals(seamStart.y(), seamEnd.y());
            assertEquals(seamStart.z(), seamEnd.z());
            assertEquals(0.0F, seamStart.u());
            assertEquals(1.0F, seamEnd.u());
            assertEquals(seamStart.v(), seamEnd.v());
        }
    }

    @Test
    void referenceHeightMeshUsesTheSameSharedLattice() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(
                geometry, variedCompleteAtlas(geometry), false, 83.5);

        assertEquals(mesh.triangleVertex(19, 12, 5),
                mesh.triangleVertex(19, 13, 0));
        assertEquals(mesh.triangleVertex(19, 12, 2),
                mesh.triangleVertex(19, 13, 1));
    }

    @Test
    void incompleteMeshAddsClosedReturnsAtBothInnerRimFaces() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, HASH);
        RingSurfaceMesh.Mesh withoutReturns = RingSurfaceMesh.build(
                geometry, atlas, false, 64.0);
        RingSurfaceMesh.Mesh withReturns = RingSurfaceMesh.build(
                geometry, atlas, false, 64.0, 96.0, 5);

        assertEquals(withoutReturns.vertexCount() + withReturns.segments() * 12,
                withReturns.vertexCount());
    }

    private static RingTerrainAtlas variedCompleteAtlas(RingGeometry geometry) {
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, HASH);
        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                int height = 48 + Math.floorMod(column * 11 + row * 17, 97);
                atlas.putCell(column, row, height, 0x336699);
            }
        }
        return atlas;
    }

    private static List<RingSurfaceMesh.Vertex> emitted(RingSurfaceMesh.Mesh mesh) {
        List<RingSurfaceMesh.Vertex> vertices = new ArrayList<>(mesh.vertexCount());
        mesh.emitTriangles((x, y, z, u, v) -> vertices.add(
                new RingSurfaceMesh.Vertex(x, y, z, u, v)));
        assertEquals(mesh.vertexCount(), vertices.size());
        return vertices;
    }

    private static RingSurfaceMesh.Vertex triangleVertex(List<RingSurfaceMesh.Vertex> triangles,
                                                          RingSurfaceMesh.Mesh mesh,
                                                          int segment, int band, int vertex) {
        return triangles.get((segment * mesh.bands() + band) * 6 + vertex);
    }
}
