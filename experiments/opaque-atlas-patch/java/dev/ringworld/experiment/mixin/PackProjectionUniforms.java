package dev.ringworld.experiment.mixin;

import static org.lwjgl.opengl.GL20.*;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingDistortionTuning;
import dev.ringworld.client.RingMinecraftClientAccess;

import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExtendedShader.class, remap = false)
abstract class PackProjectionUniforms {
    @Unique private int trial$layout = -2, trial$distortion;
    @Unique private int trial$circumference = -1, trial$chunks = -1;
    @Unique private float trial$cameraY = Float.NaN, trial$referenceY;
    @Unique private int trial$worldTop;

    @Inject(method = "iris$setupState", at = @At("TAIL"))
    private void trial$view(CallbackInfo ci) {
        if (!dev.ringworld.experiment.NativeAtlasTarget.ENABLED) return;
        if (trial$layout == -2) {
            int id = ((ExtendedShader) (Object) this).getProgramId();
            trial$layout = glGetUniformLocation(id, "ringTrialLayout");
            trial$distortion = glGetUniformLocation(id, "ringTrialDistortion");
        }
        if (trial$layout < 0) return;
        var mc = Minecraft.getInstance();
        var g = mc.level == null ? null : ClientRingState.geometry(mc.level);
        int circ = g == null ? 0 : g.circumferenceBlocks();
        float y = g == null ? 0 : (float) RingMinecraftClientAccess.camera(mc).position().y;
        float ref = g == null ? 0 : (float) g.surfaceReferenceY();
        int chunks =
                g != null && RingDistortionTuning.enabled() ? RingDistortionTuning.chunks() : 0;
        int worldTop = g == null ? 0 : mc.level.getMaxY() + 1;
        if (circ == trial$circumference
                && chunks == trial$chunks
                && ref == trial$referenceY
                && y == trial$cameraY
                && worldTop == trial$worldTop) return;
        glUniform4f(trial$layout, g == null ? 0 : 1, circ, ref, y);
        if (chunks > 0) {
            var projection =
                    dev.ringworld.world.RingNearbyProjection.create(g, y, worldTop, chunks);
            glUniform4f(
                    trial$distortion,
                    1,
                    (float) projection.coreBlocks(),
                    (float) projection.blendEnd(),
                    (float) projection.referenceRadius());
        } else glUniform4f(trial$distortion, 0, 0, 0, 0);
        trial$circumference = circ;
        trial$chunks = chunks;
        trial$referenceY = ref;
        trial$cameraY = y;
        trial$worldTop = worldTop;
    }
}
