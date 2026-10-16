package dev.ringworld.world;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class RingDisplayStorageReuseTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2048);

    static Stream<Arguments> reductions() {
        return Stream.of(Arguments.of(RingLodQuality.LOW, 1), Arguments.of(RingLodQuality.LOW, 2),
                Arguments.of(RingLodQuality.LOW, 4), Arguments.of(RingLodQuality.MEDIUM, 1));
    }

    @ParameterizedTest
    @MethodSource("reductions")
    void replacementDisplayContentMatchesUnchangedIndependentCopyApi(RingLodQuality quality, int step) {
        var source = complete(step, 42);
        var target = new RingTerrainAtlas(GEOMETRY, 42, quality.sampleStep());
        long previous = target.contentFingerprint();
        for (int pass = 0; pass < 3; pass++) {
            int scale = quality.sampleStep()/step;
            for(int z=0;z<scale;z++)for(int x=0;x<scale;x++)
                source.putCell(x,z,110+pass,0x123456+pass,pass,0xabcdef-pass,15-pass);
            quality.copyDisplaySnapshotTo(source, target);
            equivalent(quality.displaySnapshot(source), target);
            assertNotEquals(previous,target.contentFingerprint());
            previous=target.contentFingerprint();
        }
        var independent = quality.displaySnapshot(source);
        source.putCell(1, 1, 200, 0xff00ff, 15, 0xff00ff, 0);
        assertEquals(independent.contentFingerprint(), target.contentFingerprint());
    }

    @Test
    void invalidReducedTargetsFailWithoutMutatingThem() {
        var source = complete(1, 42);
        for (var target : new RingTerrainAtlas[]{new RingTerrainAtlas(GEOMETRY, 43, 2),
                new RingTerrainAtlas(GEOMETRY, 42, 8), new RingTerrainAtlas(new RingGeometry(416,2048),42,2)}) {
            target.putCell(0, 0, 99, 0xabcdef);
            long fingerprint = target.contentFingerprint();
            assertThrows(IllegalArgumentException.class, () -> RingLodQuality.MEDIUM.copyDisplaySnapshotTo(source, target));
            assertEquals(fingerprint, target.contentFingerprint());
        }
        var target = new RingTerrainAtlas(GEOMETRY,42,2);
        source.clear();
        assertThrows(IllegalArgumentException.class, () -> RingLodQuality.MEDIUM.copyDisplaySnapshotTo(source,target));
        assertThrows(IllegalArgumentException.class, () -> RingLodQuality.HIGH.copyDisplaySnapshotTo(source,source));
    }

    @Test
    void displayAndMipStorageStayUnderTheExclusiveWorkspaceLease() {
        var workspace = new RingSurfaceBuildWorkspace();
        var source = complete(1,42);
        RingTerrainAtlas reduced;
        int[] mip;
        try (var lease = workspace.capture(source)) {
            reduced = lease.displayAtlas(RingLodQuality.MEDIUM);
            equivalent(RingLodQuality.MEDIUM.displaySnapshot(source), reduced);
            assertSame(lease.atlas(), lease.displayAtlas(RingLodQuality.HIGH));
            assertSame(lease.atlas(), lease.displayAtlas(null));
            mip = lease.mipPixels(1, 128);
            assertNotSame(mip, lease.mipPixels(2,128));
            assertThrows(IllegalArgumentException.class, () -> lease.mipPixels(0,128));
            assertThrows(IllegalArgumentException.class, () -> lease.mipPixels(32,128));
            assertThrows(IllegalArgumentException.class, () -> lease.mipPixels(1,0));
            assertThrows(IllegalStateException.class, () -> workspace.capture(source));
        }
        source.putCell(1,1,200,0xff00ff,15,0x123456,12);
        try (var lease = workspace.capture(source)) {
            assertSame(reduced,lease.displayAtlas(RingLodQuality.MEDIUM));
            equivalent(RingLodQuality.MEDIUM.displaySnapshot(source),reduced);
            assertSame(mip,lease.mipPixels(1,128));
            assertNotSame(mip,lease.mipPixels(1,256));
        }
        source.clear();
        try (var lease = workspace.capture(source)) {
            assertSame(lease.atlas(),lease.displayAtlas(RingLodQuality.MEDIUM),"Partial data must never reuse a previous complete display");
        }
        var closed = workspace.capture(source); closed.close();
        assertThrows(IllegalStateException.class, () -> closed.displayAtlas(RingLodQuality.MEDIUM));
        assertThrows(IllegalStateException.class, () -> closed.mipPixels(1,128));
    }

    @ParameterizedTest
    @CsvSource({"1,1","1,17","17,1","2,2","3,5","16,8","128,16"})
    void reusableMipOutputMatchesUnchangedOriginalAlgorithm(int width,int height) {
        int[] source = new int[width*height];
        int[] target = new int[Math.max(1,width>>1)*Math.max(1,height>>1)];
        var random = new Random(width*123L+height);
        for(int pass=0;pass<3;pass++) {
            for(int i=0;i<source.length;i++) source[i]=random.nextInt();
            RingSurfaceLod.buildNextMipRgbLight(source,width,height,target);
            assertArrayEquals(RingSurfaceLod.buildNextMipRgbLight(source,width,height),target);
        }
    }

    @Test
    void invalidMipTargetsCannotOverwriteInputs() {
        int[] source = {0xff123456,0xffabcdef,0x00112233,0x12345678};
        int[] before=source.clone();
        assertThrows(IllegalArgumentException.class, () -> RingSurfaceLod.buildNextMipRgbLight(source,2,2,source));
        assertArrayEquals(before,source);
        assertThrows(IllegalArgumentException.class, () -> RingSurfaceLod.buildNextMipRgbLight(source,2,2,new int[2]));
        assertThrows(IllegalArgumentException.class, () -> RingSurfaceLod.buildNextMipRgbLight(source,0,2,new int[1]));
    }

    private static RingTerrainAtlas complete(int step,long hash) {
        var result = new RingTerrainAtlas(GEOMETRY,hash,step);
        for(int z=0;z<result.rows();z++)for(int x=0;x<result.columns();x++)
            result.putCell(x,z,(x*37+z*19)%384-64,(x*1103515245+z*12345)^0x12abcd,
                    (x^z)&15,0xabcdef,(x+3*z)&15);
        return result;
    }

    @Test
    void warmReducedDisplayAndMipUseDoesNotAllocateCellArrays() {
        var bean=assertInstanceOf(com.sun.management.ThreadMXBean.class,java.lang.management.ManagementFactory.getThreadMXBean());
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        var workspace=new RingSurfaceBuildWorkspace();var source=complete(1,42);
        for(int i=0;i<20;i++)try(var lease=workspace.capture(source)) {
            lease.displayAtlas(RingLodQuality.LOW);lease.mipPixels(1,4096);
        }
        long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);
        for(int i=0;i<20;i++)try(var lease=workspace.capture(source)) {
            assertTrue(lease.displayAtlas(RingLodQuality.LOW).isComplete());lease.mipPixels(1,4096);
        }
        long allocated=bean.getThreadAllocatedBytes(id)-before;
        assertTrue(allocated<65_536,"Warm display/mip storage allocated "+allocated+" bytes");
        System.out.println("Reusable display/mip storage: "+allocated+" bytes for 20 complete builds");
    }

    private static void equivalent(RingTerrainAtlas expected,RingTerrainAtlas actual) {
        assertEquals(expected.presentCount(),actual.presentCount());
        assertEquals(expected.presentChunkCount(),actual.presentChunkCount());
        assertEquals(expected.contentFingerprint(),actual.contentFingerprint());
        assertEquals(expected.surfaceHeightFingerprint(),actual.surfaceHeightFingerprint());
        for(int z=0;z<expected.tileRows();z++)for(int x=0;x<expected.tileColumns();x++)
            assertArrayEquals(expected.encodeTile(x,z),actual.encodeTile(x,z));
    }
}
