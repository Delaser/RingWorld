package dev.ringworld.client.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps shader compilation runnable while chunk builds occupy the shared pool. */
@Mixin(PipelineCache.class)
abstract class PipelineCacheMixin {
    @Unique
    private static final Executor ringworld$pipelineWorker =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RingWorld pipeline compilation");
                thread.setDaemon(true);
                return thread;
            });

    @ModifyArg(method = "get", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuDevice;compilePipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            index = 2)
    private Executor ringworld$avoidChunkWorkerStarvation(Executor sharedWorker) {
        return ringworld$pipelineWorker;
    }
}
