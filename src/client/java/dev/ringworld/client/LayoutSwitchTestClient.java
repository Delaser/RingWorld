package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldStorageAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Opt-in same-process saved-world switch regression.
 *
 * <p>This is intentionally separate from the destructive local smoke world:
 * it opens two existing saves, verifies the first session is cleared during
 * disconnect, and checks that the second handshake installs only its own
 * immutable geometry and atlas.</p>
 */
public final class LayoutSwitchTestClient {
    private static final int JOIN_SETTLE_TICKS = 80;
    private static final int STAGE_TIMEOUT_TICKS = 2_400;
    private static final String EXPECTATION_PROPERTY = "ringworld.layoutSwitchExpectation";

    private final String firstWorld = System.getProperty("ringworld.layoutSwitchFirst", "").trim();
    private final String secondWorld = System.getProperty("ringworld.layoutSwitchSecond", "").trim();
    private int stage;
    private int ticks;
    private boolean expectationChecked;
    private Expectation expectation;
    private long firstFingerprint;
    private RingGeometry firstGeometry;
    private int firstWallHeightBlocks;
    private int firstSurfaceReferenceY;
    private long firstAtlasWorldHash;
    private long firstAtlasContentFingerprint;
    private boolean disconnectClearedState;
    private boolean firstStorageVerified;

    public boolean tick(Minecraft client) {
        if (firstWorld.isEmpty() || secondWorld.isEmpty()) return false;
        if (!checkExpectation(client)) return true;
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
        if (expectation == Expectation.SAME_GEOMETRY_DIFFERENT_SEED && !atlas.isComplete()) return;
        if (!geometry.equals(atlas.geometry())) {
            finish(client, false, "first atlas geometry mismatch");
            return;
        }
        if (!verifyDimensionOwnedStorage(client, firstWorld)) return;

        firstGeometry = geometry;
        firstFingerprint = ClientRingState.layoutFingerprint();
        firstWallHeightBlocks = ClientRingState.wallHeightBlocks();
        firstSurfaceReferenceY = ClientRingState.surfaceReferenceY();
        firstAtlasWorldHash = atlas.worldHash();
        firstAtlasContentFingerprint = atlasContentFingerprint(atlas);
        firstStorageVerified = true;
        RingWorldMod.LOGGER.info(
                "[layout-switch] first session ready: {}x{}, wallHeight={}, surfaceY={}, "
                        + "fingerprint={}, worldHash={}, contentFingerprint={}, atlas={}x{}",
                geometry.circumferenceBlocks(), geometry.widthBlocks(),
                firstWallHeightBlocks, firstSurfaceReferenceY,
                Long.toUnsignedString(firstFingerprint, 16),
                Long.toUnsignedString(firstAtlasWorldHash, 16),
                Long.toUnsignedString(firstAtlasContentFingerprint, 16),
                atlas.columns(), atlas.rows());
        dev.ringworld.client.compat.ClientWorldLifecycle.disconnect(
                client, Component.literal("RingWorld layout-switch regression"));
        advanceTo(2);
    }

    private void startSecondWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        // Equal-size worlds are the stale-GPU failure case: geometry alone
        // cannot prove that a previous world's texture was released.
        disconnectClearedState = RingWorldClientSession.isCleared();
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
        if (expectation == Expectation.SAME_GEOMETRY_DIFFERENT_SEED && !atlas.isComplete()) return;

