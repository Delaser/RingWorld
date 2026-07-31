package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingGeometryTest {
    @Test
    void productionProxyExtendsBeyondAnOrdinaryTwentyEightChunkFarPlane() {
        RingGeometry production = new RingGeometry(
                RingWorldSettings.DEFAULT_WIDTH,
                RingWorldSettings.DEFAULT_CIRCUMFERENCE);
        RingGeometry safeSmall = new RingGeometry(416, 2_048);
        double ordinaryFarPlane = 28 * 16.0 * 4.0;

        assertTrue(production.oppositeReferenceSurfaceDistance(
                RingGeometry.SURFACE_Y) > ordinaryFarPlane);
        assertTrue(production.maximumReferenceSurfaceDistance(
                RingGeometry.SURFACE_Y, 0.0) > ordinaryFarPlane);
        assertTrue(safeSmall.maximumReferenceSurfaceDistance(
                RingGeometry.SURFACE_Y, 0.0) < ordinaryFarPlane);
    }

    // Deliberately retain a non-power-of-two custom layout so the topology
    // suite cannot accidentally become dependent on the production default.
    private final RingGeometry geometry = new RingGeometry(4_096, 15_552);

    @Test
    void seamHasOnePhysicalPosition() {
        assertEquals(geometry.toPhysical(0, 64, 100), geometry.toPhysical(15_552, 64, 100));
        assertEquals(0.0, geometry.shortestCircumferenceDelta(15_551, -1), 1.0e-9);
    }

    @Test
    void clientPresentationPositionsKeepTravelContinuousAcrossTheSeam() {
        RingPosition forward = RingPosition.fromPresentationX(15_552.25, geometry);
        RingPosition backward = RingPosition.fromPresentationX(-0.25, geometry);
        assertEquals(0.25, forward.canonicalX(), 1.0e-9);
        assertEquals(1, forward.chartIndex());
        assertEquals(15_552.25, forward.presentationX(geometry), 1.0e-9);
        assertEquals(15_551.75, backward.canonicalX(), 1.0e-9);
        assertEquals(-1, backward.chartIndex());
        assertEquals(-0.25, backward.presentationX(geometry), 1.0e-9);
        assertEquals(15_552.2, geometry.nearestImageX(0.2, 15_552.1), 1.0e-9);
    }

    @Test
    void presentationChartsAlwaysResolveToOneCanonicalX() {
        RingGeometry testRing = new RingGeometry(416, 2_048);
        RingPosition beforeZero = RingPosition.fromPresentationX(-0.25, testRing);
        RingPosition firstSeam = RingPosition.fromPresentationX(2_048.0, testRing);
        RingPosition secondSeam = RingPosition.fromPresentationX(4_096.25, testRing);

        assertEquals(2_047.75, beforeZero.canonicalX(), 1.0e-9);
        assertEquals(-1, beforeZero.chartIndex());
        assertEquals(0.0, firstSeam.canonicalX(), 1.0e-9);
        assertEquals(1, firstSeam.chartIndex());
        assertEquals(0.25, secondSeam.canonicalX(), 1.0e-9);
        assertEquals(2, secondSeam.chartIndex());
        assertEquals(0, testRing.wrapBlockX(2_048));
        assertEquals(127, RingChunkCoordinates.wrapChunkX(-1, testRing));
    }

    @Test
    void nextPositiveSeamFollowsTheCurrentPresentationChart() {
        RingGeometry testRing = new RingGeometry(416, 2_048);

        assertEquals(0.0, testRing.nextPositiveSeamX(-4.0), 1.0e-9);
        assertEquals(2_048.0, testRing.nextPositiveSeamX(2_044.0), 1.0e-9);
        assertEquals(4_096.0, testRing.nextPositiveSeamX(2_048.0), 1.0e-9);
        assertEquals(-2_048.0, testRing.nextPositiveSeamX(-4_096.0), 1.0e-9);
    }

    @Test
    void canonicalWrapOnlyOccursOutsideTheStorageRange() {
        assertTrue(!geometry.needsCanonicalWrap(0.0));
        assertTrue(!geometry.needsCanonicalWrap(15_551.999));
        assertTrue(geometry.needsCanonicalWrap(-0.000_001));
        assertTrue(geometry.needsCanonicalWrap(15_552.0));
    }

    @Test
    void circumferenceIsAboutOneHourOfWalking() {
        // 4.317 blocks/s is normal walking speed without sprinting or effects.
        double seconds = RingWorldSettings.DEFAULT_CIRCUMFERENCE / 4.317;
        assertTrue(seconds > 3_750 && seconds < 3_850);
    }

    @Test
    void productionCircumferenceAlignsPowerOfTwoAtlasChunksAndRegions() {
        int circumference = RingWorldSettings.DEFAULT_CIRCUMFERENCE;

        assertEquals(1, Integer.bitCount(circumference));
        assertEquals(0, circumference % 512);
        assertEquals(1_024, circumference / 16);
        assertEquals(32, circumference / 512);
        assertEquals(2_048, circumference / RingTerrainAtlas.SAMPLE_STEP_BLOCKS);
    }

    @Test
    void gravityRotatesWithTheBand() {
        assertEquals(1.0, geometry.gravityAt(0).y, 1.0e-9);
        assertEquals(1.0, geometry.gravityAt(geometry.circumferenceBlocks() / 4.0).z, 1.0e-9);
    }

    @Test
    void chunkCoordinatesArePeriodicToo() {
        assertEquals(972, RingChunkCoordinates.circumferenceChunks(geometry));
        assertEquals(0, RingChunkCoordinates.wrapChunkX(972, geometry));
        assertEquals(971, RingChunkCoordinates.wrapChunkX(-1, geometry));
        assertEquals(23, RingChunkCoordinates.wrapChunkX(23 + 972 * 50, geometry));
        assertEquals(972, RingChunkCoordinates.nearestImageChunkX(0, 971, geometry));
        assertEquals(-1, RingChunkCoordinates.nearestImageChunkX(971, 0, geometry));
        assertEquals(971, RingChunkCoordinates.nearestImageChunkX(-1, 970, geometry));
    }

    @Test
    void widthIsCentredOnVanillaSpawnCoordinates() {
        assertEquals(-2_048, geometry.minWidthZ());
        assertEquals(2_047, geometry.maxWidthZ());
        assertEquals(-128, geometry.minChunkZ());
        assertEquals(127, geometry.maxChunkZ());
        assertTrue(geometry.isExteriorChunkZ(-129));
        assertFalse(geometry.isExteriorChunkZ(-128));
        assertFalse(geometry.isExteriorChunkZ(127));
        assertTrue(geometry.isExteriorChunkZ(128));
        assertTrue(geometry.isInsideWidth(0));
    }

    @Test
    void cylindricalNoiseCoordinatesMeetAtTheSeam() {
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(geometry);
        assertEquals(geometry.angleAt(0), geometry.angleAt(geometry.circumferenceBlocks()), 1.0e-12);
        assertEquals(geometry.toPhysical(0, 0, 700),
                geometry.toPhysical(geometry.circumferenceBlocks(), 0, 700));
        assertEquals(coordinates.ringX(0), coordinates.ringX(geometry.circumferenceBlocks()));
        assertEquals(coordinates.ringZ(0, 700),
                coordinates.ringZ(geometry.circumferenceBlocks(), 700));
        assertTrue(Math.abs(coordinates.ringX(geometry.circumferenceBlocks() - 1)
                - coordinates.ringX(0)) <= 1);
        assertTrue(Math.abs(coordinates.ringZ(geometry.circumferenceBlocks() - 1, 700)
                - coordinates.ringZ(0, 700)) <= 1);
    }

    @Test
    void optimizedCameraTransformIsContinuousAndLocallyOriented() {
        Vec3 camera = new Vec3(15_551.5, 64.0, 10.0);
        assertEquals(Vec3.ZERO, geometry.toCameraLocal(camera, camera));
        Vec3 acrossSeam = geometry.toCameraLocal(new Vec3(15_552.5, 64.0, 10.0), camera);
        assertTrue(acrossSeam.x > 0.99 && acrossSeam.x < 1.01);
        assertTrue(Math.abs(acrossSeam.y) < 0.001);
        assertEquals(4.0, geometry.toCameraLocal(new Vec3(camera.x, camera.y, 14.0), camera).z, 1.0e-9);
    }

    @Test
    void starIsOneRingCenteredPointAtEveryPositionAndPresentationImage() {
        double ringCenterY = geometry.radius() + RingGeometry.SURFACE_Y;
        for (double x : new double[]{0.0, geometry.circumferenceBlocks() / 4.0,
                geometry.circumferenceBlocks() / 2.0,
                geometry.circumferenceBlocks() * 3.0 / 4.0,
                geometry.circumferenceBlocks() * 3.0}) {
            Vec3 camera = new Vec3(x, 80.0, 0.0);
            assertEquals(new Vec3(0.0, ringCenterY - camera.y, 0.0),
                    geometry.ringCenterInCameraFrame(camera));
            assertEquals(new Vec3(0.0, 1.0, 0.0), geometry.directionToRingCenter(camera));
        }

        Vec3 positiveEdge = new Vec3(123.0, 80.0, geometry.maxWidthZ());
        Vec3 negativeEdge = new Vec3(123.0, 80.0, geometry.minWidthZ());
        assertTrue(geometry.directionToRingCenter(positiveEdge).z < 0.0);
        assertTrue(geometry.directionToRingCenter(negativeEdge).z > 0.0);
        assertEquals(geometry.directionToRingCenter(positiveEdge).y,
                geometry.directionToRingCenter(negativeEdge).y, 0.001);
    }

    @Test
    void entityTangentFramesRotateContinuouslyAcrossPeriodicImages() {
        double quarter = geometry.circumferenceBlocks() / 4.0;
        assertEquals(0.0, geometry.tangentFrameAngle(100.0, 100.0), 1.0e-12);
        assertEquals(Math.PI / 2.0, geometry.tangentFrameAngle(0.0, quarter), 1.0e-12);
        assertEquals(-Math.PI / 2.0, geometry.tangentFrameAngle(0.0, -quarter), 1.0e-12);
        assertEquals(geometry.tangentFrameAngle(15_551.5, 15_552.5),
                geometry.tangentFrameAngle(15_551.5, 0.5), 1.0e-12);
        assertEquals(geometry.tangentFrameAngle(10.0, 11.0),
                geometry.tangentFrameAngle(10.0 + 50.0 * geometry.circumferenceBlocks(),
                        11.0 + 50.0 * geometry.circumferenceBlocks()), 1.0e-12);
    }

    @Test
    void distantSurfaceFormsACompleteArchThroughTheZenith() {
        Vec3 camera = new Vec3(0.0, RingGeometry.SURFACE_Y, 0.0);
        Vec3 eastBase = geometry.toCameraLocal(
                new Vec3(64.0, RingGeometry.SURFACE_Y, 0.0), camera);
        Vec3 opposite = geometry.toCameraLocal(
                new Vec3(geometry.circumferenceBlocks() / 2.0,
                        RingGeometry.SURFACE_Y, 0.0), camera);
        Vec3 westBase = geometry.toCameraLocal(
                new Vec3(geometry.circumferenceBlocks() - 64.0,
                        RingGeometry.SURFACE_Y, 0.0), camera);

        assertTrue(eastBase.x > 0.0 && eastBase.y > 0.0);
        assertEquals(0.0, opposite.x, 1.0e-9);
        assertTrue(opposite.y > 0.0);
        assertTrue(westBase.x < 0.0 && westBase.y > 0.0);
    }

    @Test
    void curvedSectionBoundsRiseIntoTheUpwardFrustum() {
        RingGeometry smallRing = new RingGeometry(416, 2_048);
        Vec3 camera = new Vec3(0.0, 80.0, 0.0);
        AABB flatSection = new AABB(432.0, 64.0, -8.0, 448.0, 80.0, 8.0);
        AABB curved = smallRing.toCameraLocalBounds(flatSection, camera);

        assertTrue(curved.minY > 150.0,
                "a far section should visibly rise with the ring rather than remain flat");
        assertTrue(curved.maxX > curved.minX);
        assertEquals(-8.0, curved.minZ, 1.0e-9);
        assertEquals(8.0, curved.maxZ, 1.0e-9);
    }

    @Test
    void capturePitchTracksTheRenderedSurfaceAtDifferentDistances() {
        RingGeometry smallRing = new RingGeometry(416, 2_048);

        double shortViewPitch = smallRing.pitchDegreesToIntrinsic(
                120.0, 64.0, 6 * 16.0, 0.0);
        double longViewPitch = smallRing.pitchDegreesToIntrinsic(
                120.0, 64.0, 28 * 16.0, 0.0);

        assertTrue(shortViewPitch > 0.0,
                "near surface is still below the elevated test camera");
        assertTrue(longViewPitch < 0.0,
                "curved distant surface rises above the test camera");
        assertEquals(23.918, shortViewPitch, 0.01);
        assertEquals(-32.843, longViewPitch, 0.02);
    }

    @Test
    void topologySplitsASeamCrossingBoxIntoCanonicalWindows() {
        RingTopology topology = new RingTopology(geometry);
        AABB query = new AABB(15_551.5, 60.0, -1.0, 15_553.5, 62.0, 1.0);
        var windows = topology.canonicalWindows(query);
        assertEquals(2, windows.size());
        assertEquals(15_551.5, windows.get(0).canonicalBox().minX, 1.0e-9);
        assertEquals(15_552.0, windows.get(0).canonicalBox().maxX, 1.0e-9);
        assertEquals(0.0, windows.get(1).canonicalBox().minX, 1.0e-9);
        assertEquals(1.5, windows.get(1).canonicalBox().maxX, 1.0e-9);
        assertEquals(15_552.0, windows.get(1).chartOffset(), 1.0e-9);
    }

    @Test
    void topologyUsesShortestDistanceAcrossTheSeam() {
        RingTopology topology = new RingTopology(geometry);
        assertEquals(4.0, topology.squaredHorizontalDistance(15_551.0, 0.0, 1.0, 0.0), 1.0e-9);
        assertEquals(15_553.0, topology.imageNear(1.0, 15_551.0), 1.0e-9);
        assertEquals(972, topology.imageChunkNear(0, 971));
    }

    @Test
    void fullCircumferenceQueriesCanonicalStorageOnlyOnce() {
        RingTopology topology = new RingTopology(geometry);
        var windows = topology.canonicalWindows(new AABB(-10.0, 0.0, 0.0, 20_000.0, 1.0, 1.0));
        assertEquals(1, windows.size());
        assertEquals(0.0, windows.getFirst().canonicalBox().minX, 1.0e-9);
        assertEquals(15_552.0, windows.getFirst().canonicalBox().maxX, 1.0e-9);
    }
}
