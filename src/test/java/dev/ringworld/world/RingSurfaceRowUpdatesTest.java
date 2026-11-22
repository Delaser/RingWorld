package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingSurfaceRowUpdatesTest {
    @Test void onlySeedMatchingMipChainsWithSparseChanges() {
        long[][] displayed = {{1,2,3,4}, {1,2}, {1}};
        assertEquals(0, RingSurfaceRowUpdates.smallPatchTexels(displayed, 8, displayed, 8));
        long[][] sparse = {{1,9,3,4}, {1,3}, {1}};
        assertEquals(12, RingSurfaceRowUpdates.smallPatchTexels(displayed, 8, sparse, 8));
        assertEquals(-1, RingSurfaceRowUpdates.smallPatchTexels(null, 8, sparse, 8));
        assertEquals(-1, RingSurfaceRowUpdates.smallPatchTexels(displayed, 16, sparse, 8));
        assertEquals(-1, RingSurfaceRowUpdates.smallPatchTexels(displayed, 8, new long[][]{{1,2,3,4}}, 8));
        assertEquals(-1, RingSurfaceRowUpdates.smallPatchTexels(displayed, 8, new long[][]{{1,2,3,4},{1},{1}}, 8));
        long[][] dense = {{9,9,9,9}, {1,2}, {1}};
        assertEquals(-1, RingSurfaceRowUpdates.smallPatchTexels(displayed, 8, dense, 8));
        assertEquals(-1, RingSurfaceRowUpdates.smallPatchTexels(displayed, 524288, sparse, 524288));
    }

    @Test void aFreshTargetResumesAtTheNextRowAndNeverExceedsItsBudget() {
        long[][] pixels = {new long[5], new long[3], new long[1]};
        var cursor = new RingSurfaceRowUpdates.Cursor(null, pixels, 16);
        List<String> ranges = new ArrayList<>();
        List<Integer> batches = new ArrayList<>();
        while (!cursor.done()) {
            batches.add(cursor.transferNext(32, (mip, first, count) -> ranges.add(mip + "/" + first + "/" + count)));
        }
        assertEquals(List.of(32, 32, 32, 12), batches);
        assertEquals(List.of("0/0/2", "0/2/2", "0/4/1", "1/0/2", "1/2/1", "2/0/1"), ranges);
    }

    @Test void comparesWithTheResidentTargetAndRespectsEveryMip() {
        long[][] resident = {{1,2,3,4,5,6}, {7,8,9}, {10}};
        long[][] next = {{1,20,30,4,5,60}, {70,8,9}, {10}};
        List<String> uploads = new ArrayList<>();
        long texels = RingSurfaceRowUpdates.transfer(resident, next, 16, (level, row, count) -> {
            uploads.add(level + "/" + row + "/" + count);
            System.arraycopy(next[level], row, resident[level], row, count);
        });
        assertEquals(List.of("0/1/2", "0/5/1", "1/0/1"), uploads);
        assertEquals(56, texels);
        assertEquals(0, RingSurfaceRowUpdates.transfer(resident, next, 16, (level, row, count) -> fail()));
    }
}
