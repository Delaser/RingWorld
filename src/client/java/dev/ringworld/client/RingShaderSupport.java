package dev.ringworld.client;

import dev.ringworld.client.compat.IrisAtlasTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/** Local opt-out. Foreign renderer classes are touched only after its shader hook has run. */
public final class RingShaderSupport {
    private static volatile boolean detected;
    private static Boolean enabled;

    private RingShaderSupport() {}

    private static Path disabledFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/ringworld-shaders.disabled");
    }

    public static void rendererDetected() { detected = true; }
    public static boolean enabled() {
        if (enabled == null) enabled = !Files.exists(disabledFile());
        return enabled;
    }

    public static String select(boolean value) {
        Path path = disabledFile();
        try {
            if (value) Files.deleteIfExists(path);
            else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "");
            }
            enabled = value;
            return summary();
        } catch (IOException e) {
            return "Could not save RingWorld shader support setting: " + e.getMessage();
        }
    }

    public static String summary() {
        if (!enabled()) return "RingWorld shader support is off for this client.";
        return detected && IrisAtlasTarget.active()
                ? "RingWorld shader support is on. Shader pack detected."
                : "RingWorld shader support is on. Waiting for a shader pack.";
    }

    static String activePackName() {
        return detected ? IrisAtlasTarget.activePackName() : null;
    }

    public static boolean useDeferredAtlas() {
        return detected && enabled() && IrisAtlasTarget.canDefer();
    }

    public static long bindAtlas(int mainColor) {
        return detected && enabled() ? IrisAtlasTarget.bind(mainColor) : 0;
    }

    public static void restoreAtlas(long original) {
        if (original != 0) IrisAtlasTarget.restore(original);
    }
}
