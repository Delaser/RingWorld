package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldClientSession;
import dev.ringworld.client.RingWorldCreationUiTestClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears static RingWorld state whenever Minecraft tears down a world. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "disconnect()V", at = @At("HEAD"))
    private void ringworld$clearDisconnectSession(CallbackInfo ci) {
        RingWorldClientSession.clear();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void ringworld$clearDisconnectSession(Screen nextScreen, CallbackInfo ci) {
        RingWorldClientSession.clear();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void ringworld$clearDisconnectSession(Screen nextScreen, boolean transferring, CallbackInfo ci) {
        RingWorldClientSession.clear();
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void ringworld$clearClientSession(Screen nextScreen, CallbackInfo ci) {
        RingWorldClientSession.clear();
    }

    /** Menu rendering does not reach either loader's level-render callback. */
    @Inject(method = "runTick", at = @At("TAIL"))
    private void ringworld$recordCreationUiTestFrame(boolean advanceGameTime, CallbackInfo ci) {
        RingWorldCreationUiTestClient.frameRendered();
    }
}