        long fingerprint = ClientRingState.layoutFingerprint();
        long atlasContentFingerprint = atlasContentFingerprint(atlas);
        boolean sameGeometry = firstGeometry != null && firstGeometry.equals(geometry);
        boolean sameWorldDimensions = sameGeometry
                && firstWallHeightBlocks == ClientRingState.wallHeightBlocks()
                && firstSurfaceReferenceY == ClientRingState.surfaceReferenceY();
        boolean changedIdentity = firstFingerprint != 0L && fingerprint != firstFingerprint
                && atlas.worldHash() != firstAtlasWorldHash;
        boolean expectedRelation = switch (expectation) {
            case DIFFERENT_LAYOUT -> !sameGeometry && changedIdentity;
            case SAME_GEOMETRY_DIFFERENT_SEED -> sameWorldDimensions && changedIdentity
                    && atlasContentFingerprint != firstAtlasContentFingerprint;
        };
        boolean atlasMatchesSecond = geometry.equals(atlas.geometry())
                && ClientRingState.hasServerAtlasWorldHash()
                && ClientRingState.serverAtlasWorldHash() == atlas.worldHash();
        boolean secondStorageVerified = verifyDimensionOwnedStorage(client, secondWorld);
        if (!secondStorageVerified) return;
        boolean passed = disconnectClearedState && firstStorageVerified && expectedRelation
                && atlasMatchesSecond;
        finish(client, passed, "expectation=" + expectation.id + ", second="
                + geometry.circumferenceBlocks() + "x" + geometry.widthBlocks()
                + ", fingerprint="
                + Long.toUnsignedString(fingerprint, 16) + ", atlas="
                + atlas.columns() + "x" + atlas.rows() + ", worldHash="
                + Long.toUnsignedString(atlas.worldHash(), 16) + ", contentFingerprint="
                + Long.toUnsignedString(atlasContentFingerprint, 16));
    }

    private boolean checkExpectation(Minecraft client) {
        if (expectationChecked) return expectation != null;
        expectationChecked = true;
        String configured = System.getProperty(EXPECTATION_PROPERTY, Expectation.DIFFERENT_LAYOUT.id).trim();
        expectation = Expectation.byId(configured);
        if (expectation == null) {
            finish(client, false, "invalid " + EXPECTATION_PROPERTY + "='" + configured
                    + "'; use '" + Expectation.DIFFERENT_LAYOUT.id + "' or '"
                    + Expectation.SAME_GEOMETRY_DIFFERENT_SEED.id + "'");
            return false;
        }
        RingWorldMod.LOGGER.info("[layout-switch] expectation={}", expectation.id);
        return true;
    }

    /**
     * Test-only signature of the received atlas. This runs once per opened
     * world and does not participate in normal renderer texture processing.
     */
    private static long atlasContentFingerprint(RingTerrainAtlas atlas) {
        long fingerprint = 0xCBF29CE484222325L;
        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                long sample = ((long) atlas.cellHeight(column, row) & 0xFFFFL) << 32
                        | Integer.toUnsignedLong(atlas.cellColor(column, row));
                fingerprint ^= sample;
                fingerprint *= 0x100000001B3L;
            }
        }
        fingerprint ^= Integer.toUnsignedLong(atlas.columns());
        fingerprint *= 0x100000001B3L;
        return fingerprint ^ Integer.toUnsignedLong(atlas.rows());
    }

    private void advanceTo(int nextStage) {
        stage = nextStage;
        ticks = 0;
    }

    private void finish(Minecraft client, boolean passed, String detail) {
        if (stage == 4) return;
        stage = 4;
        RingWorldMod.LOGGER.info("[layout-switch] result={}, {}", passed, detail);
        RingWorldMod.LOGGER.info("[layout-switch] result-json={\"passed\":{},\"detail\":\"{}\"}",
                passed, detail.replace("\\", "\\\\").replace("\"", "\\\""));
        client.stop();
    }

    private enum Expectation {
        DIFFERENT_LAYOUT("different-layout"),
        SAME_GEOMETRY_DIFFERENT_SEED("same-geometry-different-seed");

        private final String id;

        Expectation(String id) {
            this.id = id;
        }

        private static Expectation byId(String candidate) {
            for (Expectation expectation : values()) {
                if (expectation.id.equals(candidate)) return expectation;
            }
            return null;
        }
    }

    /**
     * A copied legacy world may retain the former root data files, but an
     * opened 26.1 session must have materialized both active files under the
     * Overworld's own storage path. This checks the server-owned path rather
     * than reconstructing a version-specific dimension directory name.
     */
    private boolean verifyDimensionOwnedStorage(Minecraft client, String worldName) {
        var server = client.getSingleplayerServer();
        if (server == null) return false;
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return false;

        Path dimensionPath = RingWorldStorageAccess.dimensionPath(overworld);
        Path settings = dimensionPath.resolve("data/ringworld/settings.dat");
        Path atlas = dimensionPath.resolve("data/ringworld/terrain-atlas.rwat.gz");
        if (!Files.isRegularFile(settings) || !Files.isRegularFile(atlas)) return false;

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        boolean legacySettingsPreserved = !Files.exists(worldRoot.resolve("data/ringworld_settings.dat"))
                || Files.isRegularFile(worldRoot.resolve("data/ringworld_settings.dat"));
        boolean legacyAtlasPreserved = !Files.exists(worldRoot.resolve("data/ringworld-terrain-atlas.rwat.gz"))
                || Files.isRegularFile(worldRoot.resolve("data/ringworld-terrain-atlas.rwat.gz"));
        if (!legacySettingsPreserved || !legacyAtlasPreserved) {
            finish(client, false, "legacy storage changed while opening " + worldName);
            return false;
        }
        RingWorldMod.LOGGER.info("[layout-switch] dimension storage ready for '{}': settings={}, atlas={}",
                worldName, settings, atlas);
        return true;
    }
}
