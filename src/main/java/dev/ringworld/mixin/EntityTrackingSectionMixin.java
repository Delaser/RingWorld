package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Compares entity bounds in canonical storage space during section queries. */
@Mixin(EntityTrackingSection.class)
abstract class EntityTrackingSectionMixin<T extends EntityLike> {
    @Shadow @Final private TypeFilterableList<T> collection;

    @Inject(method = "forEach(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$canonicalBounds(Box box, LazyIterationConsumer<T> consumer,
                                           CallbackInfoReturnable<LazyIterationConsumer.NextIteration> cir) {
        RingTopology topology = topology();
        if (topology == null) return;
        for (T entity : collection) {
            if (topology.canonicalBox(entity.getBoundingBox()).intersects(box)
                    && consumer.accept(entity).shouldAbort()) {
                cir.setReturnValue(LazyIterationConsumer.NextIteration.ABORT);
                return;
            }
        }
        cir.setReturnValue(LazyIterationConsumer.NextIteration.CONTINUE);
    }

    @Inject(method = "forEach(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;",
            at = @At("HEAD"), cancellable = true)
    private <U extends T> void ringworld$canonicalTypedBounds(TypeFilter<T, U> type, Box box,
                                                              LazyIterationConsumer<? super U> consumer,
                                                              CallbackInfoReturnable<LazyIterationConsumer.NextIteration> cir) {
        RingTopology topology = topology();
        if (topology == null) return;
        Collection<? extends T> matches = collection.getAllOfType(type.getBaseClass());
        for (T entity : matches) {
            U typed = type.downcast(entity);
            if (typed != null && topology.canonicalBox(entity.getBoundingBox()).intersects(box)
                    && consumer.accept(typed).shouldAbort()) {
                cir.setReturnValue(LazyIterationConsumer.NextIteration.ABORT);
                return;
            }
        }
        cir.setReturnValue(LazyIterationConsumer.NextIteration.CONTINUE);
    }

    private RingTopology topology() {
        for (T entity : collection) {
            if (entity instanceof Entity minecraftEntity
                    && minecraftEntity.getEntityWorld() instanceof ServerWorld world
                    && world.getRegistryKey() == World.OVERWORLD) {
                return new RingTopology(RingWorldServer.geometryFor(world));
            }
            break;
        }
        return null;
    }
}
