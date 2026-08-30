package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainPreviewPayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPosition;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client copy of the immutable settings received during login. */
public final class ClientRingState {
    private static volatile RingGeometry geometry;
    private static volatile int wallHeightBlocks;
    private static volatile int surfaceReferenceY;
    private static volatile int terrainNoiseMapping;
    private static volatile int settingsFormatVersion;
    private static volatile RingWallStyle wallStyle = RingWallStyle.LEGACY;
    private static volatile RingSkyProfile skyProfile = RingSkyProfile.DEFAULT;
    private static volatile long generatorSeed;
    private static volatile long layoutFingerprint;
    @Nullable private static volatile RingPosition cameraPosition;
    private static volatile long cameraSeamCrossings;
    private static volatile long seamCorrectionPackets;
    @Nullable private static volatile RingTerrainAtlas terrainAtlas;
    @Nullable private static volatile RingTerrainPreview terrainPreview;
    private static volatile int terrainPreviewStage = -1;
    private static volatile long serverAtlasWorldHash;
    private static volatile boolean hasServerAtlasWorldHash;
    @Nullable private static Path terrainAtlasCachePath;
    private static int terrainAtlasRevision;
    private static boolean terrainAtlasDirty;
    private static boolean terrainAtlasPendingRender;
    private static long lastTerrainAtlasSaveMillis;
    private static long lastTerrainAtlasPublishMillis;
    private static long terrainAtlasPendingSinceMillis;
    private static long lastTerrainAtlasChangeMillis;
    private static Path cacheDirectory = Path.of("ringworld-cache");

    private ClientRingState() { }

