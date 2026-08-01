package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingWorldProductionLifecycleTest;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Client-owned assertions for the isolated production dimension/reopen regression. */
final class ProductionLifecycleTestClient {
    private static final int DIMENSION_SETTLE_TICKS = 20;
    private static final int STAGE_TIMEOUT_TICKS = 1_800;

    private final String worldName = System.getProperty("ringworld.productionLifecycleWorld", "").trim();
    private int stage;
    private int stageTicks;
    private int dimensionTicks;
    private boolean initialOpenRequested;
    private RingGeometry baselineGeometry;
    private long baselineFingerprint;
    private long baselineAtlasWorldHash;
    private boolean disconnectClearedState;

    boolean tick(Minecraft client) {
        if (!Boolean.getBoolean("ringworld.productionLifecycleTest")) return false;
        if (worldName.isEmpty()) {
            finish(client, false, "missing productionLifecycleWorld");
            return true;
        }
        if (!client.isGameLoadFinished() && client.level == null) {
            stageTicks = 0;
            return true;
        }
        if (++stageTicks > STAGE_TIMEOUT_TICKS) {
            finish(client, false, "timeout stage=" + stage + " dimension=" + dimensionName(client));
            return true;
        }

        switch (stage) {
            case 0 -> captureProductionBaseline(client);
            case 1 -> verifyInactiveDimension(client, Level.NETHER, "nether");
            case 2 -> verifyIntermediateOverworldReturn(client);
            case 3 -> verifyInactiveDimension(client, Level.END, "end");
            case 4 -> verifyReturnAndSave(client);
            case 5 -> reopenCopiedWorld(client);
            case 6 -> verifyReopenedWorld(client);
            default -> { }
        }
        return true;
    }

