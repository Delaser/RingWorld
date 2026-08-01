package dev.ringworld.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.AtlasPregenerationHandle;
import dev.ringworld.world.AtlasPregenerationHeadlessPolicy;
import dev.ringworld.world.AtlasPregenerationOptions;
import dev.ringworld.world.AtlasPregenerationProgress;
import dev.ringworld.world.AtlasPregenerationReport;
import dev.ringworld.world.AtlasPregenerationReportStatus;
import dev.ringworld.world.AtlasPregenerationResult;
import dev.ringworld.world.RingAtlasPregenerationCursor;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * Fabric/dedicated-server launch adapter for the explicit headless prewarm
 * option. It owns no atlas writes or scheduler: it observes the one service
 * handle, saves the world after verified completion, writes JSON evidence, and
 * then asks Minecraft to stop.
 */
final class RingWorldHeadlessPrewarm {
    private static final String ENABLE_PROPERTY = "ringworld.headlessPrewarm";
    private static final String REPORT_PROPERTY = "ringworld.headlessPrewarmReport";
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<MinecraftServer, Run> RUNS = new IdentityHashMap<>();

    private RingWorldHeadlessPrewarm() { }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RingWorldHeadlessPrewarm::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(RingWorldHeadlessPrewarm::serverStopping);
    }

    static boolean requested(MinecraftServer server) {
        return Boolean.getBoolean(ENABLE_PROPERTY) && server.isDedicatedServer();
    }

    static boolean suppressesBackgroundAutostart(MinecraftServer server) {
        return AtlasPregenerationHeadlessPolicy.suppressesBackgroundAutostart(requested(server));
    }

    static boolean rejectPlayerJoins(MinecraftServer server) { return RUNS.containsKey(server); }

    static void start(ServerLevel world) {
        MinecraftServer server = world.getServer();
        if (!requested(server) || RUNS.containsKey(server)) return;
        Run run = null;
        try {
            RingWorldSettings settings = RingWorldSettings.get(world);
            RingTerrainAtlas atlas = RingAtlasPregenerationService.atlas(world);
            Path resultPath = reportPath(world);
            HeadlessPrewarmEvidenceFiles.resetForNewRun(resultPath);
            run = new Run(world, settings, atlas, resultPath);
            run.handle = RingAtlasPregenerationService.pregenerate(world,
                    AtlasPregenerationOptions.headlessPrewarmDefaults(), progress -> { });
            run.writeProgress();
            // Publish only after handle construction and first durable status
            // write. A setup failure must never leave a join-blocking RUNS
            // entry without a terminal report/halt path.
            RUNS.put(server, run);
            RingWorldMod.LOGGER.info("[headless-prewarm] START worldHash={} layoutFingerprint={} report={}",
                    Long.toUnsignedString(atlas.worldHash()), Long.toUnsignedString(settings.layoutFingerprint()),
                    run.resultPath);
        } catch (Throwable failure) {
            if (run != null && run.handle != null) RingAtlasPregenerationService.interruptForServerStop(world);
            failStartup(server, world, failure);
        }
    }

    static void failStartup(MinecraftServer server, ServerLevel world, Throwable failure) {
        if (!requested(server)) return;
        Run run = RUNS.remove(server);
        if (run == null) run = Run.rejected(world, reportPathUnchecked(world));
        try {
            run.finish(AtlasPregenerationReportStatus.REJECTED, Optional.of(message(failure)));
        } catch (Throwable reportFailure) {
            RingWorldMod.LOGGER.error("[headless-prewarm] failed to construct rejected report", reportFailure);
        } finally {
            RingWorldMod.LOGGER.error("[headless-prewarm] REJECTED", failure);
            RUNS.remove(server);
            server.halt(false);
        }
    }

    private static void tick(MinecraftServer server) {
        Run run = RUNS.get(server);
        if (run == null || run.finished || run.handle == null) return;
        try {
            if (++run.ticks % 20 == 0) run.writeProgress();
            if (!run.handle.completion().toCompletableFuture().isDone()) return;
            AtlasPregenerationResult result = run.handle.completion().toCompletableFuture().join();
            // The service completed only after atomic atlas save and reopen
            // validation. Minecraft owns the subsequent normal world save.
            if (!server.saveEverything(true, true, true)) {
                throw new IOException("Minecraft rejected final headless world save");
            }
            run.result = result;
            run.finish(AtlasPregenerationReportStatus.COMPLETE, Optional.empty());
            RingWorldMod.LOGGER.info("[headless-prewarm] COMPLETE report={}", run.resultPath);
            RUNS.remove(server);
            server.halt(false);
        } catch (Throwable failure) {
            failRunning(server, run, failure);
        }
    }

    /** Handles progress/report I/O failures without leaking join-gating state. */
    private static void failRunning(MinecraftServer server, Run run, Throwable failure) {
        try {
            RingAtlasPregenerationService.interruptForServerStop(run.world);
        } catch (Throwable checkpointFailure) {
            failure.addSuppressed(checkpointFailure);
            RingWorldMod.LOGGER.error("[headless-prewarm] checkpoint after failure also failed", checkpointFailure);
        }
        try {
            run.finish(AtlasPregenerationReportStatus.FAILED, Optional.of(message(failure)));
        } catch (Throwable reportFailure) {
            RingWorldMod.LOGGER.error("[headless-prewarm] failed to construct terminal failure report", reportFailure);
        } finally {
            RingWorldMod.LOGGER.error("[headless-prewarm] FAILED", failure);
            RUNS.remove(server);
            server.halt(false);
        }
    }

    private static void serverStopping(MinecraftServer server) {
        Run run = RUNS.get(server);
        if (run == null || run.finished || run.world == null) return;
        // SIGTERM/normal external stop arrives on the server thread. Cancel
        // checkpoints durable cells; the next requested run resumes from them.
        String reason = "server stopped before verified atlas completion";
        try {
            RingAtlasPregenerationService.interruptForServerStop(run.world);
        } catch (Throwable failure) {
            reason += ": checkpoint failed: " + message(failure);
            RingWorldMod.LOGGER.error("[headless-prewarm] interruption checkpoint failed", failure);
        }
        try {
            run.finish(AtlasPregenerationReportStatus.INTERRUPTED,
                    Optional.of(reason));
            RingWorldMod.LOGGER.warn("[headless-prewarm] INTERRUPTED report={}", run.resultPath);
        } catch (Throwable failure) {
            RingWorldMod.LOGGER.error("[headless-prewarm] interruption report failed", failure);
        } finally {
            RUNS.remove(server);
        }
    }

    private static Path reportPath(ServerLevel world) {
        String filename = System.getProperty(REPORT_PROPERTY, "result.json");
        Path candidate = Path.of(filename);
        if (candidate.isAbsolute() || candidate.getNameCount() != 1 || !filename.endsWith(".json")
                || filename.equals("progress.json")) {
            throw new IllegalArgumentException("-D" + REPORT_PROPERTY + " must be one relative .json filename");
        }
        return world.getServer().getWorldPath(LevelResource.ROOT).resolve("ringworld-prewarm").resolve(filename);
    }

    private static Path reportPathUnchecked(ServerLevel world) {
        try {
            return reportPath(world);
        } catch (RuntimeException ignored) {
            // Startup rejection must still write evidence even when the caller
            // supplied an unsafe report-name option; use the safe default.
            return world.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("ringworld-prewarm").resolve("result.json");
        }
    }

    private static String message(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        String value = cause.getMessage();
        return value == null || value.isBlank() ? cause.getClass().getSimpleName() : value;
    }

    private static final class Run {
        private final ServerLevel world;
        private final RingWorldSettings settings;
        private final RingTerrainAtlas atlas;
        private final Path progressPath;
        private final Path resultPath;
        private final long startedNanos = System.nanoTime();
        private AtlasPregenerationHandle handle;
        private AtlasPregenerationResult result;
        private int ticks;
        private boolean finished;

        private Run(ServerLevel world, RingWorldSettings settings, RingTerrainAtlas atlas, Path resultPath) {
            this.world = world;
            this.settings = settings;
            this.atlas = atlas;
            this.progressPath = resultPath.resolveSibling("progress.json");
            this.resultPath = resultPath;
        }

        private static Run rejected(ServerLevel world, Path resultPath) {
            return new Run(world, null, null, resultPath);
        }

        private void writeProgress() {
            if (finished) return;
            AtlasPregenerationProgress progress = handle == null ? new AtlasPregenerationProgress(
                    dev.ringworld.world.AtlasPregenerationState.FAILED, 0, 0, 0, 0, 0,
                    Duration.ZERO, Optional.empty(), Optional.of("headless prewarm did not start")) : handle.progress();
            JsonObject json = new JsonObject();
            json.addProperty("type", "progress");
            json.addProperty("state", progress.state().name());
            json.addProperty("schemaVersion", 1);
            json.addProperty("identityAvailable", true);
            json.addProperty("worldHash", Long.toUnsignedString(atlas.worldHash()));
            json.addProperty("layoutFingerprint", Long.toUnsignedString(settings.layoutFingerprint()));
            json.addProperty("completedChunks", atlas.presentChunkCount());
            json.addProperty("totalChunks", RingAtlasPregenerationCursor.checkedTotalChunks(
                    atlas.geometry().circumferenceChunks(), atlas.geometry().widthChunks()));
            json.addProperty("generatedChunksThisRun", progress.completedChunks());
            json.addProperty("completedCells", progress.presentCells());
            json.addProperty("totalCells", progress.totalCells());
            json.addProperty("elapsedMillis", progress.elapsed().toMillis());
            json.addProperty("cellsPerSecond", progress.cellsPerSecond());
            progress.eta().ifPresentOrElse(value -> json.addProperty("etaMillis", value.toMillis()),
                    () -> json.add("etaMillis", null));
            progress.lastError().ifPresentOrElse(value -> json.addProperty("lastError", value),
                    () -> json.add("lastError", null));
            writeAtomically(progressPath, JSON.toJson(json));
        }

        private void finish(AtlasPregenerationReportStatus status, Optional<String> failure) {
            if (finished) return;
            AtlasPregenerationProgress progress = handle == null ? new AtlasPregenerationProgress(
                    dev.ringworld.world.AtlasPregenerationState.FAILED, 0, 0, 0, 0, 0,
                    Duration.ZERO, Optional.empty(), failure) : handle.progress();
            boolean identityAvailable = atlas != null && settings != null;
            AtlasPregenerationReport report = new AtlasPregenerationReport(1, status, identityAvailable,
                    identityAvailable ? atlas.worldHash() : 0L,
                    identityAvailable ? settings.layoutFingerprint() : 0L,
                    identityAvailable ? atlas.presentChunkCount() : 0L,
                    identityAvailable ? RingAtlasPregenerationCursor.checkedTotalChunks(
                            atlas.geometry().circumferenceChunks(), atlas.geometry().widthChunks()) : 0L,
                    identityAvailable ? atlas.presentCount() : 0,
                    identityAvailable ? atlas.cellCount() : 0,
                    result == null ? Duration.ofNanos(System.nanoTime() - startedNanos) : result.elapsed(),
                    identityAvailable ? Optional.of(result == null ? RingAtlasPregenerationService.cachePath(world) : result.atlasPath()) : Optional.empty(), failure);
            JsonObject json = new JsonObject();
            json.addProperty("schemaVersion", report.schemaVersion());
            json.addProperty("status", report.status().name());
            json.addProperty("identityAvailable", report.identityAvailable());
            json.addProperty("worldHash", Long.toUnsignedString(report.worldHash()));
            json.addProperty("layoutFingerprint", Long.toUnsignedString(report.layoutFingerprint()));
            json.addProperty("completedChunks", report.completedChunks());
            json.addProperty("totalChunks", report.totalChunks());
            json.addProperty("completedCells", report.completedCells());
            json.addProperty("totalCells", report.totalCells());
            json.addProperty("elapsedMillis", report.elapsed().toMillis());
            report.atlasPath().ifPresentOrElse(value -> json.addProperty("atlasPath", value.toString()),
                    () -> json.add("atlasPath", null));
            report.failureReason().ifPresentOrElse(value -> json.addProperty("failureReason", value),
                    () -> json.add("failureReason", null));
            writeAtomically(resultPath, JSON.toJson(json));
            finished = true;
        }

        private static void writeAtomically(Path path, String content) {
            try {
                Files.createDirectories(path.getParent());
                Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("could not write headless prewarm report " + path, exception);
            }
        }
    }
}
