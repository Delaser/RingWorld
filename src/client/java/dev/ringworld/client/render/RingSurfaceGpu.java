package dev.ringworld.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.world.RingSurfaceMesh;
import dev.ringworld.world.RingSurfaceMeshBytes;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/** Minecraft 1.21.1 vertex-buffer adapter for the shared CPU surface mesh. */
final class RingSurfaceGpu {
    private RingSurfaceGpu() { }

    /** Native bytes are prepared on the serial worker; no GL calls occur here. */
    static PackedMesh packMesh(RingSurfaceMesh.Mesh mesh, int vertexArgb) {
        int count = mesh.vertexCount();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        ByteBufferBuilder allocator = new ByteBufferBuilder(Math.multiplyExact(count, format.getVertexSize()));
        try {
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.Mode.TRIANGLES, format);
            mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
                private int sideColor = -1;
                private float intrinsicX, intrinsicY, intrinsicZ;
                private boolean intrinsicReady;
                @Override public void sideColor(int rgb) { sideColor = rgb; }
                @Override public void intrinsic(float x, float y, float z) {
                    intrinsicX = x; intrinsicY = y; intrinsicZ = z;
                    intrinsicReady = true;
                }
                @Override public void vertex(float x, float y, float z, float u, float v) {
                    if (!intrinsicReady) throw new IllegalStateException("surface vertex lacks intrinsic coordinates");
                    intrinsicReady = false;
                    builder.addVertex(intrinsicX, intrinsicY, intrinsicZ).setUv(u, v)
                            .setColor(sideColor < 0 ? vertexArgb : 0xFF000000 | sideColor);
                }
            });
            MeshData data = builder.buildOrThrow();
            try { return new PackedMesh(allocator, data, count); }
            catch (RuntimeException | Error failure) { data.close(); throw failure; }
        } catch (RuntimeException | Error failure) { allocator.close(); throw failure; }
    }

    /** Allocate draw state first; transfer at most one bounded slice on later render calls. */
    static Upload beginUpload(PackedMesh packed) {
        RenderSystem.assertOnRenderThread();
        MeshData data = packed.data;
        if (data == null || data.indexBuffer() != null
                || data.drawState().mode() != VertexFormat.Mode.TRIANGLES) {
            throw new IllegalArgumentException("surface upload requires an owned unindexed triangle mesh");
        }
        ByteBuffer source = data.vertexBuffer().asReadOnlyBuffer();
        VertexBuffer replacement = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            replacement.bind();
            // Pinned 1.21.1 VertexBuffer sets up its VAO, format and sequential
            // indices while skipping vertex transfer when this view is null.
            // Only the header is submitted/closed here. PackedMesh continues to
            // own the real MeshData and allocator until the entire build retires.
            replacement.upload(new DrawStateOnly(data.drawState()));
            int bufferId = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            if (bufferId <= 0) throw new IllegalStateException("surface vertex buffer was not bound");
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) source.remaining(), GL15.GL_STATIC_DRAW);
            return new Upload(replacement, bufferId, source);
        } catch (RuntimeException | Error failure) { replacement.close(); throw failure; }
        finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            VertexBuffer.unbind();
        }
    }

    private static final class DrawStateOnly extends MeshData {
        DrawStateOnly(DrawState state) { super(null, state); }
        @Override public ByteBuffer vertexBuffer() { return null; }
        @Override public void close() { /* Header owns no vertex or index storage. */ }
    }

    /** Never drawable until every byte has transferred; the previous matched surface stays active. */
    static final class Upload implements AutoCloseable {
        static final int BYTES_PER_RENDER_CALL = 8 * 1024 * 1024;
        private VertexBuffer replacement;
        private final int bufferId;
        private ByteBuffer source;
        private int offset;

        private Upload(VertexBuffer replacement, int bufferId, ByteBuffer source) {
            this.replacement = replacement;
            this.bufferId = bufferId;
            this.source = source;
        }

        boolean complete() { return source != null && !source.hasRemaining(); }

        void advance() {
            RenderSystem.assertOnRenderThread();
            if (replacement == null || source == null) throw new IllegalStateException("surface upload is closed");
            if (complete()) return;
            int length = Math.min(BYTES_PER_RENDER_CALL, source.remaining());
            ByteBuffer slice = source.slice();
            slice.limit(length);
            try {
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) offset, slice);
                source.position(source.position() + length);
                offset += length;
            } finally { GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); }
        }

        VertexBuffer take() {
            RenderSystem.assertOnRenderThread();
            if (!complete() || replacement == null) throw new IllegalStateException("surface upload is incomplete");
            VertexBuffer ready = replacement;
            replacement = null;
            source = null;
            return ready;
        }

        @Override public void close() {
            RenderSystem.assertOnRenderThread();
            VertexBuffer abandoned = replacement;
            replacement = null;
            source = null;
            if (abandoned != null) abandoned.close();
        }
    }

    static final class PackedMesh implements AutoCloseable {
        private ByteBufferBuilder allocator;
        private MeshData data;
        private final int vertexCount;
        private final RingSurfaceMeshBytes identity;
        PackedMesh(ByteBufferBuilder allocator, MeshData data, int vertexCount) {
            this.allocator = allocator; this.data = data; this.vertexCount = vertexCount;
            identity = RingSurfaceMeshBytes.identify(data.vertexBuffer(), vertexCount,
                    DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        }
        int vertexCount() { return vertexCount; }
        RingSurfaceMeshBytes identity() { return identity; }
        @Override public void close() {
            try { if (data != null) data.close(); } finally {
                data = null;
                if (allocator != null) allocator.close();
                allocator = null;
            }
        }
    }
}
