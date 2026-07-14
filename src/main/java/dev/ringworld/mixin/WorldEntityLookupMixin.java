package dev.ringworld.mixin;

import com.google.common.collect.Lists;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Splits entity lookup boxes that cross a circumference seam. */
@Mixin(World.class)
abstract class WorldEntityLookupMixin {
    @Shadow protected abstract EntityLookup<Entity> getEntityLookup();

    @Inject(method = "getOtherEntities", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicEntities(Entity except, Box box, Predicate<? super Entity> predicate,
                                            CallbackInfoReturnable<List<Entity>> cir) {
        RingTopology topology = topologyFor(box);
        if (topology == null) return;
        List<Entity> result = Lists.newArrayList();
        Set<Entity> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(box)) {
            getEntityLookup().forEachIntersects(window.canonicalBox(), entity -> {
                if (entity != except && seen.add(entity) && predicate.test(entity)) result.add(entity);
            });
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "collectEntitiesByType(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void ringworld$periodicTypedEntities(TypeFilter<Entity, T> filter, Box box,
                                                                    Predicate<? super T> predicate,
                                                                    List<? super T> result, int limit,
                                                                    CallbackInfo ci) {
        RingTopology topology = topologyFor(box);
        if (topology == null) return;
        Set<T> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(box)) {
            getEntityLookup().forEachIntersects(filter, window.canonicalBox(), entity -> {
                if (seen.add(entity) && predicate.test(entity)) result.add(entity);
                return result.size() >= limit
                        ? net.minecraft.util.function.LazyIterationConsumer.NextIteration.ABORT
                        : net.minecraft.util.function.LazyIterationConsumer.NextIteration.CONTINUE;
            });
            if (result.size() >= limit) break;
        }
        ci.cancel();
    }

    private RingTopology topologyFor(Box box) {
        if (!((Object) this instanceof ServerWorld world) || world.getRegistryKey() != World.OVERWORLD) return null;
        var geometry = RingWorldServer.geometryFor(world);
        if (box.minX >= 0.0 && box.maxX <= geometry.circumferenceBlocks()) return null;
        return new RingTopology(geometry);
    }
}
