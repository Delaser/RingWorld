package dev.ringworld.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** Keeps mainline's named screenshot call shape on Minecraft 1.21.1. */
public final class Screenshot {
    private Screenshot() { }

    public static void grab(File gameDirectory, String name, RenderTarget target,
                            int scale, Consumer<Component> callback) {
        if (scale != 1) {
            throw new IllegalArgumentException("Minecraft 1.21.1 only supports native-scale screenshots");
        }
        net.minecraft.client.Screenshot.grab(gameDirectory, name, target, callback);
    }

    public static void grab(File gameDirectory, RenderTarget target,
                            Consumer<Component> callback) {
        net.minecraft.client.Screenshot.grab(gameDirectory, target, callback);
    }
}
