package dev.ringworld.world;

/** Terminal status written by a platform-owned headless prewarm coordinator. */
public enum AtlasPregenerationReportStatus {
    COMPLETE,
    FAILED,
    INTERRUPTED,
    REJECTED
}
