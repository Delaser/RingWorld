package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;

/** Opt-in raw fixture samples; no sample-buffer allocation or I/O in ordinary play. */
final class RingFrameEvidence {
    private final boolean enabled = Boolean.getBoolean("ringworld.rawFrameEvidence");
    private long[] samples;
    private int count;

    void reset() {
        count = 0;
        if (enabled && samples == null) samples = new long[65_536];
    }

    void record(long nanos) {
        if (!enabled) return;
        if (samples == null || count == samples.length || nanos <= 0)
            throw new IllegalStateException("Raw frame evidence is uninitialized, full or invalid");
        samples[count++] = nanos;
    }

    void write(String label, int expectedCount) {
        if (!enabled) return;
        if (!label.matches("[a-z0-9-]+") || count <= 0 || count != expectedCount)
            throw new IllegalStateException("Missing or inconsistent raw frame evidence: " + label);
        var path = Minecraft.getInstance().gameDirectory.toPath().resolve("evidence")
                .resolve("frames-" + label + ".rwfr");
        try {
            Files.createDirectories(path.getParent());
            try (var output = new DataOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
                output.writeInt(0x52574652); // RWFR, big endian
                output.writeInt(1);
                output.writeInt(count);
                for (int i = 0; i < count; i++) output.writeLong(samples[i]);
            }
            RingWorldMod.LOGGER.info("[raw-frame-evidence] file={} samples={} gpu={} driver={} java={}",
                    path.getFileName(), count,
                    org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER),
                    org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION),
                    System.getProperty("java.runtime.version"));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not retain raw frame evidence", failure);
        }
    }
}
