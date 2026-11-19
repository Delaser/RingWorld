package dev.ringworld.client.mixin.compat;

import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.client.render.RingRenderUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium's older GL path does not use Minecraft RenderPass. Restore its shared GL slot. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface", remap = false)
abstract class SodiumUniformBindingMixin {
    @Unique private int ringworld$previousBuffer;
    @Unique private long ringworld$previousStart, ringworld$previousSize;
    @Unique private int ringworld$genericBinding;
    @Unique private boolean ringworld$bound;
    // Stock Sodium uses units 0–3. Borrow a separate unit and restore its previous state.
    @Unique private static final int RINGWORLD_SURFACE_UNIT = 7;
    @Unique private int ringworld$previousTexture, ringworld$previousSampler;
    @Unique private boolean ringworld$surfaceBound;

    @Inject(method = "setupState", at = @At("TAIL"))
    private void ringworld$bind(CallbackInfo ci) {
        if (ringworld$bound) throw new IllegalStateException("Unbalanced Sodium uniform scope");
        var buffer = RingRenderUniforms.buffer();
        if (buffer == null) return;
        int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int block = GL31C.glGetUniformBlockIndex(program, "RingWorld");
        if (block == GL31C.GL_INVALID_INDEX) throw new IllegalStateException("Missing RingWorld terrain uniforms");
        ringworld$genericBinding = GL20C.glGetInteger(GL31C.GL_UNIFORM_BUFFER_BINDING);
        ringworld$previousBuffer = GL30C.glGetIntegeri(GL31C.GL_UNIFORM_BUFFER_BINDING, 1);
        ringworld$previousStart = GL32C.glGetInteger64i(GL31C.GL_UNIFORM_BUFFER_START, 1);
        ringworld$previousSize = GL32C.glGetInteger64i(GL31C.GL_UNIFORM_BUFFER_SIZE, 1);
        GL31C.glUniformBlockBinding(program, block, 1);
        GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, 1, ((SodiumGlBufferAccessor)buffer).ringworld$handle());
        ringworld$bound = true;
        int location = GL20C.glGetUniformLocation(program, "RingWorldSurface");
        if (location >= 0) {
            var surface = RingSurfaceTextureRenderer.waterSurface();
            if (surface == null) surface = Minecraft.getInstance().gameRenderer.levelLightmap();
            int active = GL20C.glGetInteger(GL20C.GL_ACTIVE_TEXTURE);
            GL20C.glActiveTexture(GL20C.GL_TEXTURE0 + RINGWORLD_SURFACE_UNIT);
            ringworld$previousTexture = GL20C.glGetInteger(GL20C.GL_TEXTURE_BINDING_2D);
            ringworld$previousSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, RINGWORLD_SURFACE_UNIT);
            GL20C.glBindTexture(GL20C.GL_TEXTURE_2D, ((GlTextureView)surface).texture().glId());
            var sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, true);
            GL33C.glBindSampler(RINGWORLD_SURFACE_UNIT, ((GlSampler)sampler).getId());
            GL20C.glUniform1i(location, RINGWORLD_SURFACE_UNIT);
            GL20C.glActiveTexture(active);
            ringworld$surfaceBound = true;
        }
        int camera = GL20C.glGetUniformLocation(program, "RingWorldCameraY");
        if (camera >= 0) {
            GL20C.glUniform1f(camera, (float)RingMinecraftClientAccess.camera(Minecraft.getInstance()).position().y);
        }
    }

    @Inject(method = "resetState", at = @At("HEAD"))
    private void ringworld$restore(CallbackInfo ci) {
        if (!ringworld$bound) return;
        if (ringworld$previousBuffer == 0 || ringworld$previousSize == 0) {
            GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, 1, ringworld$previousBuffer);
        } else {
            GL30C.glBindBufferRange(GL31C.GL_UNIFORM_BUFFER, 1, ringworld$previousBuffer,
                    ringworld$previousStart, ringworld$previousSize);
        }
        GL20C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, ringworld$genericBinding);
        if (ringworld$surfaceBound) {
            int active = GL20C.glGetInteger(GL20C.GL_ACTIVE_TEXTURE);
            GL20C.glActiveTexture(GL20C.GL_TEXTURE0 + RINGWORLD_SURFACE_UNIT);
            GL20C.glBindTexture(GL20C.GL_TEXTURE_2D, ringworld$previousTexture);
            GL33C.glBindSampler(RINGWORLD_SURFACE_UNIT, ringworld$previousSampler);
            GL20C.glActiveTexture(active);
            ringworld$surfaceBound = false;
        }
        ringworld$bound = false;
    }
}
