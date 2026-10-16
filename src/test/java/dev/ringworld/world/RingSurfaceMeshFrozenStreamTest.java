package dev.ringworld.world;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Exact emitted GPU-input streams generated independently from the frozen old jar. */
class RingSurfaceMeshFrozenStreamTest {
    record Fixture(int step, boolean dense, boolean detailed, int top, int vertices, String digest) { }

    static Stream<Fixture> fixtures() throws Exception {
        try (var stream = RingSurfaceMeshFrozenStreamTest.class.getResourceAsStream("surface-mesh-streams-golden-v1.csv")) {
            assertNotNull(stream);
            List<String> lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList();
            assertEquals("# frozenJarSha256=d3c2a43ef11c0ce08b106d4003d40b060ce1f597cf0f5f7203aee9701a8a2efc", lines.get(0));
            var fixtures = lines.stream().filter(line -> !line.startsWith("#")).map(line -> {
                String[] fields = line.split(",");
                return new Fixture(Integer.parseInt(fields[0]), Boolean.parseBoolean(fields[1]),
                        Boolean.parseBoolean(fields[2]), Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]), fields[5]);
            }).toList();
            assertEquals(32, fixtures.size());
            return fixtures.stream();
        }
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void completeEmittedVertexMaterialAndIntrinsicStreamMatchesOldJar(Fixture fixture) throws Exception {
        var geometry = new RingGeometry(128, 2048);
        var atlas = new RingTerrainAtlas(geometry, 42L, fixture.step());
        for (int z = 0; z < atlas.rows(); z++) for (int x = 0; x < atlas.columns(); x++) {
            if (!fixture.dense() && Math.floorMod(x * 31 + z * 17, 11) == 0) continue;
            atlas.putCell(x, z, (x * 37 + z * 19) % 384 - 64,
                    (x * 1103515245 + z * 12345) ^ 0x12abcd, (x ^ z) & 15, 0xabcdef, (x + 3 * z) & 15);
        }
        var mesh = RingSurfaceMesh.build(geometry, atlas, fixture.detailed(), 64.0, fixture.top(), 5);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        var data = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
        mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
            private void write(int tag, float... values) {
                try {
                    data.writeByte(tag);
                    for (float value : values) data.writeInt(Float.floatToRawIntBits(value));
                } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
            @Override public void vertex(float x, float y, float z, float u, float v) { write(0, x, y, z, u, v); }
            @Override public void intrinsic(float x, float y, float z) { write(2, x, y, z); }
            @Override public void sideColor(int rgb) {
                try { data.writeByte(1); data.writeInt(rgb); }
                catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
        });
        assertEquals(fixture.vertices(), mesh.vertexCount());
        assertEquals(fixture.digest(), HexFormat.of().formatHex(digest.digest()));
    }
}
