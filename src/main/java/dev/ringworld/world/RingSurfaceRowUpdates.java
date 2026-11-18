package dev.ringworld.world;

/** Select contiguous changed scanlines independently for every mip of a reusable Atlas texture. */
public final class RingSurfaceRowUpdates {
    private RingSurfaceRowUpdates() { }

    @FunctionalInterface
    public interface Upload {
        void rows(int mip, int first, int count);
    }

    public static long transfer(long[][] resident, long[][] next, int width, Upload upload) {
        long texels = 0;
        for (int mip = 0; mip < next.length; mip++) {
            long[] before = resident[mip], after = next[mip];
            int row = 0;
            while (row < after.length) {
                if (after[row] == before[row]) {
                    row++;
                    continue;
                }
                int first = row++;
                while (row < after.length && after[row] != before[row]) row++;
                upload.rows(mip, first, row - first);
                texels += (long)(row - first) * Math.max(1, width >> mip);
            }
        }
        return texels;
    }
}
