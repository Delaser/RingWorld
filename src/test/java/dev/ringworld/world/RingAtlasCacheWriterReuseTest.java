package dev.ringworld.world;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasCacheWriterReuseTest {
    private static final RingGeometry GEOMETRY=new RingGeometry(128,2048);

    @Test
    void activeEncodingNeverLendsItsStorageToPendingOwnerUpdates(@TempDir Path directory) throws Exception {
        var tasks=new ArrayDeque<Runnable>();
        var started=new CountDownLatch(1);var finish=new CountDownLatch(1);
        var calls=new AtomicInteger();var snapshots=new ArrayList<RingTerrainAtlas>();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,snapshot)->{
            snapshots.add(snapshot);
            if(calls.incrementAndGet()==1) {
                started.countDown();
                try {if(!finish.await(10,TimeUnit.SECONDS))throw new AssertionError("Encoding release timed out");}
                catch(InterruptedException exception){throw new AssertionError(exception);}
                assertEquals(80,snapshot.cellHeight(0,0));
                assertEquals(1,snapshot.revision());
            }
            snapshot.save(path);
        });
        var source=new RingTerrainAtlas(GEOMETRY,42,8);source.putCell(0,0,80,0x123456);source.commitRevision(1);
        Path path=directory.resolve("atlas.gz");var first=writer.submit(path,source);
        var worker=new Thread(tasks.remove());worker.start();
        try {
            assertTrue(started.await(10,TimeUnit.SECONDS));
            source.putCell(0,0,96,0xabcdef);source.commitRevision(2);writer.submit(path,source);
            source.putCell(0,0,112,0xff00ff);source.commitRevision(3);writer.submit(path,source);
            source.putCell(0,0,128,0x00ff00);source.commitRevision(4);
        } finally {finish.countDown();}
        worker.join(10_000);assertFalse(worker.isAlive());first.get(10,TimeUnit.SECONDS);
        assertEquals(2,calls.get());assertNotSame(snapshots.get(0),snapshots.get(1));
        var saved=RingTerrainAtlas.load(path,GEOMETRY,42);
        assertEquals(112,saved.cellHeight(0,0));assertEquals(3,saved.revision());
    }

    @Test
    void idleReuseAndSessionDiscardRespectGenerations(@TempDir Path directory) {
        var tasks=new ArrayDeque<Runnable>();var snapshots=new ArrayList<RingTerrainAtlas>();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,snapshot)->snapshots.add(snapshot));
        var source=new RingTerrainAtlas(GEOMETRY,42,8);Path path=directory.resolve("atlas.gz");
        writer.submit(path,source);tasks.remove().run();assertTrue(writer.hasReusableSnapshot());
        writer.submit(path,source);tasks.remove().run();assertSame(snapshots.get(0),snapshots.get(1));
        writer.discardReusableSnapshots();assertFalse(writer.hasReusableSnapshot());
        writer.submit(path,source);writer.discardReusableSnapshots();tasks.remove().run();
        assertFalse(writer.hasReusableSnapshot(),"An earlier-session completion must not restore idle storage");
        assertNotSame(snapshots.get(1),snapshots.get(2));
        writer.submit(path,source);tasks.remove().run();assertNotSame(snapshots.get(2),snapshots.get(3));
        assertTrue(writer.hasReusableSnapshot());
    }

    @Test
    void changedWorldIdentityCannotReuseIncompatibleArrays(@TempDir Path directory) {
        var tasks=new ArrayDeque<Runnable>();var snapshots=new ArrayList<RingTerrainAtlas>();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,snapshot)->snapshots.add(snapshot));
        for(var source:new RingTerrainAtlas[]{new RingTerrainAtlas(GEOMETRY,42,8),new RingTerrainAtlas(GEOMETRY,43,8),
                new RingTerrainAtlas(GEOMETRY,43,4),new RingTerrainAtlas(new RingGeometry(416,2048),43,4)}) {
            writer.submit(directory.resolve("atlas.gz"),source);tasks.remove().run();
            var actual=snapshots.getLast();assertEquals(source.geometry(),actual.geometry());
            assertEquals(source.worldHash(),actual.worldHash());assertEquals(source.sampleStep(),actual.sampleStep());
        }
        for(int i=1;i<snapshots.size();i++)assertNotSame(snapshots.get(i-1),snapshots.get(i));
    }

    @Test
    void failedWriteCannotPublishAReusableSnapshot(@TempDir Path directory) {
        var tasks=new ArrayDeque<Runnable>();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,snapshot)->{throw new java.io.IOException("deliberate failure");});
        var future=writer.submit(directory.resolve("atlas.gz"),new RingTerrainAtlas(GEOMETRY,42,8));
        tasks.remove().run();assertTrue(future.isCompletedExceptionally());assertFalse(writer.hasReusableSnapshot());
    }

    @Test
    void repeatedWarmSubmissionsAvoidPerWriteCellArrayAllocation(@TempDir Path directory) {
        var bean=assertInstanceOf(com.sun.management.ThreadMXBean.class,ManagementFactory.getThreadMXBean());
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        var tasks=new ArrayDeque<Runnable>();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,snapshot)->{});
        var source=new RingTerrainAtlas(GEOMETRY,42,8);Path path=directory.resolve("atlas.gz");
        for(int i=0;i<100;i++){writer.submit(path,source);tasks.remove().run();}
        long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);
        for(int i=0;i<200;i++){writer.submit(path,source);tasks.remove().run();}
        long allocated=bean.getThreadAllocatedBytes(id)-before;
        assertTrue(allocated<262_144,"Reusable cache capture allocated "+allocated+" bytes");
        System.out.println("Reusable cache capture: "+allocated+" bytes for 200 writes (sink excludes disk allocation)");
    }

    @Test
    void fatalWorkerFailureCompletesQueuedFuturesAndAllowsRetry(@TempDir Path directory) {
        var tasks=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
        var writer=new RingAtlasCacheWriter(tasks::add,(path,snapshot)->{
            if(calls.incrementAndGet()==1)throw new AssertionError("deliberate worker failure");
        });
        var source=new RingTerrainAtlas(GEOMETRY,42,8);
        var first=writer.submit(directory.resolve("first.gz"),source);
        var pending=writer.submit(directory.resolve("second.gz"),source);
        assertThrows(AssertionError.class,()->tasks.remove().run());
        assertTrue(first.isCompletedExceptionally());assertTrue(pending.isCompletedExceptionally());
        assertFalse(writer.hasReusableSnapshot());
        var retry=writer.submit(directory.resolve("first.gz"),source);
        tasks.remove().run();assertTrue(retry.isDone());assertFalse(retry.isCompletedExceptionally());
    }

    @Test
    void executorRejectionDoesNotStrandTheDrainState(@TempDir Path directory) {
        var tasks=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
        var writer=new RingAtlasCacheWriter(task->{
            if(calls.incrementAndGet()==1)throw new java.util.concurrent.RejectedExecutionException("deliberate rejection");
            tasks.add(task);
        },(path,snapshot)->{});
        var source=new RingTerrainAtlas(GEOMETRY,42,8);
        assertThrows(java.util.concurrent.RejectedExecutionException.class,()->writer.submit(directory.resolve("atlas.gz"),source));
        var retry=writer.submit(directory.resolve("atlas.gz"),source);
        tasks.remove().run();assertTrue(retry.isDone());assertFalse(retry.isCompletedExceptionally());
    }
}
