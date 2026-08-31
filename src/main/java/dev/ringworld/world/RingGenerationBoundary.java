package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Boundary operations performed during chunk generation, never live ticks. */
public final class RingGenerationBoundary {
    public static final int RIM_THICKNESS = 5;
    /** Increment when rim placement/material semantics change. */
    public static final int RIM_STYLE_VERSION = 3;

    private RingGenerationBoundary() { }

    /**
     * Exclusive intrinsic Y immediately above the generated rim, clamped to
     * the owning world's vertical extent. Terrain above this bound remains
     * vanilla terrain; rim installation deliberately does not erase it.
     */
    public static int wallTopExclusive(int worldMinY, int worldHeight, int wallHeightBlocks) {
        if (worldHeight <= 0) throw new IllegalArgumentException("world height must be positive");
        if (wallHeightBlocks < 0) throw new IllegalArgumentException("wall height must be non-negative");
        long worldTopExclusive = (long)worldMinY + worldHeight;
        long requestedTopExclusive = (long)worldMinY + wallHeightBlocks;
        return (int)Math.min(requestedTopExclusive, worldTopExclusive);
    }

    public static boolean isExterior(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return pos.getMaxBlockZ() < geometry.minWidthZ() || pos.getMinBlockZ() > geometry.maxWidthZ();
    }

    public static boolean containsRim(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return containsZ(pos, geometry.minWidthZ()) || containsZ(pos, geometry.maxWidthZ());
    }

