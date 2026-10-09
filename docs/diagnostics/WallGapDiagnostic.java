package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;

/** Isolated CPU probe of the pre-fix Atlas mesh and deterministic wall decay. */
public final class WallGapDiagnostic {
    public static void main(String[] args) {
        var geometry = new RingGeometry(128, 2048);
        var atlas = new RingTerrainAtlas(geometry, 255, 8);
        for (int z = 0; z < atlas.rows(); z++) {
            for (int x = 0; x < atlas.columns(); x++) atlas.putCell(x, z, 144, 0x336699);
        }
        var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, 96, 5);
        List<double[]> vertices = new ArrayList<>();
        mesh.emitTriangles((x, y, z, u, v) -> vertices.add(new double[] {
                geometry.physicalCenterY() - Math.hypot(x, y), z, v }));
        int terrainSides = 0, steppedWallCaps = 0;
        for (int i = 0; i < vertices.size(); i += 3) {
            var a = vertices.get(i);
            var b = vertices.get(i + 1);
            var c = vertices.get(i + 2);
            double min = Math.min(a[0], Math.min(b[0], c[0]));
            double max = Math.max(a[0], Math.max(b[0], c[0]));
            if (Math.abs(a[1] - b[1]) < 1e-6 && Math.abs(a[1] - c[1]) < 1e-6
                    && min < 143 && max > 97) terrainSides++;
            if (a[2] == 3 && min < 95.9) steppedWallCaps++;
        }
        var intact = RingWallStyle.custom(5, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.ENGINEERED, 0);
        var decayed = RingWallStyle.custom(5, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.ENGINEERED, 25);
        int intactMissing = 0, decayedMissing = 0, depthDifferences = 0;
        for (int x = 0; x < 2048; x++) {
            int inner = RingWallPattern.topCollapseDepth(decayed, x, 4, 2048, 255);
            int outer = RingWallPattern.topCollapseDepth(decayed, x, 0, 2048, 255);
            if (inner != outer) depthDifferences++;
            for (int y = 64; y < 96; y++) {
                if (!RingWallPattern.blockPresent(intact, x, y, 4, 96, 2048, 255)) intactMissing++;
                if (!RingWallPattern.blockPresent(decayed, x, y, 4, 96, 2048, 255)) decayedMissing++;
            }
        }
        System.out.printf("terrain=144 wallTop=96 missing span=48 blocks; vertical faces covering span=%d%n", terrainSides);
        System.out.printf("stepped wall caps below original top=%d; zero-decay transparent blocks=%d; normal-decay transparent blocks=%d; differing inner/outer column heights=%d%n",
                steppedWallCaps, intactMissing, decayedMissing, depthDifferences);
        if (terrainSides != 0 || steppedWallCaps != 0 || intactMissing != 0
                || decayedMissing == 0 || depthDifferences == 0) {
            throw new AssertionError("Diagnostic no longer matches geometry; re-investigate");
        }
    }
}
