package dev.ringworld.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.compat.ClientWorldLifecycle;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.server.RingAtlasPregenerationService;
import dev.ringworld.world.RingTerrainAtlas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;

/** Opt-in disposable cache-history regression; every reopen uses normal saving and teardown. */
public final class RingAtlasCacheIdentityTestClient {
    private static final RingAtlasCacheIdentityTestClient INSTANCE = new RingAtlasCacheIdentityTestClient();
    private record Install(int cells, boolean reused, long revision, long serverContent, long clientContent) {}
    private final List<Install> installs = new ArrayList<>();
    private final String world = System.getProperty("ringworld.cacheIdentityWorld", "Input");
    private int stage, ticks, settle, opens, completedDisconnects;
    private RingIntegratedCaptureControl.PlayerSafety safety = new RingIntegratedCaptureControl.PlayerSafety();
    private boolean normalizationRequested, sampleRequested;
    private volatile boolean normalized, sampleReady, serverClean;
    private volatile long serverContent, serverRevision;
    private volatile String failure;
    private RingTerrainAtlas baseline;
    private Path cache;

    public static boolean tickIfEnabled(Minecraft client) {
        return Boolean.getBoolean("ringworld.cacheIdentityTest") && INSTANCE.tick(client);
    }

    static void recordInstall(RingTerrainAtlasMetadataPayload metadata, RingTerrainAtlas atlas, boolean reused) {
        var fixture = INSTANCE;
        if (fixture.installs.size() == 3) throw new IllegalStateException("Unexpected fourth cache fixture metadata installation");
        fixture.installs.add(new Install(atlas.presentCount(), reused, metadata.revision(),
                metadata.contentFingerprint(), atlas.contentFingerprint()));
        RingWorldMod.LOGGER.info("[cache-identity-test] install={} cells={} reused={} revision={} expected={} actual={}",
                fixture.installs.size(), atlas.presentCount(), reused, metadata.revision(),
                Long.toUnsignedString(metadata.contentFingerprint(), 16), Long.toUnsignedString(atlas.contentFingerprint(), 16));
    }

