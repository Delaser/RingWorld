package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPosition;
import dev.ringworld.world.RingTerrainAtlas;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client copy of the immutable settings received during login. */
public final class ClientRingState {
    private static volatile RingGeometry geometry;
    private static volatile int wallHeightBlocks;
    private static volatile int surfaceReferenceY;
    private static volatile long layoutFingerprint;
    @Nullable private static volatile RingPosition cameraPosition;
    private static volatile long cameraSeamCrossings;
    private static volatile long seamCorrectionPackets;
    @Nullable private static volatile RingTerrainAtlas terrainAtlas;
    @Nullable private static Path terrainAtlasCachePath;
    private static int terrainAtlasRevision;
    private static boolean terrainAtlasDirty;
    private static boolean terrainAtlasPendingRender;
    private static long lastTerrainAtlasSaveMillis;
    private static long lastTerrainAtlasPublishMillis;

    private ClientRingState() { }

    public static void set(RingGeometry newGeometry, int newWallHeightBlocks,
                           int newSurfaceReferenceY, long newLayoutFingerprint) {
        geometry = newGeometry;
        wallHeightBlocks = newWallHeightBlocks;
        surfaceReferenceY = newSurfaceReferenceY;
        layoutFingerprint = newLayoutFingerprint;
        cameraPosition = null;
        cameraSeamCrossings = 0;
        seamCorrectionPackets = 0;
        terrainAtlas = null;
        terrainAtlasCachePath = null;
        terrainAtlasRevision++;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
    }

    @Nullable
    public static RingGeometry geometry() {
        MinecraftClient client = MinecraftClient.getInstance();
        return geometry != null && client.world != null
                && client.world.getRegistryKey() == World.OVERWORLD
                ? geometry : null;
    }

    public static int wallHeightBlocks() { return wallHeightBlocks; }
    public static int surfaceReferenceY() { return surfaceReferenceY; }
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
        Path cache = FabricLoader.getInstance().getGameDir().resolve("ringworld-cache")
                .resolve("terrain-" + Long.toUnsignedString(metadata.worldHash(), 16) + ".rwat.gz");
        if (Files.exists(cache)) {
            try {
                replacement = RingTerrainAtlas.load(cache, current, metadata.worldHash());
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
        terrainAtlasCachePath = cache;
        terrainAtlasRevision++;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        RingWorldMod.LOGGER.info("RingWorld terrain atlas ready: {}/{} cached cells ({}%)",
                replacement.presentCount(), replacement.cellCount(),
                Math.round(replacement.completion() * 1000.0) / 10.0);
        return replacement.isComplete();
    }

    public static void applyTerrainAtlasTile(long worldHash, int tileX, int tileZ, byte[] data) {
        RingTerrainAtlas atlas = terrainAtlas;
        if (atlas == null || atlas.worldHash() != worldHash) return;
        try {
            atlas.applyTile(tileX, tileZ, data);
            terrainAtlasDirty = true;
            terrainAtlasPendingRender = true;
            publishTerrainAtlasIfDue(atlas.isComplete());
            saveTerrainAtlasIfDue(atlas.isComplete());
            if (atlas.isComplete()) {
                RingWorldMod.LOGGER.info("RingWorld terrain atlas download complete: {} cells",
                        atlas.presentCount());
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            RingWorldMod.LOGGER.warn("Rejected invalid RingWorld terrain atlas tile {},{}", tileX, tileZ, exception);
        }
    }

    @Nullable
    public static RingTerrainAtlas terrainAtlas() { return terrainAtlas; }
    public static int terrainAtlasRevision() { return terrainAtlasRevision; }

    /** Coalesces partial-cache writes while saving a newly completed atlas immediately. */
    public static void saveTerrainAtlasIfDue(boolean force) {
        RingTerrainAtlas atlas = terrainAtlas;
        Path cache = terrainAtlasCachePath;
        publishTerrainAtlasIfDue(force);
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

    /** Avoids rebuilding the 110k-vertex Arch once per incoming network tile. */
    private static void publishTerrainAtlasIfDue(boolean force) {
        if (!terrainAtlasPendingRender) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastTerrainAtlasPublishMillis < 1_000L) return;
        terrainAtlasRevision++;
        terrainAtlasPendingRender = false;
        lastTerrainAtlasPublishMillis = now;
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
        layoutFingerprint = 0L;
        cameraPosition = null;
        cameraSeamCrossings = 0;
        seamCorrectionPackets = 0;
        terrainAtlas = null;
        terrainAtlasCachePath = null;
        terrainAtlasDirty = false;
        terrainAtlasPendingRender = false;
        terrainAtlasRevision++;
    }
}
