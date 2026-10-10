package dev.ringworld.mixin;

import dev.ringworld.server.RingChunkGraphAccess;
import dev.ringworld.server.RingChunkWorkBudget;
import dev.ringworld.server.RingChunkWorkContext;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Incremental propagation; publish holder futures only after the graph converges. */
@Mixin(DistanceManager.class)
abstract class DistanceManagerWorkMixin implements RingChunkGraphAccess {
    @Unique private LoadingChunkTrackerAccessor ringworld$tracker;
    @Unique private int ringworld$processed;

    @Override public boolean ringworld$graphPending() {
        return ringworld$tracker != null && ((DynamicGraphMinFixedPoint) ringworld$tracker).getQueueSize() > 0;
    }

    @Redirect(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/LoadingChunkTracker;runDistanceUpdates(I)I"))
    private int ringworld$propagate(@Coerce Object tracker, int count) {
        ringworld$tracker = (LoadingChunkTrackerAccessor) tracker;
        RingChunkWorkBudget budget = RingChunkWorkContext.graphBudget();
        if (budget == null) return ringworld$tracker.ringworld$runUpdates(count);
        ringworld$processed = 0;
        int batch;
        while ((batch = budget.nextBatch()) > 0 && ((DynamicGraphMinFixedPoint) ringworld$tracker).getQueueSize() > 0) {
            long start = System.nanoTime();
            int consumed = batch - ringworld$tracker.ringworld$runUpdates(batch);
            budget.record(consumed, System.nanoTime() - start);
            ringworld$processed += consumed;
            if (consumed == 0) break;
        }
        return count - ringworld$processed;
    }

    @Inject(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/LoadingChunkTracker;runDistanceUpdates(I)I", shift = At.Shift.AFTER), cancellable = true)
    private void ringworld$awaitConvergence(ChunkMap map, CallbackInfoReturnable<Boolean> cir) {
        if (RingChunkWorkContext.graphBudget() != null && ringworld$graphPending()) {
            // Keep the accumulated future-update set intact until ticket levels settle.
            // Returning false when no budget remains prevents idle executor spin.
            cir.setReturnValue(ringworld$processed > 0);
        }
    }
}
