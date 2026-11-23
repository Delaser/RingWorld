package rwqa;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rwqa_state.Seen;

@Mixin(targets = "dev.ringworld.client.render.RingSurfaceTextureRenderer", remap = false)
abstract class SeenDraw {
    @Shadow private static boolean bufferedMeshDetailed;

    @Inject(method = "render", at = @At("HEAD"))
    private static void frame(CallbackInfo ci) {
        Seen.drew = false;
    }

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ldev/ringworld/client/render/RingSurfaceGpu;draw(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ldev/ringworld/client/render/RingSurfaceBuffers;I[IIILcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Z)V",
                            shift = At.Shift.AFTER))
    private static void observed(CallbackInfo ci) {
        Seen.drew = true;
        if (bufferedMeshDetailed && !Seen.full)
            System.out.println(
                    "QA_PUBLISHED API detail after " + Seen.frames + " placeholder frames");
        if (bufferedMeshDetailed && !Seen.full) Seen.swapped = System.nanoTime();
        if (bufferedMeshDetailed) Seen.full = true;
        else Seen.placeholder = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void end(CallbackInfo ci) {
        if (Seen.placeholder) {
            Seen.frames++;
            Seen.sample();
            if (!Seen.drew) Seen.missed++;
        }
    }
}
