package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingCloudBoundsTest {
    @Test
    void followsInnerFacesForAllPresetWidthsAndCustomThickness() {
        for (int width : new int[] {128, 256, 512}) {
            RingCloudBounds bounds = RingCloudBounds.betweenInnerRimFaces(
                    new RingGeometry(width, 16_384), 5);
            assertEquals(-width / 2.0 + 5.0, bounds.minimumZ());
            assertEquals(width / 2.0 - 5.0, bounds.maximumZ());
            assertTrue(bounds.contains(bounds.minimumZ()));
            assertTrue(bounds.contains(bounds.maximumZ()));
            assertFalse(bounds.contains(bounds.minimumZ() - 0.001));
            assertFalse(bounds.contains(bounds.maximumZ() + 0.001));
        }
        RingCloudBounds custom = RingCloudBounds.betweenInnerRimFaces(
                new RingGeometry(256, 16_384), 12);
        assertEquals(-116.0, custom.minimumZ());
        assertEquals(116.0, custom.maximumZ());
    }

    @Test
    void rejectsAThicknessWithNoInterior() {
        assertThrows(IllegalArgumentException.class, () ->
                RingCloudBounds.betweenInnerRimFaces(new RingGeometry(128, 2_048), 64));
    }
}
