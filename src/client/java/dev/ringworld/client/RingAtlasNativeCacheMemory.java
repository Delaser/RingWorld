package dev.ringworld.client;

import dev.ringworld.world.RingAtlasCacheWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.system.MemoryUtil;

/** Loader-neutral client allocator; cache storage does not require a GL context. */
public final class RingAtlasNativeCacheMemory {
    private static final AtomicLong LIVE_BYTES=new AtomicLong();
    private RingAtlasNativeCacheMemory() { }

    public static long liveBytes() { return LIVE_BYTES.get(); }

    public static RingAtlasCacheWriter.Memory allocate(int bytes) {
        ByteBuffer buffer=MemoryUtil.memAlloc(bytes);
        try { return new NativeMemory(buffer,bytes); }
        catch(RuntimeException | Error failure) { MemoryUtil.memFree(buffer);throw failure; }
    }

    private static final class NativeMemory implements RingAtlasCacheWriter.Memory {
        private final AtomicReference<ByteBuffer> buffer;
        private final int bytes;
        NativeMemory(ByteBuffer buffer,int bytes) {
            this.buffer=new AtomicReference<>(buffer);this.bytes=bytes;
            LIVE_BYTES.addAndGet(bytes);
        }
        @Override public ByteBuffer buffer() {
            return java.util.Objects.requireNonNull(buffer.get(),"closed native cache memory");
        }
        @Override public void close() {
            ByteBuffer owned=buffer.getAndSet(null);
            if(owned!=null) {
                try { MemoryUtil.memFree(owned); } finally { LIVE_BYTES.addAndGet(-bytes); }
            }
        }
    }
}
