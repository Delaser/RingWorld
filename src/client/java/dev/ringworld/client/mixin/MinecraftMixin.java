package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears static RingWorld state whenever Minecraft tears down a world. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(
            method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
            at = @At("HEAD"))
    private void ringworld$clearDisconnectSession(Screen nextScreen,
                                                  boolean keepResourcePacks,
                                                  boolean showDisconnectScreen,
                                                  CallbackInfo ci) {
        RingWorldClient.clearRingSession();
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void ringworld$clearClientSession(Screen nextScreen, CallbackInfo ci) {
        RingWorldClient.clearRingSession();
    }
}
