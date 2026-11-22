package dev.ringworld.experiment.fixture.mixin;

import dev.ringworld.experiment.PatchProof;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets = "dev.ringworld.client.RingVisualParityCaptureClient", remap = false)
abstract class HoldView {
    @Inject(method = "frameRendered", at = @At("HEAD"))
    private void proof$timed(CallbackInfo ci) {
        dev.ringworld.experiment.RingCompareFixture.frame();
    }

    @Shadow private int stage;
    @Shadow private int settleTicks;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void proof$hold(Minecraft mc, CallbackInfoReturnable<Boolean> cir) {
        if (stage == 1 && settleTicks >= 22 && !PatchProof.done) {
            if (dev.ringworld.experiment.RingCompareFixture.hold(mc)) {
                cir.setReturnValue(true);
                return;
            }
            PatchProof.armed = true;
            PatchProof.strafe(mc);
            cir.setReturnValue(true);
        }
    }
}

@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
abstract class FinalFrame {
    @Shadow @Final private net.irisshaders.iris.targets.RenderTargets renderTargets;

    @Inject(method = "beginTranslucents", at = @At("HEAD"))
    private void proof$colors(CallbackInfo ci) {
        if (PatchProof.armed
                && Boolean.getBoolean("patch.attachments")
                && PatchProof.frames == 59
                && PatchProof.phase < 2) {
            var rt = renderTargets.get(1);
            if (rt != null) {
                PatchProof.snap("opaque-c1-main-" + PatchProof.phase, rt.getMainTexture());
                PatchProof.snap("opaque-c1-alt-" + PatchProof.phase, rt.getAltTexture());
            }
        }
    }

    @Inject(method = "beginTranslucents", at = @At("HEAD"))
    private void proof$albedo(CallbackInfo ci) {
        proof$readAlbedo("before-light");
    }

    @Inject(method = "beginTranslucents", at = @At("TAIL"))
    private void proof$lit(CallbackInfo ci) {
        proof$readAlbedo("after-light");
    }

    @Unique
    private void proof$readAlbedo(String stage) {
        if (PatchProof.armed
                && Boolean.getBoolean("patch.attachments")
                && PatchProof.frames == 59
                && PatchProof.phase < 2) {
            var rt = renderTargets.get(4);
            if (rt != null) {
                PatchProof.snap(stage + "-c4-main-" + PatchProof.phase, rt.getMainTexture());
                PatchProof.snap(stage + "-c4-alt-" + PatchProof.phase, rt.getAltTexture());
            }
        }
    }

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"))
    private void proof$frame(CallbackInfo ci) {
        PatchProof.frame();
    }
}
