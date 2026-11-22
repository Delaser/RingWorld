package dev.ringworld.client.render;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingUniformCacheTest {
    @Test void comparesOnlyUploadedBytesAndKeepsEachViewsContentsIndependent() {
        var main = new RingUniformCache(16);
        var alternate = new RingUniformCache(16);
        var data = ByteBuffer.allocateDirect(24).position(4).limit(20);
        assertFalse(main.matches(data));
        main.record(data);
        assertEquals(4, data.position());
        assertTrue(main.matches(data));
        assertFalse(alternate.matches(data));
        data.put(0, (byte)1);
        assertTrue(main.matches(data));
        data.put(19, (byte)1);
        assertFalse(main.matches(data));
        alternate.record(data);
        assertTrue(alternate.matches(data));
        data.put(19, (byte)0);
        assertTrue(main.matches(data));
        assertFalse(alternate.matches(data));
    }
}
