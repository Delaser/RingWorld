package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/** Projects AI path targets into the circumference image nearest the mob. */
@Mixin(PathNavigation.class)
abstract class EntityNavigationMixin {
    @Shadow @Final protected Mob mob;
    @Shadow @Final protected Level level;

    @ModifyVariable(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), argsOnly = true)
    private Set<BlockPos> ringworld$nearestPathTargets(Set<BlockPos> targets) {
        if (!(level instanceof ServerLevel serverWorld)
                || serverWorld.dimension() != Level.OVERWORLD || targets.isEmpty()) return targets;

        RingTopology topology = new RingTopology(RingWorldServer.geometryFor(serverWorld));
        Set<BlockPos> projected = new LinkedHashSet<>(targets.size());
        boolean changed = false;
        for (BlockPos target : targets) {
            int imageX = topology.imageBlockNear(target.getX(), mob.getX());
            changed |= imageX != target.getX();
            projected.add(imageX == target.getX() ? target
                    : new BlockPos(imageX, target.getY(), target.getZ()));
        }
        return changed ? Set.copyOf(projected) : targets;
    }
}
