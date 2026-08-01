package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingMonumentPlacement;
import dev.ringworld.world.RingMonumentResolution;
import dev.ringworld.world.RingStrongholdPlacement;
import dev.ringworld.world.RingStructureStateAccess;
import dev.ringworld.world.RingStructurePolicy;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.ArrayList;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces vanilla's unbounded stronghold rings with one in-band canonical start. */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin implements RingStructureStateAccess {
    @Shadow @Final private long levelSeed;
    @Shadow @Final private Map<Structure, List<StructurePlacement>> placementsForStructure;
    @Shadow @Final private BiomeSource biomeSource;
    @Shadow @Final private List<Holder<StructureSet>> possibleStructureSets;

    @Unique private volatile @Nullable RingGeometry ringworld$geometry;
    @Unique private volatile boolean ringworld$guaranteeStronghold;
    @Unique private volatile @Nullable List<ChunkPos> ringworld$strongholdPositions;
    @Unique private volatile RingMonumentResolution ringworld$monument = RingMonumentResolution.disabled();
    @Unique private volatile @Nullable StructurePlacement ringworld$monumentPlacement;
    @Unique private volatile @Nullable Structure ringworld$monumentStructure;
    @Unique private volatile int ringworld$monumentSeaLevel;
    @Unique private volatile @Nullable Climate.Sampler ringworld$monumentClimateSampler;
    @Unique private volatile @Nullable IntBinaryOperator ringworld$monumentFloorHeight;
    @Unique private volatile boolean ringworld$monumentCompatible = true;

    @Override
    public void ringworld$setStructurePolicy(RingGeometry geometry, RingStructurePolicy policy, int generatorSeaLevel,
                                             Climate.Sampler periodicClimateSampler,
                                             IntBinaryOperator oceanFloorHeight) {
        ringworld$geometry = geometry;
        ringworld$guaranteeStronghold = policy.guaranteesStronghold();
        ringworld$strongholdPositions = null;
        ringworld$monument = policy.oceanMonument();
        ringworld$monumentSeaLevel = generatorSeaLevel;
        ringworld$monumentClimateSampler = periodicClimateSampler;
        ringworld$monumentFloorHeight = oceanFloorHeight;
        ringworld$monumentCompatible = true;
        ringworld$bindBuiltinMonumentIdentity();
    }

    @Override
    public RingMonumentResolution ringworld$resolvePendingOceanMonument() {
        if (ringworld$monument.status() != RingMonumentResolution.Status.PENDING) return ringworld$monument;
        if (!ringworld$bindBuiltinMonumentIdentity()) {
            return RingMonumentResolution.unsatisfied(
                    RingMonumentResolution.Reason.BUILTIN_REGISTRY_UNAVAILABLE);
        }
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null) return RingMonumentResolution.unsatisfied(
                RingMonumentResolution.Reason.BUILTIN_REGISTRY_UNAVAILABLE);
        RingMonumentPlacement.SearchResult result = RingMonumentPlacement.findCandidate(levelSeed, geometry,
                candidate -> ringworld$monumentPlacement.applyAdditionalChunkRestrictions(candidate.chunkX(), candidate.chunkZ(), levelSeed)
                        && ringworld$monumentPlacement.applyInteractionsWithOtherStructures((ChunkGeneratorStructureState) (Object) this,
                        candidate.chunkX(), candidate.chunkZ()) && ringworld$validMonumentCandidate(candidate));
        if (result.candidate() != null) return RingMonumentResolution.satisfied(result.candidate());
        if (result.checkedCandidates() == 0) return RingMonumentResolution.unsatisfied(
                RingMonumentResolution.Reason.NO_CANDIDATE_IN_BOUNDS);
        return RingMonumentResolution.unsatisfied(result.searchBoundReached()
                ? RingMonumentResolution.Reason.SEARCH_BUDGET_EXHAUSTED
                : RingMonumentResolution.Reason.NO_CANDIDATE_MATCHED_BIOME);
    }

    @Override
    public boolean ringworld$hasCompatibleSavedOceanMonument() {
        if (ringworld$monument.status() != RingMonumentResolution.Status.SATISFIED) return true;
        if (!ringworld$bindBuiltinMonumentIdentity()) {
            ringworld$monumentCompatible = false;
            return false;
        }
        RingMonumentResolution.Candidate saved = ringworld$monument.candidate();
        ringworld$monumentCompatible = saved != null && ringworld$monumentPlacement.applyAdditionalChunkRestrictions(saved.chunkX(), saved.chunkZ(), levelSeed)
                && ringworld$monumentPlacement.applyInteractionsWithOtherStructures((ChunkGeneratorStructureState) (Object) this, saved.chunkX(), saved.chunkZ())
                && ringworld$validMonumentCandidate(new RingMonumentPlacement.Candidate(saved.chunkX(), saved.chunkZ()));
        return ringworld$monumentCompatible;
    }

    @Inject(method = "getRingPositionsFor", at = @At("RETURN"), cancellable = true)
    private void ringworld$guaranteeStronghold(
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<List<ChunkPos>> cir) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null || !ringworld$guaranteeStronghold
                || !ringworld$isStrongholdPlacement(placement)) return;

        List<ChunkPos> positions = ringworld$strongholdPositions;
        if (positions == null) {
            RingStrongholdPlacement.StartChunk start =
                    RingStrongholdPlacement.guaranteedStart(levelSeed, geometry);
            positions = List.of(new ChunkPos(start.chunkX(), start.chunkZ()));
            ringworld$strongholdPositions = positions;
        }
        cir.setReturnValue(positions);
    }

    @Unique
    private boolean ringworld$isStrongholdPlacement(StructurePlacement placement) {
        for (Map.Entry<Structure, List<StructurePlacement>> entry : placementsForStructure.entrySet()) {
            if (entry.getKey() instanceof StrongholdStructure
                    && entry.getValue().contains(placement)) return true;
        }
        return false;
    }

    @Override
    public boolean ringworld$isGuaranteedOceanMonumentCandidate(Object placement, int chunkX, int chunkZ) {
        RingMonumentResolution.Candidate candidate = ringworld$monument.candidate();
        return ringworld$monument.status() == RingMonumentResolution.Status.SATISFIED && ringworld$monumentCompatible
                && placement == ringworld$monumentPlacement && candidate != null
                && candidate.chunkX() == chunkX && candidate.chunkZ() == chunkZ;
    }

    @Unique
    private boolean ringworld$bindBuiltinMonumentIdentity() {
        Holder<StructureSet> setHolder = possibleStructureSets.stream()
                .filter(holder -> holder.unwrapKey().filter(BuiltinStructureSets.OCEAN_MONUMENTS::equals).isPresent())
                .findFirst().orElse(null);
        if (setHolder == null) return false;
        StructureSet set = setHolder.value();
        Holder<Structure> structureHolder = set.structures().stream()
                .map(StructureSet.StructureSelectionEntry::structure)
                .filter(holder -> holder.unwrapKey().filter(BuiltinStructures.OCEAN_MONUMENT::equals).isPresent())
                .findFirst().orElse(null);
        if (structureHolder == null) return false;
        ringworld$monumentPlacement = set.placement();
        ringworld$monumentStructure = structureHolder.value();
        return true;
    }

    @Unique
    private boolean ringworld$validMonumentCandidate(RingMonumentPlacement.Candidate candidate) {
        RingGeometry geometry = ringworld$geometry;
        Structure monument = ringworld$monumentStructure;
        Climate.Sampler climateSampler = ringworld$monumentClimateSampler;
        IntBinaryOperator floorHeight = ringworld$monumentFloorHeight;
        if (geometry == null || monument == null || climateSampler == null || floorHeight == null
                || !RingMonumentPlacement.isConservativelyInBounds(candidate, geometry)) return false;
        int anchorX = candidate.chunkX() * 16 + 8;
        int anchorZ = candidate.chunkZ() * 16 + 8;
        int anchorY = floorHeight.applyAsInt(anchorX, anchorZ);
        int seaLevel = ringworld$monumentSeaLevel;
        Holder<Biome> anchor = biomeSource.getNoiseBiome(
                Math.floorDiv(anchorX, 4), Math.floorDiv(anchorY, 4), Math.floorDiv(anchorZ, 4), climateSampler);
        if (!monument.biomes().contains(anchor)) return false;
        return biomeSource.getBiomesWithin(candidate.chunkX() * 16 + 9, seaLevel,
                        candidate.chunkZ() * 16 + 9,
                        RingMonumentPlacement.SURROUNDING_BIOME_RADIUS_BLOCKS, climateSampler)
                .stream().allMatch(biome -> biome.is(BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING));
    }

}
