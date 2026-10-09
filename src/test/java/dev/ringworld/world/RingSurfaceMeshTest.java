package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
                assertPositionEquals(mesh.triangleVertex(segment, band, 5),
                        mesh.triangleVertex(segment, band + 1, 0));
                assertPositionEquals(mesh.triangleVertex(segment, band, 2),
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
                assertPositionEquals(triangleVertex(triangles, mesh, segment, band, 1),
                        triangleVertex(triangles, mesh, segment + 1, band, 0));
                assertPositionEquals(triangleVertex(triangles, mesh, segment, band, 2),
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
            // Steep faces intentionally have independent upper-sample UVs;
            // only physical positions must remain shared at the seam.
        }
    }

    @Test
    void referenceHeightMeshUsesTheSameSharedLattice() {
        RingGeometry geometry = new RingGeometry(256, 2_048);
        RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(
                geometry, variedCompleteAtlas(geometry), false, 83.5);

        assertEquals(0.0F, mesh.triangleVertex(0, 0, 0).u());
        assertEquals(1.0F, mesh.triangleVertex(mesh.segments() - 1, 0, 1).u());
        assertEquals(mesh.triangleVertex(19, 12, 5),
                mesh.triangleVertex(19, 13, 0));
        assertEquals(mesh.triangleVertex(19, 12, 2),
                mesh.triangleVertex(19, 13, 1));
    }

    @Test
    void rimmedMeshAddsInnerOuterAndTopFacesAtEveryAtlasStage() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, HASH, 8);
        RingSurfaceMesh.Mesh withoutReturns = RingSurfaceMesh.build(
                geometry, atlas, false, 64.0);
        RingSurfaceMesh.Mesh withReturns = RingSurfaceMesh.build(
                geometry, atlas, false, 64.0, 96.0, 5);
        RingSurfaceMesh.Mesh detailedWithReturns = RingSurfaceMesh.build(
                geometry, variedCompleteAtlas(geometry), true, 64.0, 96.0, 5);

        assertEquals(withoutReturns.vertexCount() + withReturns.segments() * 84,
                withReturns.vertexCount());
        assertEquals(withoutReturns.vertexCount() + detailedWithReturns.segments() * 84,
                detailedWithReturns.vertexCount());
        List<RingSurfaceMesh.Vertex> vertices = emitted(withReturns);
        int surfaceVertices = withoutReturns.vertexCount();
        for (int segment = 0; segment < withReturns.segments(); segment++) {
            int bridgeOffset = surfaceVertices + segment * 84;
            for (int vertex = 0; vertex < 6; vertex++) {
                assertEquals(RingSurfaceMesh.MINIMUM_BRIDGE_TEXTURE_V,
                        vertices.get(bridgeOffset + vertex).v());
                assertEquals(RingSurfaceMesh.MAXIMUM_BRIDGE_TEXTURE_V,
                        vertices.get(bridgeOffset + 6 + vertex).v());
                assertEquals(RingSurfaceMesh.OUTER_BRIDGE_TEXTURE_V,
                        vertices.get(bridgeOffset + 12 + vertex).v());
                assertEquals(RingSurfaceMesh.OUTER_BRIDGE_TEXTURE_V,
                        vertices.get(bridgeOffset + 18 + vertex).v());
                assertEquals(RingSurfaceMesh.TOP_BRIDGE_TEXTURE_V,
                        vertices.get(bridgeOffset + 24 + vertex).v());
                for (int cap = 0; cap < 8; cap++) assertEquals(RingSurfaceMesh.TOP_BRIDGE_TEXTURE_V,
                        vertices.get(bridgeOffset + 24 + cap * 6 + vertex).v());
            }
        }
    }

    @Test
    void permanentRimsClipDetailedTerrainToPlayableInnerFaces() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(
                geometry, flatCompleteAtlas(geometry, 64), true, 64.0, 96.0, 5);

        RingSurfaceMesh.Vertex minimum = mesh.triangleVertex(0, 0, 0);
        RingSurfaceMesh.Vertex maximum = mesh.triangleVertex(0, mesh.bands() - 1, 5);
        assertEquals(-59.5F, minimum.z());
        assertEquals(59.5F, maximum.z());
        // Texture samples sit one full Atlas cell inside the wall so neither
        // colour nor relief inherits a high rim sample.
        assertEquals((-51.0F + 64.0F) / 128.0F, minimum.v());
        assertEquals((51.0F + 64.0F) / 128.0F, maximum.v());
    }

    @Test
    void steepFacesUseUpperColourWhileLowerFlatTerrainKeepsItsOwnUv() {
        var geometry = new RingGeometry(128, 2048);
        var atlas = new RingTerrainAtlas(geometry, HASH, 1);
        for (int row = 0; row < atlas.rows(); row++) for (int col = 0; col < atlas.columns(); col++)
            atlas.putCell(col, row, col < 1024 ? 64 : 96, col < 1024 ? 0xFFFFAA : 0x227722);
        var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, 64, 1,
                RingLodQuality.HIGH.profile(geometry, 96));
        var a = mesh.triangleVertex(1023, 64, 0);
        var b = mesh.triangleVertex(1023, 64, 1);
        var c = mesh.triangleVertex(1023, 64, 2);
        assertEquals(a.u(), b.u()); assertEquals(a.u(), c.u());
        assertEquals(a.v(), b.v()); assertEquals(a.v(), c.v());
        assertEquals(0x227722, atlas.sample(a.u() * 2048, a.v() * 128 - 64).color());
        assertNotEquals(mesh.triangleVertex(100, 64, 0).u(), mesh.triangleVertex(100, 64, 1).u());
        assertEquals(0xFFFFAA, atlas.sample(100.5, 0.5).color());
    }

    @Test
    void rimBottomOverlapsTerrainBelowReferenceHeight() {
        var geometry = new RingGeometry(128, 2048);
        var atlas = flatCompleteAtlas(geometry, 48);
        // A remote depression must not lower the entire ring's wall bottom.
        for (int row = 0; row < atlas.rows(); row++) atlas.putCell(100, row, 16, 0x336699);
        var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, 96, 5);
        var vertices = emitted(mesh);
        int firstWall = mesh.segments() * mesh.bands() * 6;
        var bottom = vertices.get(firstWall);
        assertEquals(geometry.physicalRadiusAt(46), Math.hypot(bottom.x(), bottom.y()), 0.0001);
    }

    @Test
    void elevatedFiniteTerrainEdgesUseExactLatticeAndReachInsideTheCrest() {
        var geometry = new RingGeometry(128, 2048);
        var mesh = RingSurfaceMesh.build(geometry, flatCompleteAtlas(geometry, 144), true,
                64, 96, 160, RingWallStyle.DEFAULT, 255,
                RingLodQuality.MEDIUM.profile(geometry, 96));
        var vertices = emitted(mesh);
        int surface = mesh.segments() * mesh.bands() * 6;
        for (int segment = 0; segment < mesh.segments(); segment++) {
            for (int side = 0; side < 2; side++) {
                int offset = surface + segment * 84 + 72 + side * 6;
                var top = mesh.triangleVertex(segment, side == 0 ? 0 : mesh.bands() - 1,
                        side == 0 ? 0 : 5);
                assertPositionEquals(top, vertices.get(offset));
                var bottom = vertices.get(offset + 5);
                assertTrue(geometry.physicalCenterY() - Math.hypot(bottom.x(), bottom.y()) < 96);
                assertEquals(top.z(), bottom.z());
                assertTrue(bottom.u() >= 2, "terrain edge uses side material, not wall material");
            }
        }
    }

    @Test
    void exteriorWallsMatchWorldFloorAndDecayedCapsShareEveryFaceEdge() {
        var geometry = new RingGeometry(128, 2048);
        var style = RingWallStyle.DEFAULT;
        var mesh = RingSurfaceMesh.build(geometry, flatCompleteAtlas(geometry, 64), true,
                64, 96, 160, style, 255, RingLodQuality.HIGH.profile(geometry, 96));
        var vertices = emitted(mesh);
        int surface = mesh.segments() * mesh.bands() * 6;
        boolean decayed = false;
        for (int segment = 0; segment < mesh.segments(); segment++) {
            int start = surface + segment * 84;
            for (int side = 0; side < 2; side++) {
                int face = start + 12 + side * 6;
                var bottom = vertices.get(face);
                assertEquals(geometry.physicalRadiusAt(-64), Math.hypot(bottom.x(), bottom.y()), 0.0001);
                assertEquals(side == 0 ? -64 : 64, bottom.z());
                // Exterior crest meets the first cap; interior meets the last.
                int firstCap = start + 24 + side * 6;
                int lastCap = start + 24 + 3 * 12 + side * 6;
                assertPositionEquals(vertices.get(face + 5), vertices.get(firstCap));
                assertPositionEquals(vertices.get(start + side * 6 + 5), vertices.get(lastCap + 5));
                for (int depth = 0; depth < 3; depth++) {
                    int cap = firstCap + depth * 12;
                    assertPositionEquals(vertices.get(cap + 5), vertices.get(cap + 12));
                    assertPositionEquals(vertices.get(cap + 2), vertices.get(cap + 13));
                }
                int x = segment * geometry.circumferenceBlocks() / mesh.segments();
                int expected = 96 - RingWallPattern.topCollapseDepth(style, x, 0, 2048, 255);
                assertEquals(geometry.physicalRadiusAt(expected),
                        Math.hypot(vertices.get(face + 5).x(), vertices.get(face + 5).y()), 0.0001);
                decayed |= expected < 96;
                int next = surface + ((segment + 1) % mesh.segments()) * 84;
                assertPositionEquals(vertices.get(face + 2), vertices.get(next + 12 + side * 6 + 5));
            }
        }
        assertTrue(decayed, "do not erase intentional decay to hide holes");
    }

    @Test
    void savedHeightExtendsDownFromTheAlignedTopAtAnyElevation() {
        var geometry = new RingGeometry(128, 2048);
        var atlas = flatCompleteAtlas(geometry, 144);
        for (int top : new int[]{96, 120}) for (int height : new int[]{32, 160, 256}) {
            var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, top, height,
                    RingWallStyle.DEFAULT, 255, RingLodQuality.LOW.profile(geometry, 96));
            var vertices = emitted(mesh);
            int firstOuter = mesh.segments() * mesh.bands() * 6 + 12;
            var bottom = vertices.get(firstOuter);
            assertEquals(geometry.physicalRadiusAt(top - height), Math.hypot(bottom.x(), bottom.y()), 0.0001);
            assertThrows(IllegalArgumentException.class, () -> RingSurfaceMesh.build(geometry, atlas,
                    true, 64, top, -1, RingWallStyle.DEFAULT, 255, RingLodQuality.LOW.profile(geometry, 96)));
        }
    }

    @Test
    void wallsBelowReferenceStillCloseTerrainAndReachWorldBottom() {
        var geometry = new RingGeometry(128, 2048);
        var mesh = RingSurfaceMesh.build(geometry, flatCompleteAtlas(geometry, 144), true,
                64, -32, 32, RingWallStyle.DEFAULT, 255,
                RingLodQuality.LOW.profile(geometry, 96));
        assertEquals(mesh.segments() * (mesh.bands() * 6 + 84), emitted(mesh).size());
    }

    @Test
    void widestWallsKeepCapGeometryBoundedAtEveryQuality() {
        var geometry = new RingGeometry(128, 2048);
        var atlas = flatCompleteAtlas(geometry, 144);
        for (var quality : RingLodQuality.values()) {
            for (int thickness : new int[]{1, 5, 32}) {
                var style = RingWallStyle.custom(thickness, RingWallStyle.DEFAULT.palette(),
                        RingWallStyle.DEFAULT.pattern(), 100);
                var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, 96, 160,
                        style, 255, quality.profile(geometry, 96));
                assertEquals(mesh.segments() * (mesh.bands() * 6 + 36 + 12 * Math.min(4, thickness)),
                        emitted(mesh).size());
            }
        }
    }

    private static void assertPositionEquals(RingSurfaceMesh.Vertex a, RingSurfaceMesh.Vertex b) {
        assertEquals(a.x(), b.x()); assertEquals(a.y(), b.y()); assertEquals(a.z(), b.z());
    }

    private static RingTerrainAtlas flatCompleteAtlas(RingGeometry geometry, int height) {
        var atlas = new RingTerrainAtlas(geometry, HASH, 8);
        for (int row = 0; row < atlas.rows(); row++) for (int col = 0; col < atlas.columns(); col++)
            atlas.putCell(col, row, height, 0x336699);
        return atlas;
    }

    private static RingTerrainAtlas variedCompleteAtlas(RingGeometry geometry) {
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, HASH, 8);
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
