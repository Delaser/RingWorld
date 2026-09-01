package dev.ringworld.world;

import java.util.Arrays;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingWallShaderStyleTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void encodingIsDeterministicAndPacksPatternSeedAndDecaySeparately() {
        RingWallStyle style = RingWallStyle.custom(7, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.PANELS, 70);

        RingWallShaderStyle.Encoded first = encode(style, 0x1234_5678_9ABCDEFL);
        RingWallShaderStyle.Encoded repeat = encode(style, 0x1234_5678_9ABCDEFL);

        assertArrayEquals(first.paletteColumns(), repeat.paletteColumns());
        assertEquals(first.vertexArgb(), repeat.vertexArgb());
        assertEquals(RingWallStyle.Pattern.PANELS.id(), first.patternId());
        assertEquals(first.patternId() * 32 + first.seedBits(),
                first.vertexArgb() >>> 24);
        assertEquals(0.70F, first.decay(), 0.000_001F);
        assertTrue(first.seedBits() >= 0 && first.seedBits() < 32);
    }

    @Test
    void paletteHasFourThresholdColumnsAndFifthColourInVertexRgb() {
        RingWallShaderStyle.Encoded encoded = encode(
                RingWallStyle.Preset.OBSIDIAN_BASTION.style(), 91L);
        float[] palette = encoded.paletteColumns();

        assertEquals(16, palette.length);
        float previous = 0.0F;
        for (int column = 0; column < 4; column++) {
            float threshold = palette[column * 4 + 3];
            assertTrue(threshold >= previous && threshold <= 1.0F);
            previous = threshold;
            for (int channel = 0; channel < 3; channel++) {
                float value = palette[column * 4 + channel];
                assertTrue(value >= 0.0F && value <= 1.0F);
            }
        }
        assertNotEquals(0, encoded.vertexArgb() & 0xFFFFFF);
    }

    @Test
    void seedPatternDecayAndPaletteRemainIndependentInputs() {
        RingWallStyle base = RingWallStyle.custom(5, RingWallStyle.Palette.WEATHERED,
                RingWallStyle.Pattern.MASONRY, 10);
        RingWallShaderStyle.Encoded first = encode(base, 1L);
        RingWallShaderStyle.Encoded otherSeed = encode(base, 2L);
        RingWallShaderStyle.Encoded otherDecay = encode(
                RingWallStyle.custom(5, base.palette(), base.pattern(), 90), 1L);
        RingWallShaderStyle.Encoded otherPalette = encode(
                RingWallStyle.custom(5, RingWallStyle.Palette.NETHER,
                        base.pattern(), base.decayPercent()), 1L);

        assertArrayEquals(first.paletteColumns(), otherSeed.paletteColumns());
        assertNotEquals(first.vertexArgb() >>> 24, otherSeed.vertexArgb() >>> 24);
        assertEquals(first.vertexArgb(), otherDecay.vertexArgb());
        assertNotEquals(first.decay(), otherDecay.decay());
        assertFalse(Arrays.equals(first.paletteColumns(), otherPalette.paletteColumns()));
    }

    @Test
    void returnedPaletteArrayCannotMutateEncodedState() {
        RingWallShaderStyle.Encoded encoded = encode(RingWallStyle.DEFAULT, 7L);
        float[] changed = encoded.paletteColumns();
        changed[0] = -1.0F;
        assertNotEquals(-1.0F, encoded.paletteColumns()[0]);
    }

    private static RingWallShaderStyle.Encoded encode(RingWallStyle style, long seed) {
        return RingWallShaderStyle.encode(style, seed, state -> {
            int id = BuiltInRegistries.BLOCK.getId(state.getBlock()) + 1;
            return id * 0x1F123B & 0xFFFFFF;
        });
    }
}
