package dev.ringworld.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.world.RingSurfaceMesh;

/** Minecraft 1.21.1 vertex-buffer adapter for the shared CPU surface mesh. */
final class RingSurfaceGpu {
    private RingSurfaceGpu() { }

    static VertexBuffer createVertexBuffer(RingSurfaceMesh.Mesh mesh, int vertexArgb) {
        try (PackedMesh packed = packMesh(mesh, vertexArgb)) { return uploadMesh(packed); }
    }

    /** Native bytes are prepared on the serial worker; no GL calls occur here. */
    static PackedMesh packMesh(RingSurfaceMesh.Mesh mesh, int vertexArgb) {
        int count = mesh.vertexCount();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        ByteBufferBuilder allocator = new ByteBufferBuilder(Math.multiplyExact(count, format.getVertexSize()));
        try {
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.Mode.TRIANGLES, format);
            mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
                private int sideColor = -1;
                @Override public void sideColor(int rgb) { sideColor = rgb; }
                @Override public void vertex(float x, float y, float z, float u, float v) {
                    builder.addVertex(x, y, z).setUv(u, v)
                            .setColor(sideColor < 0 ? vertexArgb : 0xFF000000 | sideColor);
                }
            });
            return new PackedMesh(allocator, builder.buildOrThrow(), count);
        } catch (RuntimeException | Error failure) { allocator.close(); throw failure; }
    }

    static VertexBuffer uploadMesh(PackedMesh packed) {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        VertexBuffer replacement = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            replacement.bind();
            MeshData data = packed.data;
            packed.data = null; // VertexBuffer.upload owns and closes the submitted MeshData.
            replacement.upload(data);
            return replacement;
        } catch (RuntimeException | Error failure) { replacement.close(); throw failure; }
        finally { VertexBuffer.unbind(); }
    }

    static final class PackedMesh implements AutoCloseable {
        private ByteBufferBuilder allocator;
        private MeshData data;
        private final int vertexCount;
        PackedMesh(ByteBufferBuilder allocator, MeshData data, int vertexCount) {
            this.allocator = allocator; this.data = data; this.vertexCount = vertexCount;
        }
        int vertexCount() { return vertexCount; }
        @Override public void close() {
            try { if (data != null) data.close(); } finally {
                data = null;
                if (allocator != null) allocator.close();
                allocator = null;
            }
        }
    }
}