    private boolean tick(Minecraft client) {
        try {
            if (failure != null) throw new IllegalStateException(failure);
            if (++ticks > 14_400) throw new IllegalStateException("cache fixture watchdog stage=" + stage);
            client.options.pauseOnLostFocus = false;
            client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            if (stage == 0 || stage == 3 || stage == 6) {
                if (!client.isGameLoadFinished() || !(client.screen instanceof TitleScreen)
                        || client.level != null || client.getSingleplayerServer() != null) return true;
                opens++;
                safety = new RingIntegratedCaptureControl.PlayerSafety();
                normalizationRequested = normalized = sampleRequested = sampleReady = false;
                stage++; settle = 0;
                client.createWorldOpenFlows().openWorld(world, () -> failure = "world open cancelled");
                return true;
            }
            if (stage == 2 || stage == 5 || stage == 8) {
                if (client.level != null || client.getSingleplayerServer() != null) return true;
                require(ClientRingState.sessionCleared(), "normal disconnect left raw client state");
                // Final immutable saves may still be encoding after normal
                // disconnect. Do not poison/reopen the cache until their
                // explicitly owned native storage has also retired.
                if (RingAtlasNativeCacheMemory.liveBytes() != 0L) return true;
                completedDisconnects++;
                if (stage == 2) poisonCache(client);
                if (stage == 8) { finish(client); return true; }
                stage++; settle = 0;
                return true;
            }
            if (stage != 1 && stage != 4 && stage != 7) return true;
            if (client.level == null || client.player == null || ClientRingState.geometry() == null) return true;
            if (!safety.ensure(client, detail -> failure = detail)) return true;
            if (!normalizationRequested) {
                normalizationRequested = true;
                RingIntegratedCaptureControl.execute(client, "cache fixture environment", context -> {
                    RingIntegratedCaptureControl.normalizeEnvironment(context, 6000, false);
                    context.world().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, context.server());
                    context.world().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, context.server());
                    context.world().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, context.server());
                    context.world().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, context.server());
                }, () -> normalized = true, detail -> failure = detail);
            }
            if (!normalized || client.screen != null) return true;
            var atlas = ClientRingState.terrainAtlas();
            if (atlas == null || !atlas.isComplete()
                    || !dev.ringworld.client.render.RingSurfaceTextureRenderer.captureReady()) { settle = 0; return true; }
            if (++settle < 40) return true;
            if (!sampleRequested) {
                sampleRequested = true;
                RingIntegratedCaptureControl.execute(client, "cache fixture authoritative identity", context -> {
                    var serverAtlas = RingAtlasPregenerationService.atlas(context.world());
                    serverClean = !RingAtlasPregenerationService.hasPendingRecaptures(context.world());
                    serverContent = serverAtlas.contentFingerprint(); serverRevision = serverAtlas.revision();
                }, () -> sampleReady = true, detail -> failure = detail);
            }
            if (!sampleReady) return true;
            if (!serverClean) { sampleRequested = sampleReady = false; settle = 0; return true; }
            if (atlas.contentFingerprint() != serverContent || atlas.revision() != serverRevision) return true;
            require(installs.size() == opens, "missing native metadata observation");
            Install installed = installs.get(opens - 1);
            if (stage == 1) {
                require(!installed.reused && installed.cells == 0, "first fresh profile unexpectedly reused a cache");
                baseline = atlas.snapshot(); cache = ClientRingState.terrainAtlasCachePath();
                require(cache != null, "missing client cache path");
            } else {
                require(atlas.worldHash() == baseline.worldHash() && atlas.revision() == baseline.revision()
                        && atlas.contentFingerprint() == baseline.contentFingerprint(), "server content/history changed during the isolated control");
                require(atlas.cellHeight(0, 0) == baseline.cellHeight(0, 0)
                        && atlas.cellColor(0, 0) == baseline.cellColor(0, 0), "foreign cache cell survived repair");
                require(stage == 4 ? !installed.reused && installed.cells == 0
                        : installed.reused && installed.cells == atlas.cellCount(), "incorrect reject/reuse decision before streaming");
            }
            String name = stage == 1 ? "baseline" : stage == 4 ? "repaired" : "reused";
            dev.ringworld.client.compat.Screenshot.grab(client.gameDirectory, "cache-identity-" + name + ".png",
                    client.getMainRenderTarget(), 1, message -> {});
            RingWorldMod.LOGGER.info("[cache-identity-test] stage={} verified content={} revision={}", name,
                    Long.toUnsignedString(atlas.contentFingerprint(), 16), atlas.revision());
            ClientWorldLifecycle.disconnect(client, Component.literal("RingWorld cache identity regression"));
            stage++; settle = 0;
        } catch (Exception problem) {
            RingWorldMod.LOGGER.error("[cache-identity-test] FAIL: stage=" + stage, problem);
            stage = 9; client.stop();
        }
        return true;
    }

    private void poisonCache(Minecraft client) throws IOException {
        require(cache.toAbsolutePath().normalize().startsWith(client.gameDirectory.toPath().toAbsolutePath().normalize()),
                "fixture cache is outside the disposable game directory");
        var cached = RingTerrainAtlas.load(cache, baseline.geometry(), baseline.worldHash());
        require(cached.isComplete() && cached.revision() == baseline.revision()
                && cached.contentFingerprint() == baseline.contentFingerprint(), "normal disconnect did not save the expected cache");
        var evidence = client.gameDirectory.toPath().resolve("evidence"); Files.createDirectories(evidence);
        Files.copy(cache, evidence.resolve("cache-identity-original.rwat.gz"));
        cached.putCell(0, 0, baseline.cellHeight(0, 0) + 1, baseline.cellColor(0, 0) ^ 1,
                baseline.cellBlockLight(0, 0) ^ 1, baseline.cellSideColor(0, 0), baseline.cellWaterCoverage(0, 0));
        require(cached.contentFingerprint() != baseline.contentFingerprint()
                && cached.revision() == baseline.revision(), "poison did not isolate content from revision");
        cached.save(cache);
        Files.copy(cache, evidence.resolve("cache-identity-foreign.rwat.gz"));
        RingWorldMod.LOGGER.info("[cache-identity-test] poisoned revision={} original={} foreign={}", cached.revision(),
                Long.toUnsignedString(baseline.contentFingerprint(), 16), Long.toUnsignedString(cached.contentFingerprint(), 16));
    }

    private void finish(Minecraft client) throws IOException {
        require(completedDisconnects == 3 && installs.size() == 3, "missing disconnect or metadata controls");
        var report = new JsonObject(); report.addProperty("schemaVersion", 1);
        report.addProperty("normalDisconnects", completedDisconnects);
        report.addProperty("nativeCacheBytesAfterDisconnect", RingAtlasNativeCacheMemory.liveBytes());
        report.addProperty("revision", baseline.revision());
        report.addProperty("contentFingerprint", Long.toUnsignedString(baseline.contentFingerprint()));
        var observations = new JsonArray();
        for (Install install : installs) {
            var item = new JsonObject(); item.addProperty("presentBeforeStreaming", install.cells);
            item.addProperty("cacheReused", install.reused); item.addProperty("revision", install.revision);
            item.addProperty("serverContent", Long.toUnsignedString(install.serverContent));
            item.addProperty("clientContent", Long.toUnsignedString(install.clientContent)); observations.add(item);
        }
        report.add("metadataInstalls", observations);
        var output = client.gameDirectory.toPath().resolve("evidence/cache-identity.json");
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) { writer.write(report.toString()); }
        stage = 9;
        RingWorldMod.LOGGER.info("[cache-identity-test] PASS: foreign same-revision content rejected before streaming; repaired; exact cache reused; three normal disconnects");
        client.stop();
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
