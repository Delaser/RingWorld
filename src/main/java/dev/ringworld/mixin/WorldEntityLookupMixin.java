package dev.ringworld.mixin;

import com.google.common.collect.Lists;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

/** Splits entity lookup boxes that cross a circumference seam. */
@Mixin(Level.class)
abstract class WorldEntityLookupMixin {
    @Shadow protected abstract LevelEntityGetter<Entity> getEntities();

    @Inject(method = "getEntities", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicEntities(Entity except, AABB box, Predicate<? super Entity> predicate,
                                            CallbackInfoReturnable<List<Entity>> cir) {
        RingTopology topology = topologyFor(box);
        if (topology == null) return;
        List<Entity> result = Lists.newArrayList();
        Set<Entity> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(box)) {
            getEntities().get(window.canonicalBox(), entity -> {
                if (entity != except && seen.add(entity) && predicate.test(entity)) result.add(entity);
            });
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void ringworld$periodicTypedEntities(EntityTypeTest<Entity, T> filter, AABB box,
                                                                    Predicate<? super T> predicate,
                                                                    List<? super T> result, int limit,
                                                                    CallbackInfo ci) {
        RingTopology topology = topologyFor(box);
        if (topology == null) return;
        Set<T> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(box)) {
            getEntities().get(filter, window.canonicalBox(), entity -> {
                if (seen.add(entity) && predicate.test(entity)) result.add(entity);
                return result.size() >= limit
                        ? net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT
                        : net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
            });
            if (result.size() >= limit) break;
        }
        ci.cancel();
    }

    private RingTopology topologyFor(AABB box) {
        if (!((Object) this instanceof ServerLevel world) || world.dimension() != Level.OVERWORLD) return null;
        var geometry = RingWorldServer.geometryFor(world);
        if (box.minX >= 0.0 && box.maxX <= geometry.circumferenceBlocks()) return null;
        return new RingTopology(geometry);
    }
}
