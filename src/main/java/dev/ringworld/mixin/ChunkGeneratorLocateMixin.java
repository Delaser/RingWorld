package dev.ringworld.mixin;

import com.mojang.datafixers.util.Pair;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Returns a stronghold locator in the periodic image nearest its caller. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorLocateMixin {
    @Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
    private void ringworld$nearestStrongholdImage(
            ServerLevel world, HolderSet<Structure> structures, BlockPos origin,
            int searchRadius, boolean skipKnownStructures,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        Pair<BlockPos, Holder<Structure>> result = cir.getReturnValue();
        if (result == null || world.dimension() != Level.OVERWORLD
                || !(result.getSecond().value() instanceof StrongholdStructure)) return;

        RingGeometry geometry = RingWorldServer.geometryFor(world);
        BlockPos canonical = result.getFirst();
        int imageX = (int)Math.round(geometry.nearestImageX(canonical.getX(), origin.getX()));
        if (imageX != canonical.getX()) {
            cir.setReturnValue(Pair.of(new BlockPos(imageX, canonical.getY(), canonical.getZ()),
                    result.getSecond()));
        }
    }
}