    private void captureProductionBaseline(Minecraft client) {
        if (client.level == null && client.getSingleplayerServer() == null) {
            if (initialOpenRequested) return;
            initialOpenRequested = true;
            RingWorldMod.LOGGER.info(
                    "[production-lifecycle] opening copied production world '{}' in-process",
                    worldName);
            client.createWorldOpenFlows().openWorld(worldName,
                    () -> finish(client, false, "initial save load cancelled"));
            return;
        }
        if (!isIn(client, Level.OVERWORLD)) return;
        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || !atlas.isComplete()) return;
        if (!isProductionGeometry(geometry)) {
            finish(client, false, "unexpected geometry=" + geometry.circumferenceBlocks()
                    + "x" + geometry.widthBlocks());
            return;
        }
        baselineGeometry = geometry;
        baselineFingerprint = ClientRingState.layoutFingerprint();
        baselineAtlasWorldHash = atlas.worldHash();
        if (baselineFingerprint == 0L) {
            finish(client, false, "missing production layout fingerprint");
            return;
        }
        RingWorldMod.LOGGER.info(
                "[production-lifecycle] baseline geometry={}x{} fingerprint={} atlasComplete=true worldHash={}",
                geometry.circumferenceBlocks(), geometry.widthBlocks(),
                Long.toUnsignedString(baselineFingerprint, 16),
                Long.toUnsignedString(baselineAtlasWorldHash, 16));
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            finish(client, false, "missing integrated server at production baseline");
            return;
        }
        var playerId = client.player.getUUID();
        server.execute(() -> RingWorldProductionLifecycleTest.markClientReady(playerId));
        advance(1);
    }

    private void verifyInactiveDimension(Minecraft client,
                                         net.minecraft.resources.ResourceKey<Level> expected,
                                         String label) {
        if (!isIn(client, expected)) {
            dimensionTicks = 0;
            return;
        }
        if (++dimensionTicks < DIMENSION_SETTLE_TICKS) return;
        boolean inactive = ClientRingState.geometry() == null;
        RingWorldMod.LOGGER.info("[production-lifecycle] {} RingWorld-active={}", label, !inactive);
        if (!inactive) {
            finish(client, false, label + " retained active RingWorld geometry");
            return;
        }
        advance(stage + 1);
    }

    private void verifyReturnAndSave(Minecraft client) {
        if (!isIn(client, Level.OVERWORLD)) {
            dimensionTicks = 0;
            return;
        }
        if (++dimensionTicks < DIMENSION_SETTLE_TICKS) return;
        if (!matchesBaseline()) {
            finish(client, false, "overworld return did not restore baseline state");
            return;
        }
        if (RingWorldProductionLifecycleTest.transferCycleFailed()) {
            finish(client, false, "server transfer coordinator failed");
            return;
        }
        if (!RingWorldProductionLifecycleTest.transferCyclePassed()) return;
        if (client.getSingleplayerServer() == null) {
            finish(client, false, "missing integrated server on overworld return");
            return;
        }
        // disconnectFromWorld stops and saves the integrated server on its own
        // thread. Calling MinecraftServer.saveEverything from this render
        // thread races chunk/entity managers and can corrupt the test run.
        RingWorldMod.LOGGER.info(
                "[production-lifecycle] overworld return restored baseline; requesting normal save-and-disconnect");
        client.disconnectFromWorld(Component.literal("RingWorld production lifecycle regression"));
        advance(5);
    }

    private void verifyIntermediateOverworldReturn(Minecraft client) {
        if (!isIn(client, Level.OVERWORLD)) {
            dimensionTicks = 0;
            return;
        }
        if (++dimensionTicks < DIMENSION_SETTLE_TICKS) return;
        if (!matchesBaseline()) {
            finish(client, false, "intermediate overworld return did not restore baseline state");
            return;
        }
        RingWorldMod.LOGGER.info("[production-lifecycle] intermediate overworld return restored baseline");
        advance(3);
    }

    private void reopenCopiedWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        disconnectClearedState = ClientRingState.geometry() == null
                && ClientRingState.layoutFingerprint() == 0L
                && ClientRingState.terrainAtlas() == null;
        if (!disconnectClearedState) {
            // World teardown and the integrated server stop complete on
            // different ticks. Give the clearClientLevel hook a bounded
            // window instead of sampling that transient gap as a failure.
            if (stageTicks < 200) return;
            finish(client, false, "disconnect did not clear client RingWorld state");
            return;
        }
        RingWorldMod.LOGGER.info("[production-lifecycle] reopening copied production world '{}'; client state cleared=true",
                worldName);
        advance(6);
        client.createWorldOpenFlows().openWorld(worldName,
                () -> finish(client, false, "reopen cancelled"));
    }

    private void verifyReopenedWorld(Minecraft client) {
        if (!isIn(client, Level.OVERWORLD)) {
            dimensionTicks = 0;
            return;
        }
        if (++dimensionTicks < DIMENSION_SETTLE_TICKS) return;
        boolean restored = matchesBaseline();
        finish(client, restored, "reopened geometry=" + geometryName(ClientRingState.geometry())
                + " fingerprint=" + Long.toUnsignedString(ClientRingState.layoutFingerprint(), 16)
                + " atlasComplete=" + (ClientRingState.terrainAtlas() != null
                && ClientRingState.terrainAtlas().isComplete())
                + " disconnectedCleared=" + disconnectClearedState);
    }

    private boolean matchesBaseline() {
        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        return baselineGeometry != null && baselineGeometry.equals(geometry)
                && baselineFingerprint != 0L
                && ClientRingState.layoutFingerprint() == baselineFingerprint
                && atlas != null && atlas.isComplete()
                && atlas.geometry().equals(baselineGeometry)
                && atlas.worldHash() == baselineAtlasWorldHash;
    }

    private static boolean isProductionGeometry(RingGeometry geometry) {
        return geometry.widthBlocks() == RingWorldSettings.DEFAULT_WIDTH
                && geometry.circumferenceBlocks() == RingWorldSettings.DEFAULT_CIRCUMFERENCE;
    }

    private static boolean isIn(Minecraft client, net.minecraft.resources.ResourceKey<Level> dimension) {
        return client.level != null && client.level.dimension() == dimension;
    }

    private static String dimensionName(Minecraft client) {
        return client.level == null ? "none" : client.level.dimension().identifier().toString();
    }

    private static String geometryName(RingGeometry geometry) {
        return geometry == null ? "none" : geometry.circumferenceBlocks() + "x" + geometry.widthBlocks();
    }

    private void advance(int nextStage) {
        stage = nextStage;
        stageTicks = 0;
        dimensionTicks = 0;
    }

    private void finish(Minecraft client, boolean passed, String detail) {
        if (stage == 7) return;
        stage = 7;
        RingWorldMod.LOGGER.info("[production-lifecycle] result={} {}", passed, detail);
        client.stop();
    }
}
