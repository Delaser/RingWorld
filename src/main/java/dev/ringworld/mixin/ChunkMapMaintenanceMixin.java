package dev.ringworld.mixin;

import dev.ringworld.server.RingChunkWorkContext;
import dev.ringworld.server.RingChunkMaintenanceBudget;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import dev.ringworld.server.RingChunkWriteTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.CompletableFuture;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bound ordinary RingWorld chunk snapshot work; explicit save/flush paths stay vanilla. */
@Mixin(ChunkMap.class)
abstract class ChunkMapMaintenanceMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
    @Shadow @Final private Long2LongMap chunkSaveCooldowns;
    @Unique private final RingChunkWriteTracker ringworld$writes = new RingChunkWriteTracker();
    @Shadow @Final private LongSet toDrop;
    @Shadow @Final private Queue<Runnable> unloadQueue;
    @Shadow private boolean saveChunkIfNeeded(ChunkHolder holder) { throw new AssertionError(); }
    @Unique private long[] ringworld$saveKeys;
    @Unique private int ringworld$saveCursor;
    @Unique private boolean ringworld$saveAgain;
    @Unique private boolean ringworld$ordinaryTick;
    @Unique private long ringworld$start;
    @Unique private int ringworld$tasks;
    @Unique private int ringworld$saves;
    @Unique private RingChunkMaintenanceBudget ringworld$budget;

    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void ringworld$queueAutosave(boolean flushStorage, CallbackInfo ci) {
        if (flushStorage || !RingChunkWorkContext.periodicSave() || level.dimension() != Level.OVERWORLD) return;
        if (ringworld$saveKeys == null) {
            // Coordinates only: no retained holder/chunk graph and no worker reads of live chunks.
            ringworld$saveKeys = visibleChunkMap.keySet().toLongArray();
            ringworld$saveCursor = 0;
        } else ringworld$saveAgain = true;
        ci.cancel();
    }

    @Shadow private void processUnloads(BooleanSupplier haveTime) { throw new AssertionError(); }

    @Redirect(method = "saveAllChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;processUnloads(Ljava/util/function/BooleanSupplier;)V"))
    private void ringworld$flushUnloads(ChunkMap map, BooleanSupplier haveTime) {
        boolean ordinary = ringworld$ordinaryTick;
        ringworld$ordinaryTick = false;
        try { processUnloads(haveTime); }
        finally { ringworld$ordinaryTick = ordinary; }
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void ringworld$begin(BooleanSupplier haveTime, CallbackInfo ci) {
        // Also clear an interrupted ordinary tick before the shutdown drain starts.
        ringworld$ordinaryTick = false;
        if (level.dimension() != Level.OVERWORLD || level.getServer().isCurrentlySaving()) return;
        ringworld$ordinaryTick = true;
        ringworld$start = System.nanoTime();
        ringworld$tasks = 0;
        ringworld$saves = 0;
        Runtime runtime = Runtime.getRuntime();
        ringworld$budget = RingChunkMaintenanceBudget.select((long) toDrop.size() + unloadQueue.size(),
                Math.max(0, runtime.totalMemory() - runtime.freeMemory()), runtime.maxMemory());
        // Reserve a little progress for the sweep, then share the remainder with unload/eager work.
        int examined = 0;
        while (ringworld$saveKeys != null && ringworld$saveCursor < ringworld$saveKeys.length
                && examined < 128 && ringworld$saves < 4 && ringworld$allowsWork()) {
            long key = ringworld$saveKeys[ringworld$saveCursor++];
            examined++; ringworld$tasks++;
            ChunkHolder holder = visibleChunkMap.get(key);
            if (holder == null) continue; // Removed chunks use vanilla's pending-unload save path.
            chunkSaveCooldowns.remove(key);
            saveChunkIfNeeded(holder);
        }
        if (ringworld$saveKeys != null && ringworld$saveCursor == ringworld$saveKeys.length) {
            ringworld$saveKeys = null;
            if (ringworld$saveAgain) {
                ringworld$saveAgain = false;
                ringworld$saveKeys = visibleChunkMap.keySet().toLongArray();
                ringworld$saveCursor = 0;
            }
        }
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void ringworld$end(BooleanSupplier haveTime, CallbackInfo ci) {
        if (ringworld$ordinaryTick && Boolean.getBoolean("ringworld.chunkWorkTimings")
                && (ringworld$tasks > 0 || ringworld$saveKeys != null)) {
            org.slf4j.LoggerFactory.getLogger("ringworld").info(
                    "RingWorld chunk maintenance tick={} tasks={} saveAttempts={} sweepRemaining={} spanMs={} budgetMs={}",
                    level.getServer().getTickCount(), ringworld$tasks, ringworld$saves,
                    ringworld$saveKeys == null ? 0 : ringworld$saveKeys.length - ringworld$saveCursor,
                    (System.nanoTime() - ringworld$start) / 1_000_000.0, ringworld$budget.nanos() / 1_000_000.0);
        }
        ringworld$ordinaryTick = false;
    }

    @Unique private boolean ringworld$allowsWork() {
        return ringworld$saves < ringworld$budget.saves() && ringworld$tasks < ringworld$budget.tasks()
                && ringworld$writes.active() < 128
                && (ringworld$tasks == 0 || System.nanoTime() - ringworld$start < ringworld$budget.nanos());
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;size()I"))
    private int ringworld$boundedMandatoryDrain(Queue<Runnable> queue) {
        int size = queue.size();
        return ringworld$ordinaryTick ? Math.min(size, 2000) : size;
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongIterator;hasNext()Z", remap = false))
    private boolean ringworld$boundedDropScheduling(LongIterator iterator) {
        return (!ringworld$ordinaryTick || ringworld$allowsWork()) && iterator.hasNext();
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongIterator;nextLong()J", remap = false))
    private long ringworld$countScan(LongIterator iterator) {
        if (ringworld$ordinaryTick) ringworld$tasks++;
        return iterator.nextLong();
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/function/BooleanSupplier;getAsBoolean()Z"))
    private boolean ringworld$deadline(BooleanSupplier haveTime) {
        if (!ringworld$ordinaryTick) return haveTime.getAsBoolean();
        // One indivisible operation may exceed the soft budget; never starve cleanup on a busy tick.
        return ringworld$allowsWork() && (ringworld$tasks == 0 || haveTime.getAsBoolean());
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"))
    private void ringworld$unload(Runnable task) {
        if (ringworld$ordinaryTick) ringworld$tasks++;
        if (!ringworld$ordinaryTick && level.dimension() == Level.OVERWORLD
                && level.getServer().isCurrentlySaving()) {
            // A completed save dependency can still have generation references.
            // Its unload callback then queues itself again. An unbounded shutdown
            // drain must service the chunk owner's queue between callbacks or it
            // can starve the work that releases those references forever.
            // Retain every callback and the complete vanilla save/flush drain.
            dev.ringworld.server.RingChunkUnloadProgress.run(task,
                    () -> level.getChunkSource().pollTask());
        } else task.run();
    }

    /** 1.21.1 has no native active-write counter; retain its original future and error handling. */
    @Redirect(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> ringworld$trackWrite(ChunkMap map, ChunkPos position, CompoundTag data) {
        if (level.dimension() != Level.OVERWORLD) return map.write(position, data);
        return ringworld$writes.track(() -> map.write(position, data));
    }

    /** Eager saves are the final loop in processUnloads on this Minecraft ABI. */
    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectIterator;next()Ljava/lang/Object;", remap = false))
    private Object ringworld$countEagerScan(ObjectIterator<?> iterator) {
        if (ringworld$ordinaryTick) ringworld$tasks++;
        return iterator.next();
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void ringworld$saveAttempt(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$ordinaryTick) ringworld$saves++;
    }
}
