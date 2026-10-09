package dev.ringworld.server;

import com.google.gson.JsonObject;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.server.level.ServerLevel;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;

/** Optional measurements for disposable headless comparisons; no production sampling. */
final class RingAtlasConcurrencyMetrics {
    private final boolean enabled = Boolean.getBoolean("ringworld.measureAtlasConcurrency");
    private final long started = System.nanoTime();
    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean ? bean : null;
    private final long initialCpu = os == null ? -1 : os.getProcessCpuTime();
    private final ArrayList<Long> tickDurations = new ArrayList<>();
    private long peakHeap;
    private int peakRequests;
    private int peakPending;
    private int lastTick = -1;

    void tick(ServerLevel world) {
        if (!enabled) return;
        var server = world.getServer();
        int current = server.getTickCount();
        if (current != lastTick) {
            long[] times = server.getTickTimesNanos();
            long previous = times[Math.floorMod(current - 1, times.length)];
            if (previous > 0) tickDurations.add(previous);
            lastTick = current;
        }
        peakHeap = Math.max(peakHeap, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        peakRequests = Math.max(peakRequests, RingAtlasPregenerationService.inFlightChunks(world));
        peakPending = Math.max(peakPending, world.getChunkSource().getPendingTasksCount());
    }

    void addReport(JsonObject parent) {
        if (!enabled) return;
        var report = new JsonObject();
        var sorted = new ArrayList<>(tickDurations);
        sorted.sort(Long::compare);
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9);
        report.addProperty("averageCpuCores", os == null || initialCpu < 0 ? -1 :
                (os.getProcessCpuTime() - initialCpu) / (double)(System.nanoTime() - started));
        report.addProperty("peakUsedHeapMiB", peakHeap / 1048576.0);
        report.addProperty("peakInFlightChunks", peakRequests);
        report.addProperty("peakPendingTasks", peakPending);
        report.addProperty("observedTicks", sorted.size());
        report.addProperty("meanTickMs", sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6);
        report.addProperty("p95TickMs", percentile(sorted, .95));
        report.addProperty("p99TickMs", percentile(sorted, .99));
        report.addProperty("maxTickMs", percentile(sorted, 1));
        report.addProperty("ticksOver50Ms", sorted.stream().filter(value -> value > 50_000_000L).count());
        parent.add("atlasConcurrencyMetrics", report);
    }
    private static double percentile(ArrayList<Long> sorted, double fraction) {
        return sorted.isEmpty() ? 0 : sorted.get(Math.max(0, (int)Math.ceil(sorted.size() * fraction) - 1)) / 1e6;
    }
}
