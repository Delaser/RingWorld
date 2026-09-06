package dev.ringworld.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in unattended test window: render without showing or activating a desktop window. */
@Mixin(Window.class)
abstract class FidelityGalleryWindowMixin {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static void ringworld$backgroundGallery(CallbackInfoReturnable<Long> cir) {
        if (!Boolean.getBoolean("ringworld.fidelityGallery")
                && !Boolean.getBoolean("ringworld.backgroundTestWindow")) return;
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}
