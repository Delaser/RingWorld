package dev.ringworld.world;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backport-only invariants for the Minecraft 1.21.1 compositor adapter. */
final class LegacyHandoffCompositorTest {
    @ParameterizedTest
    @ValueSource(ints = {6, 16, 28})
    void derivedOverlapPointsRemainOrderedForValidatedRadii(
            int viewDistanceChunks) {
        RingRenderProfile profile = RingRenderProfile.create(
                new RingGeometry(
                        RingWorldSettings.DEFAULT_WIDTH,
                        RingWorldSettings.DEFAULT_CIRCUMFERENCE),
                viewDistanceChunks * 16.0);

        // The shader derives this earlier proxy point locally without
        // redefining any shared RingWorldHandoff field.
        double legacyProxyFadeStart = 2.0 * profile.proxyFadeStartBlocks()
                - profile.liveFadeStartBlocks();
        assertTrue(0.0 < legacyProxyFadeStart);
        assertTrue(legacyProxyFadeStart < profile.proxyFadeStartBlocks());
        assertTrue(profile.proxyFadeStartBlocks() < profile.liveFadeStartBlocks());
        assertTrue(profile.liveFadeStartBlocks() < profile.proxyFadeEndBlocks());
        assertTrue(profile.proxyFadeEndBlocks() < profile.liveFadeEndBlocks());
        assertTrue(profile.liveFadeEndBlocks() < profile.detailEndBlocks());
    }
}
