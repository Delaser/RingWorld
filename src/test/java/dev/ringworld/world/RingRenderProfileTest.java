package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingRenderProfileTest {
    @Test
    void safeSmallProfilePreservesCurrentHandoffAndExactTexture() {
        RingRenderProfile profile = RingRenderProfile.create(
                new RingGeometry(416, 2_048), 28 * 16.0);

        assertFalse(profile.wholeRingViewRequested());
        assertEquals(448.0, profile.effectiveViewDistanceBlocks());
        assertEquals(349.44, profile.liveFadeStartBlocks(), 1.0e-9);
        assertEquals(456.96, profile.liveFadeEndBlocks(), 1.0e-9);
        assertEquals(304.64, profile.proxyFadeStartBlocks(), 1.0e-9);
        assertEquals(439.04, profile.proxyFadeEndBlocks(), 1.0e-9);
        assertEquals(RingRenderProfile.VISUAL_PROFILE_VERSION, profile.visualProfileVersion());
        assertEquals(0.52, profile.revealNear(), 1.0e-9);
        assertEquals(0.98, profile.revealFar(), 1.0e-9);
        assertEquals(0.04, profile.hazeNear(), 1.0e-9);
        assertEquals(0.16, profile.hazeFar(), 1.0e-9);
        assertEquals(1.35, profile.hazeExponent(), 1.0e-9);
        assertEquals(135.168, profile.cloudFadeStartBlocks(), 1.0e-9);
        assertEquals(245.76, profile.cloudFadeEndBlocks(), 1.0e-9);
        assertEquals(2_048, profile.textureColumns());
        assertEquals(416, profile.textureRows());
        assertEquals(256, profile.circumferenceSegments());
        assertEquals(52, profile.widthBands());
        assertEquals(79_872L, profile.vertexCount());
        assertEquals(1.0, profile.textureBlocksPerTexelX());
        assertEquals(1.0, profile.textureBlocksPerTexelZ());
    }

    @Test
    void formerWideNonPowerOfTwoProfileRemainsSupported() {
        RingRenderProfile profile = RingRenderProfile.create(
                new RingGeometry(4_096, 15_552), 28 * 16.0);

        assertEquals(4_096, profile.textureColumns());
        assertEquals(1_024, profile.textureRows());
        assertEquals(1_944, profile.circumferenceSegments());
        assertEquals(128, profile.widthBands());
        assertEquals(1_492_992L, profile.vertexCount());
        assertEquals(3.796875, profile.textureBlocksPerTexelX());
        assertEquals(4.0, profile.textureBlocksPerTexelZ());
        assertEquals(22_369_616L, profile.estimatedGpuTextureBytes());
        assertEquals(35_831_808L, profile.estimatedGpuMeshBytes());
        assertEquals(50_331_648L, profile.estimatedTextureBuildScratchBytes());
    }

    @Test
    void productionProfileAlignsExactlyWithTextureAndMeshBudgets() {
        RingRenderProfile profile = RingRenderProfile.create(
                new RingGeometry(
                        RingWorldSettings.DEFAULT_WIDTH,
                        RingWorldSettings.DEFAULT_CIRCUMFERENCE),
                28 * 16.0);

        assertEquals(4_096, profile.textureColumns());
        assertEquals(256, profile.textureRows());
        assertEquals(2_048, profile.circumferenceSegments());
        assertEquals(32, profile.widthBands());
        assertEquals(393_216L, profile.vertexCount());
        assertEquals(4.0, profile.textureBlocksPerTexelX());
        assertEquals(1.0, profile.textureBlocksPerTexelZ());
        assertEquals(5_592_384L, profile.estimatedGpuTextureBytes());
        assertEquals(9_437_184L, profile.estimatedGpuMeshBytes());
        assertEquals(12_582_912L, profile.estimatedTextureBuildScratchBytes());
    }

    @Test
    void excessiveViewDistanceBoundsEveryTransitionToOneHalfRing() {
        RingRenderProfile profile = RingRenderProfile.create(
                new RingGeometry(416, 2_048), 2_048.0);

        assertTrue(profile.wholeRingViewRequested());
        assertEquals(1_024.0, profile.effectiveViewDistanceBlocks());
        assertTrue(profile.liveFadeStartBlocks() < profile.liveFadeEndBlocks());
        assertTrue(profile.proxyFadeStartBlocks() < profile.proxyFadeEndBlocks());
        assertTrue(profile.detailStartBlocks() < profile.detailEndBlocks());
        assertTrue(profile.cloudFadeStartBlocks() < profile.cloudFadeEndBlocks());
        assertEquals(1_024.0, profile.liveFadeEndBlocks());
        assertEquals(1_003.52, profile.proxyFadeEndBlocks(), 1.0e-9);
        assertTrue(profile.proxyFadeEndBlocks() <= profile.halfCircumferenceBlocks());
        assertEquals(1_024.0, profile.detailEndBlocks());
        assertEquals(2_048.0 * 0.12, profile.cloudFadeEndBlocks(), 1.0e-9);
    }
}
