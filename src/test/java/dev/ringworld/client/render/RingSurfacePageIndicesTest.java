package dev.ringworld.client.render;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class RingSurfacePageIndicesTest {
    @Test void expandsToTheExactByteStreamAcrossPagesIncludingDifferentUvsAndColours() {
        int page = RingSurfacePageIndices.PAGE_VERTICES;
        int count = page + 36;
        int stride = RingSurfacePageIndices.STRIDE;
        byte[] original = new byte[count * stride];
        ByteBuffer source = ByteBuffer.wrap(original).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            int j = i % 54;
            source.putLong(j % 3);
            source.putLong(j % 9);
            source.putLong(j);
        }
        // Give the short final page six repeating vertices.
        for (int i = page + 6; i < count; i++)
            System.arraycopy(original, (page + (i - page) % 6) * stride, original, i * stride, stride);
        ByteBuffer packed = ByteBuffer.wrap(original.clone()).order(ByteOrder.nativeOrder());
        int[] unique = RingSurfacePageIndices.pack(packed, count, stride);
        assertArrayEquals(new int[]{54, 6}, unique);
        ByteBuffer expanded = ByteBuffer.allocate(original.length);
        for (int i = 0; i < count; i++) {
            int first = i / page * page;
            int at = (first + unique[i / page]) * stride + (i - first) * Short.BYTES;
            int index = Short.toUnsignedInt(packed.getShort(at));
            expanded.put(packed.slice((first + index) * stride, stride));
        }
        assertArrayEquals(original, expanded.array());
    }

    @Test void preservesUnsignedIndicesAndRespectsTheBufferPosition() {
        int unique = 65_535;
        int stride = RingSurfacePageIndices.STRIDE;
        int count = 2 * unique;
        ByteBuffer data = ByteBuffer.allocate(16 + stride * count).order(ByteOrder.nativeOrder());
        data.putLong(0, -1L);
        data.position(16);
        for (int i = 0; i < count; i++) data.putInt(16 + i * stride, i % unique);
        assertEquals(unique, RingSurfacePageIndices.pack(data, count, stride)[0]);
        int indices = 16 + unique * stride;
        for (int i = 0; i < count; i++) {
            assertEquals(i % unique, Short.toUnsignedInt(data.getShort(indices + i * Short.BYTES)));
        }
        assertEquals(16, data.position());
        assertEquals(-1L, data.getLong(0));
    }

    @Test void keepsRawPagesWhenIndexingWouldOverflowOrIncreaseSize() {
        for (int count : new int[]{3, 65_538}) {
            var bytes = ByteBuffer.allocate(count * RingSurfacePageIndices.STRIDE).order(ByteOrder.nativeOrder());
            for (int i = 0; i < count; i++) bytes.putInt(i * RingSurfacePageIndices.STRIDE, i);
            byte[] original = bytes.array().clone();
            assertEquals(0, RingSurfacePageIndices.pack(bytes, count, RingSurfacePageIndices.STRIDE)[0]);
            assertArrayEquals(original, bytes.array());
        }
    }
}
