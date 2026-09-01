package dev.ringworld.server;

import dev.ringworld.world.RingTerrainPreviewStage;

/** Thread-safe publication gate for one immutable-world preview generation. */
final class RingTerrainPreviewJobState {
    private final long worldHash;
    private boolean cancelled;
    private byte[] latestData;
    private RingTerrainPreviewStage latestStage;

    RingTerrainPreviewJobState(long worldHash) { this.worldHash = worldHash; }

    synchronized boolean publish(long currentWorldHash, RingTerrainPreviewStage stage,
                                 byte[] encoded) {
        if (cancelled || currentWorldHash != worldHash
                || latestStage != null && stage.wireValue() <= latestStage.wireValue()) {
            return false;
        }
        latestData = encoded.clone();
        latestStage = stage;
        return true;
    }

    synchronized Snapshot latest() {
        return latestStage == null ? null : new Snapshot(latestStage, latestData.clone());
    }

    synchronized void cancel() { cancelled = true; }
    synchronized boolean cancelled() { return cancelled; }
    long worldHash() { return worldHash; }

    record Snapshot(RingTerrainPreviewStage stage, byte[] data) { }
}
