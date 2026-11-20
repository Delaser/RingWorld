package dev.ringworld.world;

/** Select contiguous changed scanlines independently for every mip of a reusable Atlas texture. */
public final class RingSurfaceRowUpdates {
    private RingSurfaceRowUpdates() { }

    @FunctionalInterface
    public interface Upload {
        void rows(int mip, int first, int count);
    }

    public static long transfer(long[][] resident, long[][] next, int width, Upload upload) {
        return new Cursor(resident, next, width).transferNext(Integer.MAX_VALUE, upload);
    }

    /** Resumes a partially populated, unpublished target. A null resident means every row is new. */
    public static final class Cursor {
        private final long[][] resident, next;
        private final int width;
        private int mip, row;

        public Cursor(long[][] resident, long[][] next, int width) {
            this.resident = resident;
            this.next = next;
            this.width = width;
        }

        public boolean done() { return mip == next.length; }

        public int transferNext(int budget, Upload upload) {
            if (budget < width) throw new IllegalArgumentException("budget must fit one full row");
            int texels = 0;
            while (!done()) {
                int stride = Math.max(1, width >> mip);
                int remaining = (budget - texels) / stride;
                if (remaining == 0) break;
                long[] after = next[mip];
                long[] before = resident == null ? null : resident[mip];
                while (row < after.length && before != null && after[row] == before[row]) row++;
                int first = row;
                int limit = row + Math.min(remaining, after.length - row);
                while (row < limit && (before == null || after[row] != before[row])) row++;
                if (row > first) {
                    upload.rows(mip, first, row - first);
                    texels += (row - first) * stride;
                }
                if (row == after.length) { mip++; row = 0; }
            }
            return texels;
        }
    }
}
