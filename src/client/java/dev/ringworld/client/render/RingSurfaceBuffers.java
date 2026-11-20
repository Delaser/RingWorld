package dev.ringworld.client.render;

import java.nio.ByteBuffer;

/** Each upload allocates just its own triangles. Some drivers commit the entire allocation on first write. */
final class RingSurfaceBuffers implements AutoCloseable {
    static final int PAGE_VERTICES = RingSurfacePageIndices.PAGE_VERTICES;
    final RingSurfaceGpu.MeshPage[] pages;
    private int uploaded;

    RingSurfaceBuffers(int vertices) {
        pages = new RingSurfaceGpu.MeshPage[Math.ceilDiv(vertices, PAGE_VERTICES)];
    }

    boolean uploadNext(ByteBuffer source, int vertices, int[] uniqueCounts) {
        if (uploaded == vertices) return false;
        int stride = source.remaining() / vertices;
        int count = Math.min(PAGE_VERTICES, vertices - uploaded);
        ByteBuffer data = source.slice(source.position() + uploaded * stride, count * stride);
        int page = uploaded / PAGE_VERTICES;
        int unique = uniqueCounts[page];
        pages[page] = unique == 0 ? RingSurfaceGpu.uploadPage(data, null)
                : RingSurfaceGpu.uploadPage(data.slice(0, unique * stride),
                        data.slice(unique * stride, count * Short.BYTES));
        uploaded += count;
        return true;
    }

    @Override public void close() {
        for (var page : pages) if (page != null) page.close();
    }
}
