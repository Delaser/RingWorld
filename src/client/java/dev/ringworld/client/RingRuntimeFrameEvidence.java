package dev.ringworld.client;

import com.google.gson.JsonObject;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingFrameStreamWriter;
import net.minecraft.client.Minecraft;

/** Opt-in continuous real-client frame stream, including unfocused dedicated clients. */
public final class RingRuntimeFrameEvidence {
    private static final boolean ENABLED = Boolean.getBoolean("ringworld.runtimeFrameEvidence");
    private static RingFrameStreamWriter writer;
    private static long previous;
    private static long samples;
    private RingRuntimeFrameEvidence() {}

    public static void frame(Minecraft client) {
        if (!ENABLED) return;
        if (writer == null) {
            // The native multiplayer fixture applies its options before login.
            // Startup remains separately timed; streaming and all later frames are retained.
            if (client.player == null || client.level == null || !client.isGameLoadFinished()) return;
            var metadata = new JsonObject();
            String label = System.getProperty("ringworld.runtimeFrameLabel",
                    System.getProperty("ringworld.multiplayerTestRole", "client"));
            metadata.addProperty("label", label); metadata.addProperty("pid", ProcessHandle.current().pid());
            metadata.addProperty("javaRuntime", System.getProperty("java.runtime.version"));
            metadata.addProperty("gpu", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            metadata.addProperty("driver", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
            metadata.addProperty("initialWidth", client.getWindow().getWidth());
            metadata.addProperty("initialHeight", client.getWindow().getHeight());
            metadata.addProperty("initialRenderDistance", client.options.renderDistance().get());
            metadata.addProperty("initialFrameCap", client.options.framerateLimit().get());
            metadata.addProperty("initialGraphicsMode", client.options.graphicsMode().get().toString());
            metadata.addProperty("initialCloudMode", client.options.cloudStatus().get().toString());
            writer = new RingFrameStreamWriter(client.gameDirectory.toPath().resolve("evidence/runtime-frames"), label, metadata);
            previous = System.nanoTime();
            RingWorldMod.LOGGER.info("[runtime-frame-evidence] START label={} pid={} cap={} view={}",
                    label, ProcessHandle.current().pid(), client.options.framerateLimit().get(), client.options.renderDistance().get());
            return;
        }
        long now = System.nanoTime();
        writer.record(previous, now); previous = now; samples++;
    }

    public static void finish() {
        if (!ENABLED || writer == null) return;
        try {
            writer.close();
            RingWorldMod.LOGGER.info("[runtime-frame-evidence] COMPLETE samples={}", samples);
        } catch (RuntimeException failure) {
            RingWorldMod.LOGGER.error("[runtime-frame-evidence] FAIL retaining every sample", failure);
            throw failure;
        }
    }
}
