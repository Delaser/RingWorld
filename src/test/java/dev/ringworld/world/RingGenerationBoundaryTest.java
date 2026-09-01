package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;

class RingGenerationBoundaryTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void defaultWallHeightUsesTheVanillaOverworldBottom() {
        assertEquals(96, RingGenerationBoundary.wallTopExclusive(-64, 384, 160));
    }

    @Test
    void customWallHeightUsesTheSameSharedGenerationBound() {
        assertEquals(128, RingGenerationBoundary.wallTopExclusive(-64, 384, 192));
    }

    @Test
    void wallHeightClampsAtTheWorldTop() {
        assertEquals(320, RingGenerationBoundary.wallTopExclusive(-64, 384, 512));
    }

    @Test
    void invalidCoordinateDomainInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RingGenerationBoundary.wallTopExclusive(-64, 0, 160));
        assertThrows(IllegalArgumentException.class,
                () -> RingGenerationBoundary.wallTopExclusive(-64, -1, 160));
        assertThrows(IllegalArgumentException.class,
                () -> RingGenerationBoundary.wallTopExclusive(-64, 384, -1));
    }

    @Test
    void maximumThicknessOwnsEveryRimBlockAcrossChunkRows() {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingWallStyle style = RingWallStyle.custom(32, RingWallStyle.Palette.MONOLITH,
                RingWallStyle.Pattern.PANELS, 0);

        assertEquals(0, RingGenerationBoundary.rimDepthAtZ(geometry, style, -64));
        assertEquals(15, RingGenerationBoundary.rimDepthAtZ(geometry, style, -49));
        assertEquals(16, RingGenerationBoundary.rimDepthAtZ(geometry, style, -48));
        assertEquals(31, RingGenerationBoundary.rimDepthAtZ(geometry, style, -33));
        assertEquals(-1, RingGenerationBoundary.rimDepthAtZ(geometry, style, -32));
        assertEquals(31, RingGenerationBoundary.rimDepthAtZ(geometry, style, 32));
        assertEquals(16, RingGenerationBoundary.rimDepthAtZ(geometry, style, 47));
        assertEquals(15, RingGenerationBoundary.rimDepthAtZ(geometry, style, 48));
        assertEquals(0, RingGenerationBoundary.rimDepthAtZ(geometry, style, 63));
    }

    @Test
    void everyDocumentedPaletteMaterialIsRecognizedAsRimContent() {
        for (RingWallStyle.Palette palette : RingWallStyle.Palette.values()) {
            RingWallStyle style = RingWallStyle.custom(
                    5, palette, RingWallStyle.Pattern.MASONRY, 0);
            for (int roll = 0; roll < 100; roll++) {
                assertTrue(RingGenerationBoundary.isRimMaterial(
                        RingGenerationBoundary.styledRimBlockForRoll(style, roll)),
                        palette + " roll " + roll);
            }
        }
        assertTrue(RingGenerationBoundary.isRimMaterial(Blocks.SEA_LANTERN.defaultBlockState()));
        assertFalse(RingGenerationBoundary.isRimMaterial(Blocks.DIRT.defaultBlockState()));
    }

    @Test
    void paletteThresholdsMatchTheDocumentedRealBlockMapping() {
        assertPalette(RingWallStyle.Palette.WEATHERED,
                Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.ANDESITE);
        assertPalette(RingWallStyle.Palette.ANCIENT,
                Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE);
        assertPalette(RingWallStyle.Palette.NATURAL,
                Blocks.STONE, Blocks.TUFF, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.MOSS_BLOCK);
        assertPalette(RingWallStyle.Palette.ALLOY,
                Blocks.SMOOTH_STONE, Blocks.POLISHED_DIORITE, Blocks.QUARTZ_BLOCK,
                Blocks.PRISMARINE_BRICKS);
        assertPalette(RingWallStyle.Palette.INDUSTRIAL,
                Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BASALT,
                Blocks.TUFF_BRICKS, Blocks.RAW_COPPER_BLOCK);
        assertPalette(RingWallStyle.Palette.OVERGROWN,
                Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
                Blocks.CRACKED_STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSS_BLOCK);
        assertPalette(RingWallStyle.Palette.MONOLITH,
                Blocks.SMOOTH_STONE, Blocks.CALCITE, Blocks.POLISHED_ANDESITE);
        assertPalette(RingWallStyle.Palette.NETHER,
                Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.BLACKSTONE,
                Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.MAGMA_BLOCK);
        assertPalette(RingWallStyle.Palette.OBSIDIAN,
                Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.GILDED_BLACKSTONE, Blocks.AMETHYST_BLOCK);
        assertPalette(RingWallStyle.Palette.WOOD,
                Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG,
                Blocks.OAK_PLANKS, Blocks.STRIPPED_SPRUCE_LOG);
        assertThrows(IllegalArgumentException.class, () ->
                RingGenerationBoundary.styledRimBlockForRoll(RingWallStyle.DEFAULT, -1));
        assertThrows(IllegalArgumentException.class, () ->
                RingGenerationBoundary.styledRimBlockForRoll(RingWallStyle.DEFAULT, 100));
    }

    @Test
    void legacyHashMatchesTheFormerBlocksAtBothRimsAndTheCanonicalSeam() {
        int circumference = 2_048;

        assertEquals(55, RingGenerationBoundary.legacyMaterialRoll(0, 64, -64));
        assertEquals(0, RingGenerationBoundary.legacyMaterialRoll(1, 64, -64));
        assertEquals(55, RingGenerationBoundary.legacyMaterialRoll(
                circumference - 2, 95, -64));
        assertEquals(0, RingGenerationBoundary.legacyMaterialRoll(
                circumference - 1, 95, -64));

        assertEquals(0, RingGenerationBoundary.legacyMaterialRoll(0, 64, 63));
        assertEquals(55, RingGenerationBoundary.legacyMaterialRoll(1, 64, 63));
        assertEquals(0, RingGenerationBoundary.legacyMaterialRoll(
                circumference - 2, 95, 63));
        assertEquals(55, RingGenerationBoundary.legacyMaterialRoll(
                circumference - 1, 95, 63));
    }

    private static void assertPalette(RingWallStyle.Palette palette,
                                      net.minecraft.world.level.block.Block... expected) {
        RingWallStyle style = RingWallStyle.custom(
                5, palette, RingWallStyle.Pattern.MASONRY, 0);
        java.util.LinkedHashSet<net.minecraft.world.level.block.Block> actual =
                new java.util.LinkedHashSet<>();
        for (int roll = 0; roll < 100; roll++) {
            actual.add(RingGenerationBoundary.styledRimBlockForRoll(style, roll).getBlock());
        }
        assertEquals(java.util.List.of(expected), java.util.List.copyOf(actual));
    }
}
