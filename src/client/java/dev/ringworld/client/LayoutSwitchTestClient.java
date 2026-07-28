package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Opt-in same-process saved-world switch regression.
 *
 * <p>This is intentionally separate from the destructive local smoke world:
 * it opens two existing saves, verifies the first session is cleared during
 * disconnect, and checks that the second handshake installs only its own
 * immutable geometry and atlas.</p>
 */
final class LayoutSwitchTestClient {
    private static final int JOIN_SETTLE_TICKS = 80;
    private static final int STAGE_TIMEOUT_TICKS = 2_400;

    private final String firstWorld = System.getProperty("ringworld.layoutSwitchFirst", "").trim();
    private final String secondWorld = System.getProperty("ringworld.layoutSwitchSecond", "").trim();
    private int stage;
    private int ticks;
    private long firstFingerprint;
    private RingGeometry firstGeometry;
    private boolean disconnectClearedState;

    boolean tick(Minecraft client) {
        if (firstWorld.isEmpty() || secondWorld.isEmpty()) return false;
        if (++ticks > STAGE_TIMEOUT_TICKS) {
            finish(client, false, "timed out in stage " + stage);
            return true;
        }

        switch (stage) {
            case 0 -> startFirstWorld(client);
            case 1 -> captureFirstWorld(client);
            case 2 -> startSecondWorld(client);
            case 3 -> verifySecondWorld(client);
            default -> {
            }
        }
        return true;
    }

    private void startFirstWorld(Minecraft client) {
        if (!client.isGameLoadFinished() || client.level != null || client.getSingleplayerServer() != null) return;
        RingWorldMod.LOGGER.info("[layout-switch] opening first save '{}'", firstWorld);
        advanceTo(1);
        client.createWorldOpenFlows().openWorld(firstWorld,
                () -> finish(client, false, "first save load cancelled"));
    }

    private void captureFirstWorld(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || ticks < JOIN_SETTLE_TICKS) return;
        if (!geometry.equals(atlas.geometry())) {
            finish(client, false, "first atlas geometry mismatch");
            return;
        }

        firstGeometry = geometry;
        firstFingerprint = ClientRingState.layoutFingerprint();
        RingWorldMod.LOGGER.info(
                "[layout-switch] first session ready: {}x{}, fingerprint={}, atlas={}x{}",
                geometry.circumferenceBlocks(), geometry.widthBlocks(),
                Long.toUnsignedString(firstFingerprint, 16), atlas.columns(), atlas.rows());
        client.disconnectFromWorld(Component.literal("RingWorld layout-switch regression"));
        advanceTo(2);
    }

    private void startSecondWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        disconnectClearedState = ClientRingState.layoutFingerprint() == 0L
                && ClientRingState.terrainAtlas() == null;
        RingWorldMod.LOGGER.info("[layout-switch] disconnect cleared client state={}",
                disconnectClearedState);
        RingWorldMod.LOGGER.info("[layout-switch] opening second save '{}'", secondWorld);
        advanceTo(3);
        client.createWorldOpenFlows().openWorld(secondWorld,
                () -> finish(client, false, "second save load cancelled"));
    }

    private void verifySecondWorld(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || ticks < JOIN_SETTLE_TICKS) return;

        long fingerprint = ClientRingState.layoutFingerprint();
        boolean changedLayout = firstGeometry != null && !firstGeometry.equals(geometry)
                && firstFingerprint != 0L && fingerprint != firstFingerprint;
        boolean atlasMatchesSecond = geometry.equals(atlas.geometry());
        boolean passed = disconnectClearedState && changedLayout && atlasMatchesSecond;
        finish(client, passed, "second=" + geometry.circumferenceBlocks() + "x"
                + geometry.widthBlocks() + ", fingerprint="
                + Long.toUnsignedString(fingerprint, 16) + ", atlas="
                + atlas.columns() + "x" + atlas.rows());
    }

    private void advanceTo(int nextStage) {
        stage = nextStage;
        ticks = 0;
    }

    private void finish(Minecraft client, boolean passed, String detail) {
        if (stage == 4) return;
        stage = 4;
        RingWorldMod.LOGGER.info("[layout-switch] result={}, {}", passed, detail);
        client.stop();
    }
}
