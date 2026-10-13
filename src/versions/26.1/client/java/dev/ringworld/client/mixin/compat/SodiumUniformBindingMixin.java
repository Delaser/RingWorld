package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.client.render.RingRenderUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
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
        ringworld$bound = false;
    }
}
