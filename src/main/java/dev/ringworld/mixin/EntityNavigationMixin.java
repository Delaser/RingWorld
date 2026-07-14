package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashSet;
import java.util.Set;

/** Projects AI path targets into the circumference image nearest the mob. */
@Mixin(EntityNavigation.class)
abstract class EntityNavigationMixin {
    @Shadow @Final protected MobEntity entity;
    @Shadow @Final protected World world;

    @ModifyVariable(
            method = "findPathToAny(Ljava/util/Set;IZIF)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At("HEAD"), argsOnly = true)
    private Set<BlockPos> ringworld$nearestPathTargets(Set<BlockPos> targets) {
        if (!(world instanceof ServerWorld serverWorld)
                || serverWorld.getRegistryKey() != World.OVERWORLD || targets.isEmpty()) return targets;

        RingTopology topology = new RingTopology(RingWorldServer.geometryFor(serverWorld));
        Set<BlockPos> projected = new LinkedHashSet<>(targets.size());
        boolean changed = false;
        for (BlockPos target : targets) {
            int imageX = topology.imageBlockNear(target.getX(), entity.getX());
            changed |= imageX != target.getX();
            projected.add(imageX == target.getX() ? target
                    : new BlockPos(imageX, target.getY(), target.getZ()));
        }
        return changed ? Set.copyOf(projected) : targets;
    }
}
