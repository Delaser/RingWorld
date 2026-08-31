package dev.ringworld.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Minecraft 26.1 client accessors retained behind the shared client source ABI. */
public final class RingMinecraftClientAccess {
    private RingMinecraftClientAccess() { }

    public static Screen screen(Minecraft client) { return client.screen; }

    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }

    public static RenderTarget mainRenderTarget(Minecraft client) { return client.getMainRenderTarget(); }

    public static ToastManager toastManager(Minecraft client) { return client.getToastManager(); }

    public static Entity cameraEntity(Minecraft client) { return client.getCameraEntity(); }

    public static Camera camera(Minecraft client) { return client.gameRenderer.getMainCamera(); }

    public static boolean hideGui(Minecraft client) { return client.options.hideGui; }

    public static void setGuiHidden(Minecraft client, boolean hidden) {
        client.options.hideGui = hidden;
    }

    public static void invalidateChunks(Minecraft client) { client.levelRenderer.needsUpdate(); }

    public static void grabScreenshot(File gameDirectory, String name, RenderTarget target, int scale,
                                      Consumer<Component> callback) {
        Screenshot.grab(gameDirectory, name, target, scale, callback);
    }
}
