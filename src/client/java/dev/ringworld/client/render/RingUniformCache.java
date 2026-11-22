package dev.ringworld.client.render;

import java.nio.ByteBuffer;

/** Last successful contents of one render-thread-owned uniform buffer. */
public final class RingUniformCache {
    private final ByteBuffer previous;

    public RingUniformCache(int capacity) {
        previous = ByteBuffer.allocate(capacity).limit(0);
    }

    public boolean matches(ByteBuffer data) {
        return previous.equals(data);
    }

    public void record(ByteBuffer data) {
        int length = data.remaining();
        previous.clear().put(0, data, data.position(), length).limit(length);
    }
}
