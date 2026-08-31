package dev.ringworld.server;

import com.mojang.datafixers.util.Pair;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingStrongholdPlacement;
import dev.ringworld.world.RingMonumentPlacement;
import dev.ringworld.world.RingMonumentResolution;
import dev.ringworld.world.RingStructurePolicy;
import dev.ringworld.world.RingSeamTerrainAudit;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldSettings;
import dev.ringworld.world.RingMacroTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Disposable dedicated-server gate for the guaranteed stronghold and portal room. */
public final class RingWorldStrongholdTest {
    private static int ticks;
    private static boolean finished;

    private RingWorldStrongholdTest() { }

    public static void tick(MinecraftServer server) {
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
        boolean worldgenMatrix = Boolean.getBoolean("ringworld.worldgenMatrix");
        verifyPeriodicHeightQueries(world, geometry);
        verifyOptionalGeneration(world, geometry);
        if (worldgenMatrix) verifySeamWorldgenSample(world, geometry);
        verifyFiniteRims(world, geometry);
        if (!RingStructurePolicy.get(world).guaranteesStronghold()) {
            throw new IllegalStateException("Fresh test world did not persist its stronghold policy");
        }
        verifyGuaranteedMonument(world, geometry, worldgenMatrix);
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
        boolean graphTooWideX = (long) strongholdBox.maxX() - strongholdBox.minX()
                >= geometry.circumferenceBlocks();
        boolean graphTooWideZ = (long) strongholdBox.maxZ() - strongholdBox.minZ()
                >= geometry.widthBlocks();
        boolean unexpectedGraphEscape = (!graphTooWideX
                && (strongholdBox.minX() < 0
                    || strongholdBox.maxX() >= geometry.circumferenceBlocks()))
                || (!graphTooWideZ
                    && (strongholdBox.minZ() < geometry.minWidthZ()
                        || strongholdBox.maxZ() > geometry.maxWidthZ()));
        if (unexpectedGraphEscape) {
            throw new IllegalStateException("Stronghold leaves canonical ring bounds: " + strongholdBox);
        }
        BoundingBox portalBox = portal.getBoundingBox();
        BoundingBox protectedPortalBox = portalBox.inflatedBy(
                RingStrongholdPlacement.TERRAIN_ADJUSTMENT_MARGIN_BLOCKS);
        if (protectedPortalBox.minX() < 0
                || protectedPortalBox.maxX() >= geometry.circumferenceBlocks()
                || protectedPortalBox.minZ() < geometry.minWidthZ()
                || protectedPortalBox.maxZ() > geometry.maxWidthZ()) {
            throw new IllegalStateException(
                    "Portal room terrain envelope leaves canonical ring bounds: "
                            + protectedPortalBox);
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

    private static void verifyOptionalGeneration(ServerLevel world, RingGeometry geometry) {
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingStructurePolicy policy = RingStructurePolicy.get(world);
        if (settings.generationSettings().moreStructures() != policy.increasesStructureDensity()) {
            throw new IllegalStateException("saved structure-density policy does not match world settings");
        }
        if (!settings.generationSettings().continuousRiver()) return;
        RingMacroTerrain macro = new RingMacroTerrain(
                geometry, settings.generatorSeed(), settings.generationSettings());
        int riverBiomes = 0;
        int seaLevel = world.getSeaLevel();
        for (int sample = 0; sample < 8; sample++) {
            int x = sample * geometry.circumferenceBlocks() / 8;
            int z = (int)Math.round(macro.riverCenterZ(x));
            world.getChunk(x >> 4, z >> 4);
            if (world.getBiome(new BlockPos(x, seaLevel, z)).is(BiomeTags.IS_RIVER)) riverBiomes++;
            int height = world.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (height > seaLevel + 2) {
                throw new IllegalStateException("continuous river rose above its channel at "
                        + x + "," + z + ": " + height
                        + "; top=" + world.getBlockState(new BlockPos(x, height - 1, z))
                        + ", sea=" + world.getBlockState(new BlockPos(x, seaLevel, z))
                        + ", bed=" + world.getBlockState(new BlockPos(x, seaLevel - 7, z)));
            }
        }
        if (riverBiomes != 8) {
            throw new IllegalStateException("continuous river biome coverage was " + riverBiomes + "/8");
        }
        RingWorldMod.LOGGER.info("[stronghold-test] continuous river=8/8 biome/channel samples; moreStructures={}",
                policy.increasesStructureDensity());
    }

    /**
     * Generates a bounded strip on both canonical sides of the seam and
     * audits ordinary biome, carver, feature, structure, reference, loot, and
     * structure-spawn metadata without claiming that every finite seed must
     * contain every vanilla structure.
     */
    private static void verifySeamWorldgenSample(ServerLevel world, RingGeometry geometry) {
        Map<String, net.minecraft.tags.TagKey<Biome>> families = new LinkedHashMap<>();
        families.put("ocean", BiomeTags.IS_OCEAN);
        families.put("beach", BiomeTags.IS_BEACH);
        families.put("river", BiomeTags.IS_RIVER);
        families.put("mountain", BiomeTags.IS_MOUNTAIN);
        families.put("badlands", BiomeTags.IS_BADLANDS);
        families.put("taiga", BiomeTags.IS_TAIGA);
        families.put("jungle", BiomeTags.IS_JUNGLE);
        families.put("forest", BiomeTags.IS_FOREST);
        families.put("savanna", BiomeTags.IS_SAVANNA);

        Set<String> sampledFamilies = new HashSet<>();
        Set<String> sampledBiomes = new HashSet<>();
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        var sampler = ((RingWorldGeneratorAccess) generator)
                .ringworld$getPeriodicClimateSampler(world.getChunkSource().randomState());
        int sampleStep = 32;
        for (int x = 0; x < geometry.circumferenceBlocks(); x += sampleStep) {
            for (int z = geometry.minWidthZ() + 8; z <= geometry.maxWidthZ(); z += sampleStep) {
                for (int y : new int[]{0, 64, 128}) {
                    Holder<Biome> canonical = biomeSource.getNoiseBiome(
                            Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4), sampler);
                    Holder<Biome> alias = biomeSource.getNoiseBiome(
                            Math.floorDiv(x + geometry.circumferenceBlocks(), 4),
                            Math.floorDiv(y, 4), Math.floorDiv(z, 4), sampler);
                    if (!canonical.equals(alias)) {
                        throw new IllegalStateException("Periodic biome mismatch at "
                                + new BlockPos(x, y, z));
                    }
                    if (!canonical.is(BiomeTags.IS_OVERWORLD)) {
                        throw new IllegalStateException("Non-Overworld biome sampled in ring: "
                                + canonical.unwrapKey().orElse(null));
                    }
                    String biomeId = canonical.unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElse("unregistered");
                    sampledBiomes.add(biomeId);
                    classifyBiome(biomeId, sampledFamilies);
                    families.forEach((name, tag) -> {
                        if (canonical.is(tag)) sampledFamilies.add(name);
                    });
                }
            }
        }
        if (sampledFamilies.isEmpty()) {
            throw new IllegalStateException("Biome family sampling found no recognized Overworld family");
        }

        int circumferenceChunks = geometry.circumferenceChunks();
        int seamDepthChunks = Math.min(4, circumferenceChunks / 2);
        Set<Integer> sampleChunkXs = new LinkedHashSet<>();
        for (int offset = 0; offset < seamDepthChunks; offset++) {
            sampleChunkXs.add(offset);
            sampleChunkXs.add(circumferenceChunks - 1 - offset);
        }

        AtomicLong caveAir = new AtomicLong();
        AtomicLong ores = new AtomicLong();
        AtomicLong logs = new AtomicLong();
        int sampledChunks = 0;
        int validStarts = 0;
        int seamCrossingStarts = 0;
        int references = 0;
        int lootContainers = 0;
        int structuresWithSpawnOverrides = 0;
        Set<String> startKeys = new HashSet<>();
        Set<String> structureIds = new HashSet<>();
        Set<String> crossingStructureIds = new HashSet<>();
        Set<String> spawnOverrideStructureIds = new HashSet<>();
        Set<String> lootPositions = new HashSet<>();
        Set<String> referenceKeys = new HashSet<>();
        var structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        int interiorMinimumZ = geometry.minWidthZ() + RingGenerationBoundary.RIM_THICKNESS;
        int interiorMaximumZ = geometry.maxWidthZ() - RingGenerationBoundary.RIM_THICKNESS;
        int[] highSideHeights = new int[interiorMaximumZ - interiorMinimumZ + 1];
        int[] lowSideHeights = new int[highSideHeights.length];

        for (int chunkX : sampleChunkXs) {
            for (int chunkZ = geometry.minChunkZ(); chunkZ <= geometry.maxChunkZ(); chunkZ++) {
                LevelChunk chunk = world.getChunk(chunkX, chunkZ);
                sampledChunks++;
                chunk.findBlocks(state -> state.isAir() || state.is(BlockTags.LOGS) || isOre(state),
                        (position, state) -> {
                            if (state.isAir() && position.getY() < world.getSeaLevel() - 8
                                    && position.getY() > world.getMinY() + 8) caveAir.incrementAndGet();
                            if (state.is(BlockTags.LOGS)) logs.incrementAndGet();
                            if (isOre(state)) ores.incrementAndGet();
                        });
                for (BlockPos position : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(position) instanceof RandomizableContainerBlockEntity container
                            && container.getLootTable() != null) {
                        String lootKey = geometry.wrapBlockX(position.getX()) + ","
                                + position.getY() + "," + position.getZ();
                        if (!lootPositions.add(lootKey)) {
                            throw new IllegalStateException("Duplicate seam loot ownership: " + lootKey);
                        }
                        lootContainers++;
                    }
                }
                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (!start.isValid()) continue;
                    validStarts++;
                    String structureId = String.valueOf(structureRegistry.getKey(entry.getKey()));
                    structureIds.add(structureId);
                    String startKey = structureId + '@' + start.getChunkPos().x() + ',' + start.getChunkPos().z();
                    if (!startKeys.add(startKey)) {
                        throw new IllegalStateException("Duplicate seam structure start ownership: " + startKey);
                    }
                    if (start.getChunkPos().x() < 0 || start.getChunkPos().x() >= circumferenceChunks) {
                        throw new IllegalStateException("Non-canonical seam structure start: " + startKey);
                    }
                    BoundingBox box = start.getBoundingBox();
                    if (box.minX() < 0 || box.maxX() >= geometry.circumferenceBlocks()) {
                        seamCrossingStarts++;
                        crossingStructureIds.add(structureId);
                    }
                    if (!entry.getKey().spawnOverrides().isEmpty()) {
                        structuresWithSpawnOverrides++;
                        spawnOverrideStructureIds.add(structureId);
                    }
                }
                for (var referenceEntry : chunk.getAllReferences().entrySet()) {
                    String structureId = String.valueOf(structureRegistry.getKey(referenceEntry.getKey()));
                    for (long reference : referenceEntry.getValue()) {
                        int referenceX = ChunkPos.getX(reference);
                        if (referenceX < 0 || referenceX >= circumferenceChunks) {
                            throw new IllegalStateException("Non-canonical seam structure reference X="
                                    + referenceX + " from " + chunk.getPos());
                        }
                        String referenceKey = chunk.getPos().x() + "," + chunk.getPos().z()
                                + ':' + structureId + '@' + referenceX + ',' + ChunkPos.getZ(reference);
                        if (!referenceKeys.add(referenceKey)) {
                            throw new IllegalStateException("Duplicate seam structure reference: " + referenceKey);
                        }
                        references++;
                    }
                }
            }
        }
        for (int z = interiorMinimumZ; z <= interiorMaximumZ; z++) {
            int index = z - interiorMinimumZ;
            highSideHeights[index] = world.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    geometry.circumferenceBlocks() - 1, z);
            lowSideHeights[index] = world.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, z);
        }
        RingSeamTerrainAudit.Report seam = RingSeamTerrainAudit.inspect(
                highSideHeights, lowSideHeights);
        if (!seam.passes()) {
            throw new IllegalStateException("Broad terrain cliff remains at circumference seam: "
                    + seam);
        }
        if (RingWorldSettings.get(world).terrainNoiseMapping()
                >= RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2
                && !seam.passesSmoothJoin()) {
            throw new IllegalStateException("Terrain join is not smooth under the current mapping: "
                    + seam);
        }
        if (caveAir.get() == 0 || ores.get() == 0) {
            throw new IllegalStateException("Seam strip lacks ordinary carver/ore evidence: caveAir="
                    + caveAir + ", ores=" + ores);
        }
        RingWorldMod.LOGGER.info(
                "[worldgen-matrix] seed={} layout={}x{} biomeFamilies={} biomeIds={} chunks={} caveAir={} ores={} logs={} starts={} structureIds={} crossingStarts={} crossingStructureIds={} references={} lootContainers={} structuresWithSpawnOverrides={} spawnOverrideStructureIds={} seamTerrain={}",
                world.getSeed(), geometry.circumferenceBlocks(), geometry.widthBlocks(),
                sampledFamilies.stream().sorted().toList(), sampledBiomes.stream().sorted().toList(),
                sampledChunks, caveAir, ores, logs,
                validStarts, structureIds.stream().sorted().toList(), seamCrossingStarts,
                crossingStructureIds.stream().sorted().toList(), references, lootContainers,
                structuresWithSpawnOverrides, spawnOverrideStructureIds.stream().sorted().toList(),
                seam);
    }

    private static void classifyBiome(String biomeId, Set<String> families) {
        String path = biomeId.startsWith("minecraft:") ? biomeId.substring("minecraft:".length()) : biomeId;
        if (path.equals("plains") || path.equals("sunflower_plains") || path.equals("snowy_plains")) {
            families.add("plains");
        }
        if (path.equals("desert")) families.add("desert");
        if (path.equals("swamp") || path.equals("mangrove_swamp")) families.add("swamp");
        if (path.equals("mushroom_fields")) families.add("mushroom");
        if (path.equals("dripstone_caves") || path.equals("lush_caves") || path.equals("deep_dark")) {
            families.add("cave");
        }
        if (path.startsWith("snowy_") || path.startsWith("frozen_") || path.equals("ice_spikes")
                || path.equals("grove") || path.equals("frozen_ocean")
                || path.equals("deep_frozen_ocean")) {
            families.add("snowy");
        }
    }

    private static boolean isOre(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
                || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
    }

    /** Runtime proof for policy persistence, forced placement, bounds, and periodic locate. */
    private static void verifyGuaranteedMonument(
            ServerLevel world, RingGeometry geometry, boolean allowUnsatisfied) {
        RingMonumentResolution resolution = RingStructurePolicy.get(world).oceanMonument();
        RingMonumentResolution.Candidate candidate = resolution.candidate();
        if (resolution.status() != RingMonumentResolution.Status.SATISFIED || candidate == null) {
            if (!RingMonumentPlacement.hasCandidateSpace(geometry)
                    && resolution.status() == RingMonumentResolution.Status.DISABLED
                    && candidate == null) {
                RingWorldMod.LOGGER.info(
                        "[stronghold-test] monumentStatus=DISABLED: width={} cannot fit the required margins",
                        geometry.widthBlocks());
                return;
            }
            if (allowUnsatisfied
                    && resolution.status() == RingMonumentResolution.Status.UNSATISFIED
                    && candidate == null
                    && resolution.reason() != null) {
                RingWorldMod.LOGGER.info(
                        "[worldgen-matrix] monumentStatus={} monumentReason={} monumentCandidate=null",
                        resolution.status(), resolution.reason());
                return;
            }
            throw new IllegalStateException("Test seed did not satisfy its requested monument: " + resolution);
        }

        var structures = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> monumentHolder = structures.get(BuiltinStructures.OCEAN_MONUMENT)
                .orElseThrow(() -> new IllegalStateException("Built-in ocean monument holder is unavailable"));
        Structure monument = monumentHolder.value();
        if (monument.spawnOverrides().isEmpty()) {
            throw new IllegalStateException("Ocean monument has no guardian spawn override metadata");
        }
        int canonicalLocateX = candidate.chunkX() * 16;
        // Query from the adjacent presentation chart so vanilla's flat
        // random-spread scan cannot discover this arbitrary forced candidate.
        int originX = canonicalLocateX - geometry.circumferenceBlocks() + 1;
        BlockPos origin = new BlockPos(originX, world.getSeaLevel(), candidate.chunkZ() * 16);
        Pair<BlockPos, Holder<Structure>> located = world.getChunkSource().getGenerator()
                .findNearestMapStructure(world, HolderSet.direct(monumentHolder), origin, 0, false);
        if (located == null) throw new IllegalStateException("Monument locate returned no result");
        int expectedImageX = (int) Math.round(geometry.nearestImageX(canonicalLocateX, originX));
        if (located.getFirst().getX() != expectedImageX
                || geometry.wrapBlockX(located.getFirst().getX()) != geometry.wrapBlockX(canonicalLocateX)) {
            throw new IllegalStateException("Monument locate returned wrong periodic image: expectedX="
                    + expectedImageX + ", actual=" + located.getFirst());
        }

        // On a fresh fixture the locate call above must generate this
        // canonical STRUCTURE_STARTS chunk; on resume it must reuse it.
        ChunkAccess startChunk = world.getChunkSource().getChunk(
                candidate.chunkX(), candidate.chunkZ(), ChunkStatus.STRUCTURE_STARTS, true);
        if (startChunk == null) throw new IllegalStateException("Monument start chunk did not load");
        StructureStart start = world.structureManager().getStartForStructure(
                SectionPos.bottomOf(startChunk), monument, startChunk);
        if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
            throw new IllegalStateException("Guaranteed monument did not generate at " + candidate);
        }
        BoundingBox box = start.getBoundingBox();
        if (box.minX() < 0 || box.maxX() >= geometry.circumferenceBlocks()
                || box.minZ() < geometry.minWidthZ() || box.maxZ() > geometry.maxWidthZ()) {
            throw new IllegalStateException("Monument leaves canonical ring bounds: " + box);
        }

        boolean referenceableBeforeLocate = start.canBeReferenced();
        Pair<BlockPos, Holder<Structure>> unexplored = world.getChunkSource().getGenerator()
                .findNearestMapStructure(world, HolderSet.direct(monumentHolder), origin, 0, true);
        if (referenceableBeforeLocate
                && (unexplored == null || !unexplored.getSecond().is(BuiltinStructures.OCEAN_MONUMENT))) {
            throw new IllegalStateException("Unexplored monument locate/reference path failed");
        }
        if (!referenceableBeforeLocate && unexplored != null) {
            throw new IllegalStateException("Already-referenced monument was returned as unexplored");
        }
        RingWorldMod.LOGGER.info(
                "[stronghold-test] monumentCandidate={}, box={}, pieces={}, origin={}, located={}, referenceableBefore={}, unexplored={}",
                candidate, box, start.getPieces().size(), origin, located.getFirst(), referenceableBeforeLocate,
                unexplored == null ? null : unexplored.getFirst());
        if (allowUnsatisfied) {
            RingWorldMod.LOGGER.info(
                    "[worldgen-matrix] monumentStatus={} monumentReason={} monumentCandidate={},{} spawnOverrideEntries={}",
                    resolution.status(), resolution.reason(), candidate.chunkX(), candidate.chunkZ(),
                    monument.spawnOverrides().size());
        }
    }

    /** Runtime-only check: generated boundary/exterior chunks honour saved rim height. */
    private static void verifyFiniteRims(ServerLevel world, RingGeometry geometry) {
        int x = 0;
        int chunkX = SectionPos.blockToSectionCoord(x);
        int lowerRimZ = geometry.minWidthZ();
        int upperRimZ = geometry.maxWidthZ();
        int wallTopExclusive = RingGenerationBoundary.wallTopExclusive(
                world.getMinY(), world.getMaxY() - world.getMinY(),
                RingWorldSettings.get(world).wallHeightBlocks());
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
        int[] canonicalXs = {
                0, circumference / 4, circumference / 2,
                circumference * 3 / 4, circumference - 1
        };
        int[] sampleZs = {
                Math.max(geometry.minWidthZ() + 8, -120),
                0,
                Math.min(geometry.maxWidthZ() - 8, 120)
        };
        for (int canonicalX : canonicalXs) {
            for (int z : sampleZs) {
                int aliasX = canonicalX + circumference;
                int chunkX = SectionPos.blockToSectionCoord(canonicalX);
                int chunkZ = SectionPos.blockToSectionCoord(z);
                boolean terrainWasLoaded = world.getChunkSource().getChunkNow(
                        chunkX, chunkZ) != null;
                // Spawn preparation and its periodic neighbour can advance
                // either seam chunk without leaving it resident in getChunkNow().
                // Keep both edge columns as alias checks rather than
                // misclassifying them as untouched NOISE probes.
                boolean seamCardinal = canonicalX == 0 || canonicalX == circumference - 1;
                boolean compareFreshNoise = !seamCardinal && !Boolean.getBoolean(
                        "ringworld.strongholdTestResume") && !terrainWasLoaded;
                int canonicalHeight = generator.getBaseHeight(
                        canonicalX, z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
                int aliasHeight = generator.getBaseHeight(
                        aliasX, z, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
                if (canonicalHeight != aliasHeight) {
                    throw new IllegalStateException("Periodic base-height mismatch at canonicalX="
                            + canonicalX + ", aliasX=" + aliasX + ", z=" + z + ": "
                            + canonicalHeight + " != " + aliasHeight);
                }

                NoiseColumn canonicalColumn = generator.getBaseColumn(
                        canonicalX, z, world, randomState);
                NoiseColumn aliasColumn = generator.getBaseColumn(aliasX, z, world, randomState);
                for (int y = world.getMinY(); y < world.getMaxY(); y++) {
                    if (!canonicalColumn.getBlock(y).equals(aliasColumn.getBlock(y))) {
                        throw new IllegalStateException("Periodic base-column mismatch at canonicalX="
                                + canonicalX + ", aliasX=" + aliasX + ", z=" + z + ", y=" + y);
                    }
                }

                if (compareFreshNoise) {
                    ChunkAccess terrain = world.getChunkSource().getChunk(
                            chunkX, chunkZ, ChunkStatus.NOISE, true);
                    if (terrain == null) {
                        throw new IllegalStateException(
                                "Canonical terrain did not load for base-height check at X="
                                        + canonicalX + ", Z=" + z);
                    }
                    int terrainHeight = terrain.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                            Math.floorMod(canonicalX, 16), Math.floorMod(z, 16)) + 1;
                    if (canonicalHeight != terrainHeight) {
                        throw new IllegalStateException(
                                "Base-height differs from canonical generated terrain at X="
                                        + canonicalX + ", Z=" + z + ": query=" + canonicalHeight
                                        + ", terrain=" + terrainHeight);
                    }
                }
                RingWorldMod.LOGGER.info(
                        "[stronghold-test] terrain cardinal X={} Z={} height={} periodic=true freshNoise={}",
                        canonicalX, z, canonicalHeight, compareFreshNoise);
            }
        }
    }
}
