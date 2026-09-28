package dev.ringworld.mixin;

import com.mojang.datafixers.util.Pair;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingMonumentResolution;
import dev.ringworld.world.RingStructurePolicy;
import dev.ringworld.world.RingStructureStateAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes structure locating respect the one canonical, periodic Overworld plane. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorLocateMixin {
    @Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
    private void ringworld$nearestPeriodicStructureImage(
            ServerLevel world, HolderSet<Structure> structures, BlockPos origin,
            int searchRadius, boolean skipKnownStructures,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (world.dimension() != Level.OVERWORLD) return;

        RingGeometry geometry = RingWorldServer.geometryFor(world);
        Pair<BlockPos, Holder<Structure>> result = cir.getReturnValue();
        Pair<BlockPos, Holder<Structure>> guaranteedMonument = ringworld$locateGuaranteedMonument(
                world, structures, origin, searchRadius, skipKnownStructures, geometry, result);
        if (guaranteedMonument != null) result = guaranteedMonument;
        Pair<BlockPos, Holder<Structure>> additional = ringworld$locateAdditionalStructures(
                world, structures, origin, searchRadius, skipKnownStructures, geometry, result);
        if (additional != null) result = additional;

        if (result == null) return;
        BlockPos canonical = result.getFirst();
        int imageX = (int) Math.round(geometry.nearestImageX(canonical.getX(), origin.getX()));
        if (imageX != canonical.getX()) {
            result = Pair.of(new BlockPos(imageX, canonical.getY(), canonical.getZ()), result.getSecond());
        }
        cir.setReturnValue(result);
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> ringworld$locateAdditionalStructures(
            ServerLevel world, HolderSet<Structure> structures, BlockPos origin,
            int searchRadius, boolean skipKnownStructures, RingGeometry geometry,
            @Nullable Pair<BlockPos, Holder<Structure>> vanillaResult) {
        if (!RingStructurePolicy.get(world).increasesStructureDensity()) return null;
        ChunkGeneratorStructureState state = world.getChunkSource().getGeneratorState();
        if (!(state instanceof RingStructureStateAccess access)) return null;
        StructureManager manager = world.structureManager();
        int originChunkX = Math.floorMod(SectionPos.blockToSectionCoord(origin.getX()),
                geometry.circumferenceChunks());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        double bestDistance = vanillaResult == null ? Double.POSITIVE_INFINITY
                : ringworld$periodicDistanceSquared(origin, vanillaResult.getFirst(), geometry);
        Pair<BlockPos, Holder<Structure>> best = null;
        StructureStart bestStart = null;

        for (Holder<Structure> structure : structures) {
            for (StructurePlacement candidatePlacement : state.getPlacementsForStructure(structure)) {
                if (!(candidatePlacement instanceof RandomSpreadStructurePlacement placement)) continue;
                int maximumDelta = (int)Math.min(geometry.circumferenceChunks() / 2L,
                        Math.min(Integer.MAX_VALUE, (long)(searchRadius + 1) * placement.spacing()));
                int minZ = Math.max(geometry.minChunkZ(), originChunkZ - maximumDelta);
                int maxZ = Math.min(geometry.maxChunkZ(), originChunkZ + maximumDelta);
                for (int dx = -maximumDelta; dx <= maximumDelta; dx++) {
                    int chunkX = Math.floorMod(originChunkX + dx, geometry.circumferenceChunks());
                    for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                        if (!access.ringworld$isAdditionalStructureCandidate(
                                placement, chunkX, chunkZ)
                                || !placement.isStructureChunk(state, chunkX, chunkZ)) continue;
                        ChunkPos candidateChunk = new ChunkPos(chunkX, chunkZ);
                        StructureCheckResult presence = manager.checkStructurePresence(
                                candidateChunk, structure.value(), placement, skipKnownStructures);
                        if (presence == StructureCheckResult.START_NOT_PRESENT) continue;
                        ChunkAccess chunk = world.getChunk(
                                chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS);
                        StructureStart start = manager.getStartForStructure(
                                SectionPos.bottomOf(chunk), structure.value(), chunk);
                        if (start == null || !start.isValid()) continue;
                        if (skipKnownStructures) {
                            if (!start.canBeReferenced()) continue;
                        }
                        BlockPos locate = placement.getLocatePos(candidateChunk);
                        int imageX = (int)Math.round(geometry.nearestImageX(
                                locate.getX(), origin.getX()));
                        BlockPos image = new BlockPos(imageX, locate.getY(), locate.getZ());
                        double distance = origin.distSqr(image);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = Pair.of(image, structure);
                            bestStart = start;
                        }
                    }
                }
            }
        }
        if (skipKnownStructures && bestStart != null) manager.addReference(bestStart);
        return best;
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> ringworld$locateGuaranteedMonument(
            ServerLevel world, HolderSet<Structure> structures, BlockPos origin,
            int searchRadius, boolean skipKnownStructures, RingGeometry geometry,
            @Nullable Pair<BlockPos, Holder<Structure>> vanillaResult) {
        RingMonumentResolution resolution = RingStructurePolicy.get(world).oceanMonument();
        RingMonumentResolution.Candidate saved = resolution.candidate();
        if (resolution.status() != RingMonumentResolution.Status.SATISFIED || saved == null) return null;

        Holder<Structure> monument = null;
        for (Holder<Structure> structure : structures) {
            if (structure.unwrapKey().filter(BuiltinStructures.OCEAN_MONUMENT::equals).isPresent()) {
                monument = structure;
                break;
            }
        }
        if (monument == null) return null;

        ChunkGeneratorStructureState state = world.getChunkSource().getGeneratorState();
        if (!(state instanceof RingStructureStateAccess access)) return null;
        RandomSpreadStructurePlacement placement = null;
        for (StructurePlacement candidatePlacement : state.getPlacementsForStructure(monument)) {
            if (candidatePlacement instanceof RandomSpreadStructurePlacement randomSpread
                    && access.ringworld$isGuaranteedOceanMonumentCandidate(
                            candidatePlacement, saved.chunkX(), saved.chunkZ())) {
                placement = randomSpread;
                break;
            }
        }
        if (placement == null || !ringworld$withinLocateRadius(
                origin, saved, searchRadius, placement.spacing(), geometry)) return null;

        ChunkPos candidateChunk = new ChunkPos(saved.chunkX(), saved.chunkZ());
        BlockPos locatePos = placement.getLocatePos(candidateChunk);
        int imageX = (int) Math.round(geometry.nearestImageX(locatePos.getX(), origin.getX()));
        BlockPos imagePos = new BlockPos(imageX, locatePos.getY(), locatePos.getZ());
        if (vanillaResult != null && ringworld$periodicDistanceSquared(
                origin, vanillaResult.getFirst(), geometry) <= origin.distSqr(imagePos)) return null;

        StructureManager manager = world.structureManager();
        StructureCheckResult presence = manager.checkStructurePresence(
                candidateChunk, monument.value(), placement, skipKnownStructures);
        if (presence == StructureCheckResult.START_NOT_PRESENT) return null;
        if (!skipKnownStructures && presence == StructureCheckResult.START_PRESENT) {
            return Pair.of(imagePos, monument);
        }

        // This is the same canonical STRUCTURE_STARTS lookup vanilla uses. It
        // may generate the selected start, but never asks for an X alias chunk.
        ChunkAccess chunk = world.getChunk(
                candidateChunk.x(), candidateChunk.z(), ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = manager.getStartForStructure(
                SectionPos.bottomOf(chunk), monument.value(), chunk);
        if (start == null || !start.isValid()) return null;
        if (skipKnownStructures) {
            if (!start.canBeReferenced()) return null;
            manager.addReference(start);
        }
        return Pair.of(imagePos, monument);
    }

    private static boolean ringworld$withinLocateRadius(
            BlockPos origin, RingMonumentResolution.Candidate candidate,
            int searchRadius, int spacing, RingGeometry geometry) {
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        double candidateImageX = geometry.nearestImageX(candidate.chunkX() * 16.0, originChunkX * 16.0);
        long deltaXChunks = Math.round(Math.abs(candidateImageX / 16.0 - originChunkX));
        long deltaZChunks = Math.abs((long) candidate.chunkZ() - originChunkZ);
        long maximumChunks = (long) (searchRadius + 1) * spacing;
        return Math.max(deltaXChunks, deltaZChunks) <= maximumChunks;
    }

    private static double ringworld$periodicDistanceSquared(
            BlockPos origin, BlockPos target, RingGeometry geometry) {
        double deltaX = geometry.shortestCircumferenceDelta(origin.getX(), target.getX());
        double deltaY = target.getY() - origin.getY();
        double deltaZ = target.getZ() - origin.getZ();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }
}
