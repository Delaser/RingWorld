package dev.ringworld.server;

import com.google.gson.JsonObject;
import dev.ringworld.RingWorldMod;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Opt-in inclusive tick/scheduled-work measurements owned by one server instance. */
public final class RingServerTickEvidence {
    private static final int CAPACITY = 131_072;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static volatile int phase;
    private final RingTickWorkAccounting accounting = new RingTickWorkAccounting();
    private final dev.ringworld.world.RingClockAnchor anchor = dev.ringworld.world.RingClockAnchor.capture();
    private final ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
    private final long[] starts = new long[CAPACITY], games = new long[CAPACITY],
            scheduled = new long[CAPACITY], idle = new long[CAPACITY], cpuNanos = new long[CAPACITY];
    private final int[] ticks = new int[CAPACITY], players = new int[CAPACITY], phases = new int[CAPACITY];
    private final boolean cpuAvailable;
    private int count;
    private int currentPhase;
    private long cpuStart;

    public RingServerTickEvidence() {
        boolean available = cpu.isCurrentThreadCpuTimeSupported();
        if (available && !cpu.isThreadCpuTimeEnabled()) {
            try { cpu.setThreadCpuTimeEnabled(true); }
            catch (UnsupportedOperationException | SecurityException unavailable) { available = false; }
        }
        cpuAvailable = available;
    }

    /** Test coordinator phase changes apply at the next loop boundary. */
    public static void phase(int selected) {
        if (selected < 0 || selected > 255) throw new IllegalArgumentException("invalid measurement phase");
        phase = selected;
    }

    public void begin() {
        if (count == CAPACITY) throw new IllegalStateException("server tick evidence capacity exceeded");
        accounting.begin(System.nanoTime());
        currentPhase = phase;
        cpuStart = cpuAvailable ? cpu.getCurrentThreadCpuTime() : -1;
    }
    public void gameFinished() { accounting.gameFinished(System.nanoTime()); }
    public void taskStarted() { accounting.taskStarted(); }
    public void taskFinished() { accounting.taskFinished(); }
    public void idleStarted() { accounting.idleStarted(System.nanoTime()); }
    public void idleFinished() { accounting.idleFinished(System.nanoTime()); }

    public void finish(MinecraftServer server) {
        accounting.finish(System.nanoTime());
        starts[count] = accounting.startedNanos(); games[count] = accounting.gameNanos();
        scheduled[count] = accounting.scheduledNanos(); idle[count] = accounting.idleNanos();
        cpuNanos[count] = cpuAvailable ? cpu.getCurrentThreadCpuTime() - cpuStart : -1;
        ticks[count] = server.getTickCount(); players[count] = server.getPlayerList().getPlayers().size();
        phases[count] = currentPhase;
        count++;
    }

    /** Blocking export is shutdown-only and is not part of ordinary tick samples. */
    public void write(MinecraftServer server, long shutdownNanos) {
        String runId = System.getProperty("ringworld.tickEvidenceRunId", "native");
        if (!runId.matches("[A-Za-z0-9._-]{1,96}")) throw new IllegalArgumentException("invalid tick evidence run id");
        var directory = server.getWorldPath(LevelResource.ROOT).resolve("ringworld-evidence");
        String name = "ticks-" + runId + '-' + SEQUENCE.getAndIncrement();
        var path = directory.resolve(name + ".rwtk");
        try {
            Files.createDirectories(directory);
            try (var output = new DataOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
                output.writeInt(0x5257544B); output.writeInt(1); output.writeInt(count);
                for (int i = 0; i < count; i++) {
                    output.writeInt(ticks[i]); output.writeInt(players[i]); output.writeInt(phases[i]);
                    output.writeLong(starts[i]); output.writeLong(games[i]); output.writeLong(scheduled[i]);
                    output.writeLong(idle[i]); output.writeLong(cpuNanos[i]);
                }
            }
            var hash = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int length;
                while ((length = input.read(buffer)) != -1) hash.update(buffer, 0, length);
            }
            var metadata = new JsonObject();
            metadata.addProperty("schemaVersion", 1); metadata.addProperty("samples", count);
            metadata.addProperty("runId", runId); metadata.addProperty("sha256", HexFormat.of().formatHex(hash.digest()));
            metadata.addProperty("ownerCpuAvailable", cpuAvailable);
            metadata.addProperty("unfinishedLoop", accounting.active());
            metadata.addProperty("lateHookStressProbe", Boolean.getBoolean("ringworld.testTickEvidence"));
            metadata.addProperty("shutdownNanos", shutdownNanos);
            metadata.addProperty("javaRuntime", System.getProperty("java.runtime.version"));
            metadata.addProperty("clockAnchorNanoTime", anchor.nanoTime());
            metadata.addProperty("clockAnchorEpochSecond", anchor.epochSecond());
            metadata.addProperty("clockAnchorNanoAdjustment", anchor.nanoAdjustment());
            metadata.addProperty("clockAnchorUncertaintyNanos", anchor.uncertaintyNanos());
            var end = dev.ringworld.world.RingClockAnchor.capture();
            var endAnchor = new JsonObject();
            endAnchor.addProperty("clockAnchorNanoTime", end.nanoTime());
            endAnchor.addProperty("clockAnchorEpochSecond", end.epochSecond());
            endAnchor.addProperty("clockAnchorNanoAdjustment", end.nanoAdjustment());
            endAnchor.addProperty("clockAnchorUncertaintyNanos", end.uncertaintyNanos());
            metadata.add("endClockAnchor", endAnchor);
            metadata.addProperty("scope", "tickServer including tail hooks plus scheduled work; intentional scheduler parking excluded");
            try (var writer = Files.newBufferedWriter(directory.resolve(name + ".json"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW)) { writer.write(metadata.toString()); }
            RingWorldMod.LOGGER.info("[server-tick-evidence] file={} samples={} incomplete={} shutdownMs={}",
                    path, count, accounting.active(), shutdownNanos / 1e6);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Could not retain inclusive server tick evidence", failure);
        }
    }
}
