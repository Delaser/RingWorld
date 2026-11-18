package dev.ringworld.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingAtlasColoursTest {
    @Test
    void dedicatedOverworldBiomesUseVanillaColormapSamples() {
        var plains = RingAtlasColours.climateSample(0.8F, 0.4F);
        assertEquals(0x91bd59, plains.grass());
        assertEquals(0x77ab2f, plains.foliage());
        assertEquals(0xa37546, plains.litter());
        assertEquals(0x60a17b, RingAtlasColours.climateSample(-0.5F, 0.5F).foliage());
        assertEquals(0xaea42a, RingAtlasColours.climateSample(2F, 0F).foliage());
    }
}
