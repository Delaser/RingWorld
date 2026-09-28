package dev.ringworld.client.render;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingIndustrialElements;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWallStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RingWallTextureKeyTest {
    @Test
    void onlyVisibleWallInputsInvalidateTheTexture() {
        var geometry = new RingGeometry(128, 1_024);
        var atlas = new RingTerrainAtlas(geometry, 7L, 1);
        var style = RingWallStyle.DEFAULT;
        int[] palette = {0x334455, 0x556677};
        var original = RingWallTexture.contentKey(atlas, style, 11L, -64, 100, 256, palette);

        atlas.putBlockSample(0, 0, 140, 0x334455);
        assertEquals(original, RingWallTexture.contentKey(atlas, style, 11L, -64, 100, 256, palette));

        var feature = RingIndustrialElements.feature(0, geometry.circumferenceBlocks(), 11L, 0);
        int anchorZ = geometry.minWidthZ() + style.thicknessBlocks() + 3;
        atlas.putBlockSample(feature.centerX(), anchorZ, 140, 0x334455);
        var changedRim = RingWallTexture.contentKey(atlas, style, 11L, -64, 100, 256, palette);
        assertNotEquals(original, changedRim);

        assertNotEquals(changedRim, RingWallTexture.contentKey(
                atlas, style, 11L, -64, 100, 256, new int[]{0x334455, 0x556678}));
    }
}
