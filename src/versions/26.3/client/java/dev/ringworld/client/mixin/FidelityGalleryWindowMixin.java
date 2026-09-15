package dev.ringworld.client.mixin;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
/** Opt-in test windows are hidden before SDL can show or activate them. */
@Mixin(Window.class)
abstract class FidelityGalleryWindowMixin {
    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
    private long ringworld$backgroundGallery(long flags) {
        return Boolean.getBoolean("ringworld.fidelityGallery") || Boolean.getBoolean("ringworld.backgroundTestWindow")
                ? flags | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE : flags;
    }
}
