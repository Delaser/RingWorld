package dev.ringworld.world;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingSurfaceRowUpdatesTest {
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
