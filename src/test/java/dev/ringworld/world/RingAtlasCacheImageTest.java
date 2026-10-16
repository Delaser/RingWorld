package dev.ringworld.world;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasCacheImageTest {
    static Stream<Arguments> encodings() {
        var result=new ArrayList<Arguments>();
        for(int step:new int[]{1,2,4,8}) for(int width:new int[]{128,416})
            for(boolean dense:new boolean[]{false,true}) for(boolean direct:new boolean[]{false,true})
                result.add(Arguments.of(step,width,dense,direct));
        return result.stream();
    }

    @ParameterizedTest @MethodSource("encodings")
    void exactExistingFileBytesAcrossLayoutsPresenceWaterAndStorage(int step,int width,boolean dense,
                                                                   boolean direct,@TempDir Path directory) throws Exception {
        var source=new RingTerrainAtlas(new RingGeometry(width,2048),-987654321L,step);
        for(int row=0;row<source.rows();row++)for(int col=0;col<source.columns();col++) {
            if(!dense && Math.floorMod(col*31+row*17,11)==0)continue;
            int color=(col*1103515245+row*12345)&0xffffff;
            source.putCell(col,row,(col*37+row*19)%384-64,color,(col+row)&15,
                    (col^row)%2==0?color:color^0xfedcba,(col+3*row)&15);
        }
        source.commitRevision(17);
        var tracker=new MemoryTracker(direct);
        try(var image=new RingAtlasCacheImage(source,tracker::allocate)) {
            source.copyCacheImageTo(image);
            Path expected=directory.resolve("independent-atlas.gz"),actual=directory.resolve("cache-image.gz");
            source.save(expected);image.save(actual);
            assertEquals(-1L,Files.mismatch(expected,actual));
            var loaded=RingTerrainAtlas.load(actual,source.geometry(),source.worldHash());
            assertEquals(source.contentFingerprint(),loaded.contentFingerprint());
            assertEquals(source.presentCount(),loaded.presentCount());
            assertEquals(source.revision(),loaded.revision());
        }
        assertEquals(0,tracker.liveBytes);
    }

    @Test
    void completeToPartialReuseOverwritesEveryChannel(@TempDir Path directory) throws Exception {
        var geometry=new RingGeometry(128,2048);var source=new RingTerrainAtlas(geometry,42,8);
        for(int row=0;row<source.rows();row++)for(int col=0;col<source.columns();col++)
            source.putCell(col,row,100,0xabcdef,15,0x123456,15);
        var tracker=new MemoryTracker(true);
        try(var image=new RingAtlasCacheImage(source,tracker::allocate)) {
            source.copyCacheImageTo(image);
            var partial=new RingTerrainAtlas(geometry,42,8);partial.putCell(3,4,-64,0x000001,0,0x000002,0);
            partial.commitRevision(8);partial.copyCacheImageTo(image);
            Path expected=directory.resolve("partial.gz"),actual=directory.resolve("reused.gz");
            partial.save(expected);image.save(actual);assertEquals(-1L,Files.mismatch(expected,actual));
        }
        assertEquals(0,tracker.liveBytes);
    }

    @Test
    void incompatibleAndClosedCapturesCannotTouchMemory() {
        var source=new RingTerrainAtlas(new RingGeometry(128,2048),42,8);var tracker=new MemoryTracker(true);
        var image=new RingAtlasCacheImage(source,tracker::allocate);
        assertThrows(IllegalArgumentException.class,()->new RingTerrainAtlas(source.geometry(),43,8).copyCacheImageTo(image));
        assertThrows(IllegalArgumentException.class,()->new RingTerrainAtlas(source.geometry(),42,4).copyCacheImageTo(image));
        image.close();image.close();assertEquals(0,tracker.liveBytes);
        assertThrows(IllegalArgumentException.class,()->source.copyCacheImageTo(image));
        assertThrows(IllegalStateException.class,()->image.cellHeight(0,0));
    }

    @Test
    void badAllocatorRangesAreReleasedBeforeFailure() {
        var source=new RingTerrainAtlas(new RingGeometry(128,2048),42,8);
        for(int bad=0;bad<3;bad++) {
            int mode=bad;var closed=new AtomicInteger();
            assertThrows(IllegalArgumentException.class,()->new RingAtlasCacheImage(source,bytes->new RingAtlasCacheWriter.Memory() {
                final ByteBuffer buffer=mode==0?ByteBuffer.allocate(bytes-1):mode==1?
                        ByteBuffer.allocate(bytes).asReadOnlyBuffer():ByteBuffer.allocate(bytes).position(1);
                public ByteBuffer buffer(){return buffer;}
                public void close(){closed.incrementAndGet();}
            }));
            assertEquals(1,closed.get());
        }
    }

    @Test
    void directCaptureAllocatesNoCellSizedJavaArrayAndDropsIdleStorage(@TempDir Path directory) {
        var bean=assertInstanceOf(com.sun.management.ThreadMXBean.class,ManagementFactory.getThreadMXBean());
        if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        var warm=new RingTerrainAtlas(new RingGeometry(128,2048),1,8);var tracker=new MemoryTracker(true);
        try(var image=new RingAtlasCacheImage(warm,tracker::allocate)){warm.copyCacheImageTo(image);}
        var source=new RingTerrainAtlas(new RingGeometry(256,16384),42,1);
        var tasks=new ArrayDeque<Runnable>();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,image)->{},tracker::allocate);
        long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);
        writer.submit(directory.resolve("cache.gz"),source);
        long allocated=bean.getThreadAllocatedBytes(id)-before;
        assertTrue(allocated<524_288,"Cache capture allocated cell-sized heap storage: "+allocated);
        tasks.remove().run();assertEquals((long)source.cellCount()*12,tracker.liveBytes);
        writer.discardReusableSnapshots();assertEquals(0,tracker.liveBytes);
    }

    @Test
    void activeNativeStorageSurvivesSessionDiscardButCannotReturnToIdlePool(@TempDir Path directory) throws Exception {
        var tracker=new MemoryTracker(true);var tasks=new ArrayDeque<Runnable>();
        var started=new CountDownLatch(1);var finish=new CountDownLatch(1);
        var writer=new RingAtlasCacheWriter(tasks::add,(path,image)->{
            started.countDown();
            try {assertTrue(finish.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}
            assertEquals(80,image.cellHeight(0,0));image.save(path);
        },tracker::allocate);
        var source=new RingTerrainAtlas(new RingGeometry(128,2048),42,8);source.putCell(0,0,80,0xabcdef);
        var done=writer.submit(directory.resolve("saved.gz"),source);var worker=new Thread(tasks.remove());worker.start();
        try {assertTrue(started.await(10,TimeUnit.SECONDS));writer.discardReusableSnapshots();assertTrue(tracker.liveBytes>0);}
        finally {finish.countDown();}
        worker.join(10_000);assertFalse(worker.isAlive());done.join();assertFalse(writer.hasReusableSnapshot());
        assertEquals(0,tracker.liveBytes);
        assertEquals(80,RingTerrainAtlas.load(directory.resolve("saved.gz"),source.geometry(),42).cellHeight(0,0));
    }

    @Test
    void rejectionAndFatalFailureReleaseEveryOwnedAllocation(@TempDir Path directory) {
        var source=new RingTerrainAtlas(new RingGeometry(128,2048),42,8);var tracker=new MemoryTracker(true);
        var rejected=new RingAtlasCacheWriter(task->{throw new java.util.concurrent.RejectedExecutionException();},
                (path,image)->{},tracker::allocate);
        assertThrows(java.util.concurrent.RejectedExecutionException.class,()->rejected.submit(directory.resolve("rejected.gz"),source));
        assertEquals(0,tracker.liveBytes);
        var tasks=new ArrayDeque<Runnable>();
        var fatal=new RingAtlasCacheWriter(tasks::add,(path,image)->{throw new AssertionError("deliberate");},tracker::allocate);
        var first=fatal.submit(directory.resolve("first.gz"),source);var second=fatal.submit(directory.resolve("second.gz"),source);
        assertThrows(AssertionError.class,()->tasks.remove().run());
        assertTrue(first.isCompletedExceptionally());assertTrue(second.isCompletedExceptionally());
        assertEquals(0,tracker.liveBytes);
    }

    @Test
    void failedReplacementAllocationPreservesTheEarlierQueuedSave(@TempDir Path directory) throws Exception {
        var source=new RingTerrainAtlas(new RingGeometry(128,2048),42,8);
        source.putCell(0,0,80,0x123456);
        var tracker=new MemoryTracker(true);var tasks=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,image)->image.save(path),bytes->{
            if(calls.incrementAndGet()==2)throw new OutOfMemoryError("deliberate allocation failure");
            return tracker.allocate(bytes);
        });
        Path path=directory.resolve("final.gz");var earlier=writer.submit(path,source);
        var other=new RingTerrainAtlas(source.geometry(),43,8);
        assertThrows(OutOfMemoryError.class,()->writer.submit(path,other));
        assertFalse(earlier.isDone());assertEquals(1,tasks.size());tasks.remove().run();earlier.join();
        assertEquals(80,RingTerrainAtlas.load(path,source.geometry(),42).cellHeight(0,0));
        writer.discardReusableSnapshots();assertEquals(0,tracker.liveBytes);
    }

    /** Counts explicit ownership release; real LWJGL deallocation is a separate native gate. */
    private static final class MemoryTracker {
        final boolean direct;
        volatile long liveBytes;
        MemoryTracker(boolean direct) { this.direct=direct; }
        synchronized RingAtlasCacheWriter.Memory allocate(int bytes) {
            ByteBuffer data=direct?ByteBuffer.allocateDirect(bytes):ByteBuffer.allocate(bytes);
            liveBytes+=bytes;
            return new RingAtlasCacheWriter.Memory() {
                private ByteBuffer buffer=data;
                public ByteBuffer buffer(){return java.util.Objects.requireNonNull(buffer);}
                public void close(){synchronized(MemoryTracker.this){if(buffer!=null){buffer=null;liveBytes-=bytes;}}}
            };
        }
    }
}
