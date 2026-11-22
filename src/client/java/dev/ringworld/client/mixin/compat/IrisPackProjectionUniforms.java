package dev.ringworld.client.mixin.compat;

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
abstract class IrisPackProjectionUniforms {
    @Unique private int ringworld$layout = -2, ringworld$distortion;
    @Unique private int ringworld$circumference = -1, ringworld$chunks = -1;
    @Unique private float ringworld$cameraY = Float.NaN, ringworld$referenceY;
    @Unique private int ringworld$worldTop;

    @Inject(method = "iris$setupState", at = @At("TAIL"))
    private void ringworld$view(CallbackInfo ci) {
        if (ringworld$layout == -2) {
            int id = ((ExtendedShader) (Object) this).getProgramId();
            ringworld$layout = glGetUniformLocation(id, "ringworldPackLayout");
            ringworld$distortion = glGetUniformLocation(id, "ringworldPackDistortion");
        }
        if (ringworld$layout < 0) return;
        var mc = Minecraft.getInstance();
        var g = !dev.ringworld.client.RingShaderSupport.enabled() || mc.level == null ? null : ClientRingState.geometry(mc.level);
        int circ = g == null ? 0 : g.circumferenceBlocks();
        float y = g == null ? 0 : (float) RingMinecraftClientAccess.camera(mc).position().y;
        float ref = g == null ? 0 : (float) g.surfaceReferenceY();
        int chunks =
                g != null && RingDistortionTuning.enabled() ? RingDistortionTuning.chunks() : 0;
        int worldTop = g == null ? 0 : mc.level.getMaxY() + 1;
        if (circ == ringworld$circumference
                && chunks == ringworld$chunks
                && ref == ringworld$referenceY
                && y == ringworld$cameraY
                && worldTop == ringworld$worldTop) return;
        glUniform4f(ringworld$layout, g == null ? 0 : 1, circ, ref, y);
        if (chunks > 0) {
            var projection =
                    dev.ringworld.world.RingNearbyProjection.create(g, y, worldTop, chunks);
            glUniform4f(
                    ringworld$distortion,
                    1,
                    (float) projection.coreBlocks(),
                    (float) projection.blendEnd(),
                    (float) projection.referenceRadius());
        } else glUniform4f(ringworld$distortion, 0, 0, 0, 0);
        ringworld$circumference = circ;
        ringworld$chunks = chunks;
        ringworld$referenceY = ref;
        ringworld$cameraY = y;
        ringworld$worldTop = worldTop;
    }
}
