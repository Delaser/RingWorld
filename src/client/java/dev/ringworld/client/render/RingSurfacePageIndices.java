package dev.ringworld.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Worker-side compaction of an already packed POSITION_TEX_COLOR triangle list.
 * Offsets used by layer culling remain offsets in the original triangle order. */
final class RingSurfacePageIndices {
    static final int PAGE_VERTICES = 6 * 24_576;
    static final int STRIDE = 24;
    private static final int MAX_UNIQUE = 65_535;

    private RingSurfacePageIndices() {}

    /** Zero denotes a regular non-indexed page. The two streams fit inside the original page. */
    static int[] pack(ByteBuffer data, int vertices, int stride) {
        int[] counts = new int[Math.ceilDiv(vertices, PAGE_VERTICES)];
        if (vertices == 0 || stride != STRIDE) return counts;
        ByteBuffer bytes = data.slice().order(ByteOrder.nativeOrder());
        int maxUnique = Math.min(vertices, MAX_UNIQUE);
        int[] table = new int[Integer.highestOneBit(maxUnique * 2 - 1) << 1];
        int[] sources = new int[maxUnique];
        short[] indices = new short[Math.min(vertices, PAGE_VERTICES)];
        for (int page = 0; page < counts.length; page++) {
            int first = page * PAGE_VERTICES;
            int length = Math.min(PAGE_VERTICES, vertices - first);
            Arrays.fill(table, 0);
            int unique = 0;
            for (int i = 0; i < length; i++) {
                int origin = (first + i) * stride;
                long a = bytes.getLong(origin);
                long b = bytes.getLong(origin + 8);
                long c = bytes.getLong(origin + 16);
                long hash = (a ^ Long.rotateLeft(b, 21) ^ Long.rotateLeft(c, 43)) * 0x9e3779b97f4a7c15L;
                int slot = (int)(hash ^ (hash >>> 32)) & (table.length - 1);
                while (table[slot] != 0) {
                    int previous = sources[table[slot] - 1];
                    if (bytes.getLong(previous) == a && bytes.getLong(previous + 8) == b
                            && bytes.getLong(previous + 16) == c) break;
                    slot = (slot + 1) & (table.length - 1);
                }
                if (table[slot] == 0) {
                    if (unique == MAX_UNIQUE) {
                        unique = 0;
                        break;
                    }
                    sources[unique++] = origin;
                    table[slot] = unique;
                }
                indices[i] = (short)(table[slot] - 1);
            }
            if (unique == 0 || unique * stride + length * Short.BYTES >= length * stride) continue;
            counts[page] = unique;
            int dest = first * stride;
            // First-use order means each destination precedes or equals its source.
            for (int i = 0; i < unique; i++) {
                int source = sources[i];
                for (int j = 0; j < stride; j += Long.BYTES) bytes.putLong(dest + j, bytes.getLong(source + j));
                dest += stride;
            }
            bytes.slice(dest, length * Short.BYTES).order(ByteOrder.nativeOrder()).asShortBuffer().put(indices, 0, length);
        }
        return counts;
    }
}