    private static boolean containsZ(ChunkPos pos, int z) {
        return z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    /** Removes only cross-chunk feature spillover; noise generation is skipped entirely. */
    public static void clearExterior(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) continue;
            int sectionBottomY = chunk.getMinY() + sectionIndex * 16;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).isAir()) continue;
                        mutable.set(pos.getMinBlockX() + localX, sectionBottomY + localY, pos.getMinBlockZ() + localZ);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                    }
                }
            }
        }
    }

    /** Installs the finite, breakable rim after features have finished. */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int wallHeightBlocks) {
        installRim(chunk, geometry, wallHeightBlocks, RingWallStyle.LEGACY, 0L);
    }

    /** Installs the saved deterministic wall style after features have finished. */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int wallHeightBlocks,
                                  RingWallStyle style, long worldSeed) {
        if (wallHeightBlocks <= 0) return;
        ChunkPos pos = chunk.getPos();
        int firstZ = Math.max(pos.getMinBlockZ(), geometry.minWidthZ());
        int lastZ = Math.min(pos.getMaxBlockZ(), geometry.maxWidthZ());
        boolean intersectsStyledRim = false;
        for (int z = firstZ; z <= lastZ; z++) {
            if (rimDepthAtZ(geometry, style, z) >= 0) {
                intersectsStyledRim = true;
                break;
            }
        }
        if (!intersectsStyledRim) return;
        int wallTopExclusive = wallTopExclusive(
                chunk.getMinY(), chunk.getHeight(), wallHeightBlocks);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int y = chunk.getMinY(); y < wallTopExclusive; y++) {
                for (int z = firstZ; z <= lastZ; z++) {
                    int depth = rimDepthAtZ(geometry, style, z);
                    if (depth < 0) continue;
                    mutable.set(x, y, z);
                    chunk.removeBlockEntity(mutable);
                    if (RingWallPattern.blockPresent(style, x, y, depth, wallTopExclusive,
                            geometry.circumferenceBlocks(), worldSeed)) {
                        chunk.setBlockState(mutable,
                                texturedRimBlock(style, x, y, depth,
                                        geometry.circumferenceBlocks(), worldSeed), 0);
                    } else {
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                    }
                }
            }
        }
    }

    /** Returns inward rim depth for a band Z, or -1 when it is playable interior. */
    public static int rimDepthAtZ(RingGeometry geometry, RingWallStyle style, int z) {
        int lowDepth = z - geometry.minWidthZ();
        if (lowDepth >= 0 && lowDepth < style.thicknessBlocks()) return lowDepth;
        int highDepth = geometry.maxWidthZ() - z;
        return highDepth >= 0 && highDepth < style.thicknessBlocks() ? highDepth : -1;
    }

    /**
     * One-time, content-detected migration for already generated stone-brick
     * rims. Once converted there are no legacy blocks above the new top, so a
     * deliberately broken textured wall is not rebuilt on later chunk loads.
     */
    public static boolean migrateLegacyRim(LevelChunk chunk, RingGeometry geometry,
                                           int wallHeightBlocks) {
        if (!containsRim(chunk, geometry)) return false;
        ChunkPos pos = chunk.getPos();
        int wallTopExclusive = wallTopExclusive(
                chunk.getMinY(), chunk.getHeight(), wallHeightBlocks);
        int chunkTopExclusive = chunk.getMinY() + chunk.getHeight();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        boolean legacy = false;
        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX() && !legacy; x++) {
                for (int y = wallTopExclusive; y < chunkTopExclusive; y++) {
                    if (chunk.getBlockState(mutable.set(x, y, rimZ)).is(Blocks.STONE_BRICKS)) {
                        legacy = true;
                        break;
                    }
                }
            }
        }
        if (!legacy) return false;

        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                    int z = rimZ + inwardDirection * depth;
                    for (int y = chunk.getMinY(); y < chunkTopExclusive; y++) {
                        mutable.set(x, y, z);
                        if (y < wallTopExclusive) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, texturedRimBlock(x, y, z), 0);
                        } else if (chunk.getBlockState(mutable).is(Blocks.STONE_BRICKS)) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                        }
                    }
                }
            }
        }
        chunk.markUnsaved();
        return true;
    }

    public static boolean isRimMaterial(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.TUFF)
                || state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.POLISHED_DIORITE)
                || state.is(Blocks.QUARTZ_BLOCK) || state.is(Blocks.PRISMARINE_BRICKS)
                || state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.DEEPSLATE_TILES)
                || state.is(Blocks.POLISHED_BASALT) || state.is(Blocks.TUFF_BRICKS)
                || state.is(Blocks.RAW_COPPER_BLOCK) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.CALCITE) || state.is(Blocks.POLISHED_ANDESITE)
                || state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.RED_NETHER_BRICKS)
                || state.is(Blocks.BLACKSTONE) || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.GILDED_BLACKSTONE)
                || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.OAK_LOG)
                || state.is(Blocks.SPRUCE_LOG) || state.is(Blocks.DARK_OAK_LOG)
                || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.STRIPPED_SPRUCE_LOG);
    }

    private static net.minecraft.world.level.block.state.BlockState texturedRimBlock(int x, int y, int z) {
        long hash = (long)x * 0x9E3779B97F4A7C15L
                ^ (long)y * 0xC2B2AE3D27D4EB4FL
                ^ (long)z * 0x165667B19E3779F9L;
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        // Roughly 30% mossy blocks, stable across reloads and generation order.
        return Long.remainderUnsigned(hash, 10L) < 3L
                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }

    private static net.minecraft.world.level.block.state.BlockState texturedRimBlock(
            RingWallStyle style, int x, int y, int depth, int circumference, long worldSeed) {
        // One independently sampled block in a thousand. Keeping this outside
        // the 0-99 palette roll avoids the former minimum density of one percent.
        if (style.palette() == RingWallStyle.Palette.INDUSTRIAL
                && RingWallPattern.rareAccent(
                        style, x, y, depth, circumference, worldSeed, 1)) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        int roll = RingWallPattern.materialRoll(style, x, y, depth, circumference, worldSeed);
        return styledRimBlockForRoll(style, roll);
    }

    /**
     * Single material-palette source shared by real wall generation and the
     * client Atlas-rim colour derivation.
     */
    public static net.minecraft.world.level.block.state.BlockState styledRimBlockForRoll(
            RingWallStyle style, int roll) {
        if (style == null) throw new IllegalArgumentException("wall style is required");
        if (roll < 0 || roll > 99) {
            throw new IllegalArgumentException("wall material roll must be in [0, 99]");
        }
        return switch (style.palette()) {
            case WEATHERED -> roll < 55 ? Blocks.COBBLESTONE.defaultBlockState()
                    : roll < 80 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : roll < 95 ? Blocks.STONE.defaultBlockState()
                    : Blocks.ANDESITE.defaultBlockState();
            case ANCIENT -> roll < 50 ? Blocks.STONE_BRICKS.defaultBlockState()
                    : roll < 72 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : roll < 94 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : Blocks.COBBLESTONE.defaultBlockState();
            case NATURAL -> roll < 45 ? Blocks.STONE.defaultBlockState()
                    : roll < 70 ? Blocks.TUFF.defaultBlockState()
                    : roll < 90 ? Blocks.ANDESITE.defaultBlockState()
                    : roll < 98 ? Blocks.COBBLESTONE.defaultBlockState()
                    : Blocks.MOSS_BLOCK.defaultBlockState();
            case ALLOY -> roll < 40 ? Blocks.SMOOTH_STONE.defaultBlockState()
                    : roll < 70 ? Blocks.POLISHED_DIORITE.defaultBlockState()
                    : roll < 95 ? Blocks.QUARTZ_BLOCK.defaultBlockState()
                    : Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case INDUSTRIAL -> roll < 40 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                    : roll < 68 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                    : roll < 86 ? Blocks.POLISHED_BASALT.defaultBlockState()
                    : roll < 97 ? Blocks.TUFF_BRICKS.defaultBlockState()
                    : Blocks.RAW_COPPER_BLOCK.defaultBlockState();
            case OVERGROWN -> roll < 38 ? Blocks.STONE_BRICKS.defaultBlockState()
                    : roll < 60 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : roll < 78 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : roll < 92 ? Blocks.COBBLESTONE.defaultBlockState()
                    : Blocks.MOSS_BLOCK.defaultBlockState();
            case MONOLITH -> roll < 55 ? Blocks.SMOOTH_STONE.defaultBlockState()
                    : roll < 82 ? Blocks.CALCITE.defaultBlockState()
                    : Blocks.POLISHED_ANDESITE.defaultBlockState();
            case NETHER -> roll < 42 ? Blocks.NETHER_BRICKS.defaultBlockState()
                    : roll < 60 ? Blocks.RED_NETHER_BRICKS.defaultBlockState()
                    : roll < 78 ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 96 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : Blocks.MAGMA_BLOCK.defaultBlockState();
            case OBSIDIAN -> roll < 55 ? Blocks.OBSIDIAN.defaultBlockState()
                    : roll < 72 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : roll < 90 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : roll < 97 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                    : Blocks.AMETHYST_BLOCK.defaultBlockState();
            case WOOD -> roll < 30 ? Blocks.OAK_LOG.defaultBlockState()
                    : roll < 54 ? Blocks.SPRUCE_LOG.defaultBlockState()
                    : roll < 72 ? Blocks.DARK_OAK_LOG.defaultBlockState()
                    : roll < 90 ? Blocks.OAK_PLANKS.defaultBlockState()
                    : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        };
    }
}
