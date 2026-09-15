package dev.ringworld.world;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.*;
/** Version-owned calls used by the existing runtime fixtures. */
public final class RingMinecraftFixtureAccess {
    private RingMinecraftFixtureAccess() { }
    public static long clockTicks(ServerLevel world, Holder<WorldClock> clock) { return world.clockManager().getInstance(clock).totalTicks(); }
    public static void invulnerable(Entity entity, boolean value) { entity.setPermanentlyInvulnerable(value); }
    public static Either<Player.BedSleepingProblem, Unit> sleep(ServerPlayer player, BlockPos pos) { var state = player.level().getBlockState(pos); var bed = (net.minecraft.world.level.block.AbstractBedBlock)state.getBlock(); return player.startSleepInBed(bed, state, bed.getBedRule(player.level(), pos), pos); }
    public static StructureStart structureStart(ServerLevel world, Structure structure, ChunkAccess chunk) {
        return world.structureManager().getStartForStructure(structure, chunk);
    }
    public static Holder<Biome> biome(BiomeSource source, int x, int y, int z, Climate.Sampler sampler) {
        return source.createResolver(sampler).getNoiseBiome(x, y, z);
    }
    public static ChunkStatus terrainStatus() { return ChunkStatus.TERRAIN; }
}
