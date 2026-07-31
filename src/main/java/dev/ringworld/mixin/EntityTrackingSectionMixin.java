package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Compares entity bounds in canonical storage space during section queries. */
@Mixin(EntitySection.class)
abstract class EntityTrackingSectionMixin<T extends EntityAccess> {
    @Shadow @Final private ClassInstanceMultiMap<T> storage;

    @Inject(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$canonicalBounds(AABB box, AbortableIterationConsumer<T> consumer,
                                           CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir) {
        RingTopology topology = topology();
        if (topology == null) return;
        for (T entity : storage) {
            if (topology.canonicalBox(entity.getBoundingBox()).intersects(box)
                    && consumer.accept(entity).shouldAbort()) {
                cir.setReturnValue(AbortableIterationConsumer.Continuation.ABORT);
                return;
            }
        }
        cir.setReturnValue(AbortableIterationConsumer.Continuation.CONTINUE);
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At("HEAD"), cancellable = true)
    private <U extends T> void ringworld$canonicalTypedBounds(EntityTypeTest<T, U> type, AABB box,
                                                              AbortableIterationConsumer<? super U> consumer,
                                                              CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir) {
        RingTopology topology = topology();
        if (topology == null) return;
        Collection<? extends T> matches = storage.find(type.getBaseClass());
        for (T entity : matches) {
            U typed = type.tryCast(entity);
            if (typed != null && topology.canonicalBox(entity.getBoundingBox()).intersects(box)
                    && consumer.accept(typed).shouldAbort()) {
                cir.setReturnValue(AbortableIterationConsumer.Continuation.ABORT);
                return;
            }
        }
        cir.setReturnValue(AbortableIterationConsumer.Continuation.CONTINUE);
    }

    private RingTopology topology() {
        for (T entity : storage) {
            if (entity instanceof Entity minecraftEntity
                    && minecraftEntity.level() instanceof ServerLevel world
                    && world.dimension() == Level.OVERWORLD) {
                return new RingTopology(RingWorldServer.geometryFor(world));
            }
            break;
        }
        return null;
    }
}
