package dev.ringworld.world;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingSurfaceMeshBytesTest {
    @Test
    void heapDirectAndReadOnlyViewsHaveTheSameIdentityWithoutMovingTheirRanges() {
        ByteBuffer heap = ByteBuffer.allocate(32);
        for (int i=0;i<32;i++) heap.put(i,(byte)(i*19));
        heap.position(4).limit(28);
        ByteBuffer direct = ByteBuffer.allocateDirect(24);
        direct.put(heap.duplicate()).flip();
        var expected = RingSurfaceMeshBytes.identify(heap,1,24);
        assertEquals(expected,RingSurfaceMeshBytes.identify(direct,1,24));
        assertEquals(expected,RingSurfaceMeshBytes.identify(heap.asReadOnlyBuffer(),1,24));
        assertEquals(4,heap.position()); assertEquals(28,heap.limit());
        assertEquals(0,direct.position()); assertEquals(24,direct.limit());
    }

    @Test
    void everyPackedPositionUvAndColorByteParticipates() {
        ByteBuffer bytes = ByteBuffer.allocate(24);
        var baseline = RingSurfaceMeshBytes.identify(bytes,1,24);
        for (int i=0;i<24;i++) {
            bytes.put(i,(byte)1);
            assertNotEquals(baseline,RingSurfaceMeshBytes.identify(bytes,1,24),"byte "+i);
            bytes.put(i,(byte)0);
        }
    }

    @Test
    void identityDoesNotRetainOrFollowMutableNativeStorage() {
        ByteBuffer bytes=ByteBuffer.allocateDirect(24);
        var before=RingSurfaceMeshBytes.identify(bytes,1,24);
        bytes.putInt(0,123);
        assertNotEquals(before,RingSurfaceMeshBytes.identify(bytes,1,24));
        assertEquals(new RingSurfaceMeshBytes(1,24,before.sha256()),before);
    }

    @Test
    void bufferCapacityOutsideTheSubmittedRangeCannotChangeIdentity() {
        ByteBuffer bytes=ByteBuffer.allocate(32).position(4).limit(28);
        var before=RingSurfaceMeshBytes.identify(bytes,1,24);
        bytes.put(0,(byte)1); bytes.limit(32).put(31,(byte)1).limit(28);
        assertEquals(before,RingSurfaceMeshBytes.identify(bytes,1,24));
    }

    @Test
    void drawStateIsPartOfTheIdentityEvenForTheSameBytes() {
        ByteBuffer bytes=ByteBuffer.allocate(24);
        assertNotEquals(RingSurfaceMeshBytes.identify(bytes,1,24),RingSurfaceMeshBytes.identify(bytes,2,12));
    }

    @Test
    void rawFloatingPointBitsArePreserved() {
        ByteBuffer bytes=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0,0x7fc00001);
        var before=RingSurfaceMeshBytes.identify(bytes,1,24);
        bytes.putInt(0,0x7fc00002);
        assertNotEquals(before,RingSurfaceMeshBytes.identify(bytes,1,24));
        bytes.putInt(0,0);
        before=RingSurfaceMeshBytes.identify(bytes,1,24);
        bytes.putInt(0,0x80000000);
        assertNotEquals(before,RingSurfaceMeshBytes.identify(bytes,1,24));
    }

    @Test
    void reuseRequiresTheSameWorldAndLiveGpuOwnership() {
        var identity=RingSurfaceMeshBytes.identify(ByteBuffer.allocate(24),1,24);
        assertTrue(identity.canReuse(true,true,identity));
        assertFalse(identity.canReuse(false,true,identity));
        assertFalse(identity.canReuse(true,false,identity));
        assertFalse(identity.canReuse(true,true,null));
        ByteBuffer different=ByteBuffer.allocate(24).putInt(0,1);
        assertFalse(identity.canReuse(true,true,RingSurfaceMeshBytes.identify(different,1,24)));
    }

    @Test
    void invalidRangesAndDrawStatesFailClosed() {
        assertThrows(IllegalArgumentException.class,()->RingSurfaceMeshBytes.identify(ByteBuffer.allocate(23),1,24));
        assertThrows(IllegalArgumentException.class,()->RingSurfaceMeshBytes.identify(ByteBuffer.allocate(25),1,24));
        assertThrows(IllegalArgumentException.class,()->RingSurfaceMeshBytes.identify(ByteBuffer.allocate(0),0,24));
        assertThrows(ArithmeticException.class,()->RingSurfaceMeshBytes.identify(ByteBuffer.allocate(24),Integer.MAX_VALUE,24));
        assertThrows(IllegalArgumentException.class,()->new RingSurfaceMeshBytes(1,24,"invalid"));
        assertThrows(NullPointerException.class,()->RingSurfaceMeshBytes.identify(null,1,24));
    }

    @Test
    void unsampledAtlasMutationChangesTheOldKeyButNotTheActualMeshStream() {
        var geometry=new RingGeometry(128,2048);
        var atlas=new RingTerrainAtlas(geometry,42L,1);
        for(int z=0;z<128;z++) for(int x=0;x<2048;x++) atlas.putCell(x,z,64,0x112233);
        var profile=RingRenderProfile.create(geometry,96,256,16,16);
        var before=stream(atlas,profile);
        long oldKey=atlas.surfaceHeightFingerprint();
        atlas.putCell(7,64,90,0x998877);
        assertNotEquals(oldKey,atlas.surfaceHeightFingerprint());
        assertEquals(before,stream(atlas,profile));
        // The sampled neighbour must still force a different geometry identity.
        // Band4 is intrinsic Z=-0.5, the centre of Atlas row63.
        atlas.putCell(0,63,90,0x998877);
        assertNotEquals(before,stream(atlas,profile));
    }

    /** Pure intrinsic stream proof; native tests additionally exercise the real BufferBuilder packing. */
    private static RingSurfaceMeshBytes stream(RingTerrainAtlas atlas,RingRenderProfile profile) {
        var mesh=RingSurfaceMesh.build(atlas.geometry(),atlas,true,64,64,0,RingWallStyle.LEGACY,42,profile);
        ByteBuffer bytes=ByteBuffer.allocate(mesh.vertexCount()*24).order(ByteOrder.LITTLE_ENDIAN);
        mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
            int color=-1; float ix,iy,iz;
            public void sideColor(int rgb) { color=rgb; }
            public void intrinsic(float x,float y,float z) { ix=x;iy=y;iz=z; }
            public void vertex(float x,float y,float z,float u,float v) {
                bytes.putFloat(ix).putFloat(iy).putFloat(iz).putFloat(u).putFloat(v).putInt(color);
            }
        });
        bytes.flip();return RingSurfaceMeshBytes.identify(bytes,mesh.vertexCount(),24);
    }
}
