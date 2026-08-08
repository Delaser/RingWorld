package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RingMapCompassSupportTest {
    private final RingGeometry geometry = new RingGeometry(416, 2_048);

    @Test
    void mapSamplesAndDecorationsUseTheImageNearestTheirMapCentre() {
        assertEquals(2_050.0, RingMapCompassSupport.nearestMapImageX(geometry, 2.0, 2_046), 1.0e-9);
        assertEquals(1.0F, RingMapCompassSupport.nearestMapDecorationDeltaX(
                geometry, 2_046, 4, 2.0));
        assertEquals(-2.0, RingMapCompassSupport.nearestMapImageX(geometry, 2_046.0, 2), 1.0e-9);
        assertEquals(-1.0F, RingMapCompassSupport.nearestMapDecorationDeltaX(
                geometry, 2, 4, 2_046.0));
        // The graphical fixture scales its high-centred map once before locking
        // it. Its canonical seam banner must remain inside that scale-one map.
        assertEquals(2_049.5, RingMapCompassSupport.nearestMapImageX(geometry, 1.5, 2_112), 1.0e-9);
        assertEquals(-31.25F, RingMapCompassSupport.nearestMapDecorationDeltaX(
                geometry, 2_112, 2, 1.5), 1.0e-6F);
        assertEquals(127, RingMapCompassSupport.canonicalMapSampleChunkX(geometry, -1));
        assertEquals(0, RingMapCompassSupport.canonicalMapSampleChunkX(geometry, 128));
        assertEquals(2_050, RingMapCompassSupport.nearestMapBannerBlockX(geometry, 2, 2_046));
        assertEquals(-2, RingMapCompassSupport.nearestMapBannerBlockX(geometry, 2_046, 2));
    }

    @Test
    void compassTargetsUseTheImageNearestTheirHolder() {
        assertEquals(2_050.5, RingMapCompassSupport.nearestCompassTargetX(
                geometry, 2.5, 2_047.75), 1.0e-9);
        assertEquals(-1.5, RingMapCompassSupport.nearestCompassTargetX(
                geometry, 2_046.5, 0.25), 1.0e-9);
        assertFalse(RingMapCompassSupport.isCompassTargetDistinct(
                geometry, 0.5, 64.5, 10.5, 2_048.5, 64.5, 10.5));
        assertTrue(RingMapCompassSupport.isCompassTargetDistinct(
                geometry, 1.5, 64.5, 10.5, 2_048.5, 64.5, 10.5));
        assertTrue(RingMapCompassSupport.isCompassTargetDistinct(
                geometry, 0.5, 65.5, 10.5, 2_048.5, 64.5, 10.5));
    }
}
