package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingStrongholdPlacement;
import dev.ringworld.world.RingStructurePolicy;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;

/** Disposable dedicated-server gate for the guaranteed stronghold and portal room. */
final class RingWorldStrongholdTest {
    private static int ticks;
    private static boolean finished;

    private RingWorldStrongholdTest() { }

    static void tick(MinecraftServer server) {
        if (!Boolean.getBoolean("ringworld.strongholdTest") || finished || ++ticks < 20) return;
        finished = true;
        try {
            verify(server);
            RingWorldMod.LOGGER.info("[stronghold-test] PASS");
        } catch (Throwable failure) {
            RingWorldMod.LOGGER.error("[stronghold-test] FAILED", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void verify(MinecraftServer server) {
        ServerLevel world = server.getLevel(Level.OVERWORLD);
        if (world == null) throw new IllegalStateException("Overworld is unavailable");
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        verifyPeriodicHeightQueries(world, geometry);
        verifyFiniteRims(world, geometry);
        if (!RingStructurePolicy.get(world).guaranteesStronghold()) {
            throw new IllegalStateException("Fresh test world did not persist its stronghold policy");
        }
        RingStrongholdPlacement.StartChunk expected =
                RingStrongholdPlacement.guaranteedStart(world.getSeed(), geometry);

        Structure stronghold = world.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValueOrThrow(BuiltinStructures.STRONGHOLD);
        ChunkAccess startChunk = world.getChunkSource().getChunk(
                expected.chunkX(), expected.chunkZ(), ChunkStatus.STRUCTURE_STARTS, true);
        if (startChunk == null) throw new IllegalStateException("Stronghold start chunk did not load");
        StructureStart start = world.structureManager().getStartForStructure(
                SectionPos.bottomOf(startChunk), stronghold, startChunk);
        if (start == null || !start.isValid()) {
            throw new IllegalStateException("Guaranteed stronghold start was not generated at " + expected);
        }

        StructurePiece portal = start.getPieces().stream()
                .filter(StrongholdPieces.PortalRoom.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Stronghold has no portal room"));
        BoundingBox strongholdBox = start.getBoundingBox();
        if (strongholdBox.minX() < 0 || strongholdBox.maxX() >= geometry.circumferenceBlocks()
                || strongholdBox.minZ() < geometry.minWidthZ()
                || strongholdBox.maxZ() > geometry.maxWidthZ()) {
            throw new IllegalStateException("Stronghold leaves canonical ring bounds: " + strongholdBox);
        }
        BoundingBox portalBox = portal.getBoundingBox();
        if (portalBox.minX() < 0 || portalBox.maxX() >= geometry.circumferenceBlocks()
                || portalBox.minZ() < geometry.minWidthZ()
                || portalBox.maxZ() > geometry.maxWidthZ()) {
            throw new IllegalStateException("Portal room leaves canonical ring bounds: " + portalBox);
        }

        for (int chunkX = SectionPos.blockToSectionCoord(portalBox.minX());
             chunkX <= SectionPos.blockToSectionCoord(portalBox.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(portalBox.minZ());
                 chunkZ <= SectionPos.blockToSectionCoord(portalBox.maxZ()); chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }

        int frames = 0;
        BlockPos firstFrame = null;
        for (int x = portalBox.minX(); x <= portalBox.maxX(); x++) {
            for (int y = portalBox.minY(); y <= portalBox.maxY(); y++) {
                for (int z = portalBox.minZ(); z <= portalBox.maxZ(); z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (world.getBlockState(position).is(Blocks.END_PORTAL_FRAME)) {
                        frames++;
                        if (firstFrame == null) firstFrame = position;
                        world.setBlock(position, world.getBlockState(position).setValue(
                                EndPortalFrameBlock.HAS_EYE, true), 2);
                    }
                }
            }
        }
        if (frames != 12) throw new IllegalStateException("Expected 12 End portal frames, found " + frames);
        BlockPattern.BlockPatternMatch activated =
                EndPortalFrameBlock.getOrCreatePortalShape().find(world, firstFrame);
        if (activated == null) {
            throw new IllegalStateException("The generated frame orientation cannot activate an End portal");
        }

        BlockPos startOrigin = new BlockPos(
                expected.chunkX() * 16 + 8, 32, expected.chunkZ() * 16 + 8);
        BlockPos firstLocated = world.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED, startOrigin, 100, false);
        if (firstLocated == null) throw new IllegalStateException("Initial locate query returned no stronghold");
        BlockPos canonicalLocator = new BlockPos(
                geometry.wrapBlockX(firstLocated.getX()), firstLocated.getY(), firstLocated.getZ());
        int originX = geometry.wrapBlockX(canonicalLocator.getX()
                + geometry.circumferenceBlocks() / 2 + 1);
        BlockPos origin = new BlockPos(originX, canonicalLocator.getY(), canonicalLocator.getZ());
        BlockPos located = world.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED, origin, 100, false);
        if (located == null) throw new IllegalStateException("Eye/locate query returned no stronghold");
        int expectedImageX = (int)Math.round(
                geometry.nearestImageX(canonicalLocator.getX(), origin.getX()));
        if (located.getX() != expectedImageX
                || geometry.wrapBlockX(located.getX()) != geometry.wrapBlockX(canonicalLocator.getX())) {
            throw new IllegalStateException("Locator returned the wrong periodic image: expectedX="
                    + expectedImageX + ", actual=" + located);
        }

        EyeOfEnder eye = new EyeOfEnder(world,
                geometry.circumferenceBlocks() - 0.25, 100.0, 0.0);
        eye.signalTo(new Vec3(geometry.circumferenceBlocks() + 100.0, 100.0, 0.0));
        eye.setPos(geometry.circumferenceBlocks() + 0.25, 100.0, 0.0);
        RingWorldServer.canonicalizeEntityPosition(eye, geometry);
        eye.setPos(20.0, 100.0, 0.0);
        eye.setDeltaMovement(Vec3.ZERO);
        eye.tick();
        if (eye.getDeltaMovement().x >= 0.0) {
            throw new IllegalStateException("Folded Eye retained its old-chart target: velocity="
                    + eye.getDeltaMovement());
        }

        RingWorldMod.LOGGER.info(
                "[stronghold-test] startChunk={}, pieces={}, strongholdBox={}, portalBox={}, frames={}, origin={}, located={}, eyeFoldVx={}",
                expected, start.getPieces().size(), strongholdBox, portalBox, frames, origin, located,
                eye.getDeltaMovement().x);
    }

    /** Runtime-only check: generated boundary/exterior chunks honour saved rim height. */
    private static void verifyFiniteRims(ServerLevel world, RingGeometry geometry) {
        int x = 0;
        int chunkX = SectionPos.blockToSectionCoord(x);
        int lowerRimZ = geometry.minWidthZ();
        int upperRimZ = geometry.maxWidthZ();
        int wallTopExclusive = world.getMinY() + RingWorldSettings.get(world).wallHeightBlocks();
        world.getChunk(chunkX, geometry.minChunkZ());
        world.getChunk(chunkX, geometry.maxChunkZ());
        world.getChunk(chunkX, geometry.minChunkZ() - 1);
        world.getChunk(chunkX, geometry.maxChunkZ() + 1);
        for (int y = world.getMinY(); y < wallTopExclusive; y++) {
            if (!RingGenerationBoundary.isRimMaterial(world.getBlockState(new BlockPos(x, y, lowerRimZ)))
                    || !RingGenerationBoundary.isRimMaterial(world.getBlockState(new BlockPos(x, y, upperRimZ)))) {
                throw new IllegalStateException("Finite rim material is missing at Y=" + y);
            }
        }
        if (wallTopExclusive < world.getMaxY()
                && (!world.getBlockState(new BlockPos(x, wallTopExclusive, lowerRimZ)).isAir()
                || !world.getBlockState(new BlockPos(x, wallTopExclusive, upperRimZ)).isAir())) {
            throw new IllegalStateException("Finite rim continues above saved wall top Y="
                    + wallTopExclusive);
        }
        if (!world.getBlockState(new BlockPos(x, world.getMinY(), lowerRimZ - 1)).isAir()
                || !world.getBlockState(new BlockPos(x, world.getMinY(), upperRimZ + 1)).isAir()) {
            throw new IllegalStateException("Exterior finite-width terrain is not void");
        }
        RingWorldMod.LOGGER.info(
                "[stronghold-test] rims lowerZ={} upperZ={} wallTopY={} exteriorVoid=true",
                lowerRimZ, upperRimZ, wallTopExclusive);
    }

    /**
     * Structure placement reaches {@link ChunkGenerator#getBaseHeight} and
     * {@link ChunkGenerator#getBaseColumn} before a chunk exists. Both query
     * paths must see the same cylindrical sampler at canonical X and its
     * periodic alias. The remote canonical positions must also match their
     * generated noise-complete terrain. X=0 remains an alias test because the
     * server's spawn preparation may have already advanced that chunk past
     * noise generation. This is intentionally a real-server assertion because
     * the router is installed by required mixins.
     */
    private static void verifyPeriodicHeightQueries(ServerLevel world, RingGeometry geometry) {
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        RandomState randomState = world.getChunkSource().randomState();
        int circumference = geometry.circumferenceBlocks();
        int[] canonicalXs = {0, circumference / 4 + 7, circumference / 2 + 3};
        int[] remoteTerrainXs = {circumference / 4 + 7, circumference / 2 + 3};
        int z = 0;
        for (int canonicalX : canonicalXs) {
            int aliasX = canonicalX + circumference;
            int canonicalHeight = generator.getBaseHeight(
                    canonicalX, z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
            int aliasHeight = generator.getBaseHeight(
                    aliasX, z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
            if (canonicalHeight != aliasHeight) {
                throw new IllegalStateException("Periodic base-height mismatch at canonicalX="
                        + canonicalX + ", aliasX=" + aliasX + ": "
                        + canonicalHeight + " != " + aliasHeight);
            }

            NoiseColumn canonicalColumn = generator.getBaseColumn(canonicalX, z, world, randomState);
            NoiseColumn aliasColumn = generator.getBaseColumn(aliasX, z, world, randomState);
            for (int y = world.getMinY(); y < world.getMaxY(); y++) {
                if (!canonicalColumn.getBlock(y).equals(aliasColumn.getBlock(y))) {
                    throw new IllegalStateException("Periodic base-column mismatch at canonicalX="
                            + canonicalX + ", aliasX=" + aliasX + ", y=" + y);
                }
            }
        }

        for (int canonicalX : remoteTerrainXs) {
            int chunkX = SectionPos.blockToSectionCoord(canonicalX);
            int chunkZ = SectionPos.blockToSectionCoord(z);
            if (world.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
                throw new IllegalStateException("Remote base-height terrain chunk was already fully loaded at X="
                        + canonicalX + ", Z=" + z);
            }
            int canonicalHeight = generator.getBaseHeight(
                    canonicalX, z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
            ChunkAccess terrain = world.getChunkSource().getChunk(
                    chunkX, chunkZ, ChunkStatus.NOISE, true);
            if (terrain == null) {
                throw new IllegalStateException("Canonical terrain did not load for base-height check at X="
                        + canonicalX + ", Z=" + z);
            }
            int terrainHeight = terrain.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                    Math.floorMod(canonicalX, 16), Math.floorMod(z, 16)) + 1;
            if (canonicalHeight != terrainHeight) {
                throw new IllegalStateException("Base-height differs from canonical generated terrain at X="
                        + canonicalX + ", Z=" + z + ": query=" + canonicalHeight
                        + ", terrain=" + terrainHeight);
            }

        }
    }
}