    /** Platform bootstrap supplies its game-local cache directory before login. */
    public static synchronized void configureCacheDirectory(Path directory) {
        if (directory == null) throw new IllegalArgumentException("cache directory is required");
        cacheDirectory = directory.toAbsolutePath().normalize();
    }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, long newLayoutFingerprint) {
        set(newGeometry, newWallHeightBlocks, newSurfaceReferenceY,
                dev.ringworld.world.RingTerrainNoiseMapping.CURRENT,
                newLayoutFingerprint);
    }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, int newTerrainNoiseMapping,
                           long newLayoutFingerprint) {
        set(newGeometry, newWallHeightBlocks, newSurfaceReferenceY,
                newTerrainNoiseMapping, RingWallStyle.LEGACY, RingSkyProfile.DEFAULT,
                newLayoutFingerprint);
    }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, int newTerrainNoiseMapping,
                           RingWallStyle newWallStyle, long newLayoutFingerprint) {
        set(newGeometry, newWallHeightBlocks, newSurfaceReferenceY, newTerrainNoiseMapping,
                newWallStyle, RingSkyProfile.DEFAULT, newLayoutFingerprint);
    }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, int newTerrainNoiseMapping,
                           RingWallStyle newWallStyle, RingSkyProfile newSkyProfile,
                           long newLayoutFingerprint) {
        set(newGeometry, newWallHeightBlocks, newSurfaceReferenceY, newTerrainNoiseMapping,
                newWallStyle, newSkyProfile, 0L, newLayoutFingerprint);
    }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, int newTerrainNoiseMapping,
                           RingWallStyle newWallStyle, RingSkyProfile newSkyProfile,
                           long newGeneratorSeed, long newLayoutFingerprint) {
        set(newGeometry, newWallHeightBlocks, newSurfaceReferenceY, newTerrainNoiseMapping,
                newWallStyle, newSkyProfile, newGeneratorSeed,
                RingWorldSettings.FORMAT_VERSION, newLayoutFingerprint);
    }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, int newTerrainNoiseMapping,
                           RingWallStyle newWallStyle, RingSkyProfile newSkyProfile,
                           long newGeneratorSeed, int newSettingsFormatVersion,
                           long newLayoutFingerprint) {
        geometry = newGeometry;
        wallHeightBlocks = newWallHeightBlocks;
        surfaceReferenceY = newSurfaceReferenceY;
        terrainNoiseMapping = dev.ringworld.world.RingTerrainNoiseMapping.requireSupported(
                newTerrainNoiseMapping);
        if (newSettingsFormatVersion < 1
                || newSettingsFormatVersion > RingWorldSettings.FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported RingWorld settings format "
                    + newSettingsFormatVersion);
        }
        settingsFormatVersion = newSettingsFormatVersion;
        wallStyle = java.util.Objects.requireNonNull(newWallStyle, "wallStyle");
        skyProfile = java.util.Objects.requireNonNull(newSkyProfile, "skyProfile");
        generatorSeed = newGeneratorSeed;
        layoutFingerprint = newLayoutFingerprint;
        cameraPosition = null;
        cameraSeamCrossings = 0;
        seamCorrectionPackets = 0;
        terrainAtlas = null;
        terrainPreview = null;
        terrainPreviewStage = -1;
        serverAtlasWorldHash = 0L;
        hasServerAtlasWorldHash = false;
        terrainAtlasCachePath = null;
        terrainAtlasRevision++;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        terrainAtlasPendingSinceMillis = 0L;
        lastTerrainAtlasChangeMillis = 0L;
        lastTerrainAtlasSaveMillis = 0L;
        lastTerrainAtlasPublishMillis = 0L;
    }

    @Nullable
    public static RingGeometry geometry() {
        Minecraft client = Minecraft.getInstance();
        return geometry != null && client.level != null
                && client.level.dimension() == Level.OVERWORLD
                ? geometry : null;
    }

    public static int wallHeightBlocks() { return wallHeightBlocks; }
    public static int surfaceReferenceY() { return surfaceReferenceY; }
    public static int terrainNoiseMapping() { return terrainNoiseMapping; }
    public static int settingsFormatVersion() { return settingsFormatVersion; }
    public static RingWallStyle wallStyle() { return wallStyle; }
    public static RingSkyProfile skyProfile() { return skyProfile; }
    public static long generatorSeed() { return generatorSeed; }
    public static void setSkyProfile(RingSkyProfile profile) {
        skyProfile = java.util.Objects.requireNonNull(profile, "profile");
    }
    public static long layoutFingerprint() { return layoutFingerprint; }

    /**
     * Tracks the continuous client presentation chart. It also protects against a canonical
     * correction from an older server without interpolating across the full
     * circumference.
     */
    public static void updateCameraPosition(double canonicalX) {
        RingGeometry currentGeometry = geometry();
        if (currentGeometry == null) return;
        RingPosition previous = cameraPosition;
        if (previous == null) {
            cameraPosition = RingPosition.fromPresentationX(canonicalX, currentGeometry);
            return;
        }
        double nearest = currentGeometry.nearestImageX(canonicalX, previous.presentationX(currentGeometry));
        RingPosition next = RingPosition.fromPresentationX(nearest, currentGeometry);
        if (next.chartIndex() != previous.chartIndex()) cameraSeamCrossings++;
        cameraPosition = next;
    }

    @Nullable
    public static RingPosition cameraPosition() { return cameraPosition; }

    public static long cameraSeamCrossings() { return cameraSeamCrossings; }

    public static void recordSeamCorrectionPacket() { seamCorrectionPackets++; }

    public static long seamCorrectionPackets() { return seamCorrectionPackets; }

    /** Installs metadata and reuses a complete world-hash cache when available. */
    public static boolean installTerrainAtlas(RingTerrainAtlasMetadataPayload metadata) {
        RingGeometry current = geometry;
        if (current == null || metadata.tileSize() != RingTerrainAtlas.TILE_SIZE) return false;
        RingTerrainAtlas replacement = null;
        Path cache = cacheDirectory.resolve(
                "terrain-" + Long.toUnsignedString(metadata.worldHash(), 16) + ".rwat.gz");
        if (metadata.revision() < 0L) return false;
        if (Files.exists(cache)) {
            try {
                replacement = RingTerrainAtlas.load(cache, current, metadata.worldHash());
                if (replacement.revision() != metadata.revision()) replacement = null;
            } catch (IOException exception) {
                RingWorldMod.LOGGER.warn("Ignoring invalid client RingWorld terrain cache {}", cache, exception);
            }
        }
        if (replacement == null) {
            replacement = new RingTerrainAtlas(current, metadata.worldHash(), metadata.sampleStep());
        }
        if (replacement.columns() != metadata.columns() || replacement.rows() != metadata.rows()) {
            RingWorldMod.LOGGER.warn("Server RingWorld terrain atlas dimensions do not match ring geometry");
            return false;
        }
        terrainAtlas = replacement;
        serverAtlasWorldHash = metadata.worldHash();
        hasServerAtlasWorldHash = true;
        terrainAtlasCachePath = cache;
        terrainAtlasRevision++;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        RingWorldMod.LOGGER.info("RingWorld terrain atlas ready: {}/{} cached cells ({}%)",
                replacement.presentCount(), replacement.cellCount(),
                Math.round(replacement.completion() * 1000.0) / 10.0);
        return replacement.isComplete() && replacement.revision() == metadata.revision();
    }

    public static void applyTerrainAtlasTile(long worldHash, int tileX, int tileZ, byte[] data) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != worldHash) return;
        try {
            boolean wasComplete = atlas.isComplete();
            if (!atlas.applyTile(tileX, tileZ, data)) return;
            boolean becameComplete = !wasComplete && atlas.isComplete();
            terrainAtlasDirty = true;
            long now = System.currentTimeMillis();
            if (!terrainAtlasPendingRender) terrainAtlasPendingSinceMillis = now;
            terrainAtlasPendingRender = true;
            lastTerrainAtlasChangeMillis = now;
            // Force the first complete surface immediately. Later updates to
            // an already-complete atlas use the normal coalescing windows so
            // a dirty-tile burst cannot rebuild the full texture and mesh for
            // every packet.
            publishTerrainAtlasIfDue(becameComplete);
            saveTerrainAtlasIfDue(becameComplete);
            if (becameComplete) {
                RingWorldMod.LOGGER.info("RingWorld terrain atlas download complete: {} cells",
                        atlas.presentCount());
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain atlas tile {},{}", tileX, tileZ, exception);
        }
    }

    /** Durably acknowledges an ordered server tile batch only after it is complete. */
    public static void commitTerrainAtlasRevision(long worldHash, long revision) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != worldHash) return;
        try {
            if (!atlas.commitRevision(revision)) return;
            terrainAtlasDirty = true;
            // Changed tiles already request a visual publication. A revision
            // commit is a durable transaction marker, not a texture change;
            // forcing another render generation here rebuilt an identical
            // complete-ring texture after every tile batch.
            saveTerrainAtlasCacheIfDue(true);
            RingWorldMod.LOGGER.info("RingWorld terrain atlas revision {} committed", revision);
        } catch (IOException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain atlas revision {}", revision, exception);
        }
    }

    @Nullable
    public static RingTerrainAtlas terrainAtlas() { return terrainAtlas; }
    @Nullable
    public static RingTerrainPreview terrainPreview() { return terrainPreview; }
    public static int terrainPreviewStage() { return terrainPreviewStage; }

    public static void installTerrainPreview(RingTerrainPreviewPayload payload) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != payload.worldHash()) return;
        try {
            RingTerrainPreviewStage stage = RingTerrainPreviewStage.fromWireValue(payload.stage());
            RingTerrainPreview decoded = RingTerrainPreview.decode(payload.data());
            if (decoded.worldHash() != payload.worldHash()) {
                RingWorldMod.LOGGER.warn("Rejected RingWorld terrain preview with mismatched world identity");
                return;
            }
            terrainPreview = decoded;
            terrainPreviewStage = stage.wireValue();
            terrainAtlasRevision++;
            RingWorldMod.LOGGER.info("RingWorld {} seed preview ready: {}x{} ({} compressed bytes)",
                    stage.logLabel(), decoded.columns(), decoded.rows(), payload.data().length);
        } catch (IOException | RuntimeException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain preview", exception);
        }
    }
    public static int terrainAtlasRevision() { return terrainAtlasRevision; }
    public static long terrainAtlasDurableRevision() {
        RingTerrainAtlas atlas = terrainAtlas;
        return atlas == null ? 0L : atlas.revision();
    }
    public static long serverAtlasWorldHash() { return serverAtlasWorldHash; }
    public static boolean hasServerAtlasWorldHash() { return hasServerAtlasWorldHash; }

    /** Raw teardown assertion; unlike {@link #geometry()}, this does not depend on the active level. */
    public static boolean sessionCleared() {
        return geometry == null
                && wallHeightBlocks == 0
                && surfaceReferenceY == 0
                && terrainNoiseMapping == 0
                && settingsFormatVersion == 0
                && layoutFingerprint == 0L
                && cameraPosition == null
                && cameraSeamCrossings == 0L
                && seamCorrectionPackets == 0L
                && terrainAtlas == null
                && terrainPreview == null
                && terrainPreviewStage == -1
                && serverAtlasWorldHash == 0L
                && !hasServerAtlasWorldHash
                && terrainAtlasCachePath == null
                && !terrainAtlasDirty
                && !terrainAtlasPendingRender;
    }

    /** Coalesces partial-cache writes while saving a newly completed atlas immediately. */
    public static void saveTerrainAtlasIfDue(boolean force) {
        publishTerrainAtlasIfDue(force);
        saveTerrainAtlasCacheIfDue(force);
    }

    private static void saveTerrainAtlasCacheIfDue(boolean force) {
        RingTerrainAtlas atlas = terrainAtlas;
        Path cache = terrainAtlasCachePath;
        if (!terrainAtlasDirty || atlas == null || cache == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastTerrainAtlasSaveMillis < 10_000L) return;
        try {
            atlas.save(cache);
            terrainAtlasDirty = false;
            lastTerrainAtlasSaveMillis = now;
        } catch (IOException exception) {
            RingWorldMod.LOGGER.error("Could not save client RingWorld terrain atlas " + cache, exception);
        }
    }

    /** Avoids rebuilding the complete-ring texture once per incoming network tile. */
    private static void publishTerrainAtlasIfDue(boolean force) {
        if (!terrainAtlasPendingRender) return;
        long now = System.currentTimeMillis();
        if (!force) {
            RingTerrainAtlas atlas = terrainAtlas;
            if (atlas != null && atlas.isComplete()) {
                // Distant LOD may trail authoritative block state briefly.
                // Batch natural leaf/fluid/terrain settling into one upload
                // after three quiet seconds, while bounding continuous churn
                // to a ten-second maximum delay.
                boolean quiet = now - lastTerrainAtlasChangeMillis >= 3_000L;
                boolean maximumDelayReached = now - terrainAtlasPendingSinceMillis >= 10_000L;
                if (!quiet && !maximumDelayReached) return;
            } else if (now - lastTerrainAtlasPublishMillis < 1_000L) {
                return;
            }
        }
        terrainAtlasRevision++;
        terrainAtlasPendingRender = false;
        lastTerrainAtlasPublishMillis = now;
        terrainAtlasPendingSinceMillis = 0L;
    }

    /** Starts a fresh track after an intentional test-only long teleport. */
    public static void resetCameraContinuity(double logicalX) {
        RingGeometry currentGeometry = geometry();
        if (currentGeometry == null) return;
        cameraPosition = RingPosition.fromPresentationX(logicalX, currentGeometry);
        cameraSeamCrossings = 0;
        seamCorrectionPackets = 0;
    }

    public static void clear() {
        saveTerrainAtlasIfDue(true);
        geometry = null;
        wallHeightBlocks = 0;
        surfaceReferenceY = 0;
        terrainNoiseMapping = 0;
        settingsFormatVersion = 0;
        layoutFingerprint = 0L;
        generatorSeed = 0L;
        cameraPosition = null;
        cameraSeamCrossings = 0;
        seamCorrectionPackets = 0;
        terrainAtlas = null;
        terrainPreview = null;
        terrainPreviewStage = -1;
        serverAtlasWorldHash = 0L;
        hasServerAtlasWorldHash = false;
        terrainAtlasCachePath = null;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        lastTerrainAtlasSaveMillis = 0L;
        lastTerrainAtlasPublishMillis = 0L;
        terrainAtlasPendingSinceMillis = 0L;
        lastTerrainAtlasChangeMillis = 0L;
        terrainAtlasRevision++;
    }
}
