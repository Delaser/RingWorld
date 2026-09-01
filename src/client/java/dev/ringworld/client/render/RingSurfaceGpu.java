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
        int count = mesh.vertexCount();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(count * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    allocator, VertexFormat.Mode.TRIANGLES, format);
            mesh.emitTriangles((x, y, z, u, v) -> builder.addVertex(x, y, z)
                    .setUv(u, v).setColor(vertexArgb));
            MeshData built = builder.buildOrThrow();
            VertexBuffer replacement = new VertexBuffer(VertexBuffer.Usage.STATIC);
            replacement.bind();
            replacement.upload(built);
            VertexBuffer.unbind();
            return replacement;
        }
    }
}
