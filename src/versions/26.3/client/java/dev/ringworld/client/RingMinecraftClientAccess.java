package dev.ringworld.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Minecraft 26.3 client accessors retained behind the shared client source ABI. */
public final class RingMinecraftClientAccess {
    private RingMinecraftClientAccess() { }

    /** Create isolated preview noise state from the worker's immutable registry snapshot. */
    public static RandomState createPreviewRandomState(RegistryAccess.Frozen registries,
                                                      Holder<NoiseGeneratorSettings> settings,
                                                      ResourceKey<NoiseGeneratorSettings> settingsKey,
                                                      long seed) {
        return RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
    }

    /** Reveal an opt-in hidden review window without activating the desktop app. */
    public static void showBackgroundReviewWindow(Minecraft client) {
        org.lwjgl.sdl.SDLVideo.SDL_ShowWindow(client.getWindow().handle());
        org.lwjgl.sdl.SDLVideo.SDL_SetWindowFocusable(client.getWindow().handle(), true);
    }

    public static int maxTextureSize() { return com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize(); }

    public static Screen screen(Minecraft client) { return client.gui.screen(); }

    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }

    public static RenderTarget mainRenderTarget(Minecraft client) { return client.gameRenderer.mainRenderTarget(); }

    public static ToastManager toastManager(Minecraft client) { return client.gui.toastManager(); }

    public static Entity cameraEntity(Minecraft client) { return client.getCameraEntity(); }

    public static Camera camera(Minecraft client) { return client.gameRenderer.mainCamera(); }

    public static boolean hideGui(Minecraft client) { return client.gui.hud.isHidden(); }

    public static void setGuiHidden(Minecraft client, boolean hidden) {
        if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
    }

    public static void invalidateChunks(Minecraft client) {
        client.levelRenderer.sectionOcclusionGraph().invalidate();
    }

    public static void grabScreenshot(File gameDirectory, String name, RenderTarget target, int scale,
                                      Consumer<Component> callback) {
        Screenshot.grab(gameDirectory, name, target, scale, callback);
    }
}
