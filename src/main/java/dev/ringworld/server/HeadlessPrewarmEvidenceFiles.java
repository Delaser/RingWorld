package dev.ringworld.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem-only evidence hygiene for the dedicated headless adapter. */
final class HeadlessPrewarmEvidenceFiles {
    private HeadlessPrewarmEvidenceFiles() { }

    /**
     * Removes evidence from a previous run before publishing a new job. A
     * crash before the next progress/result write therefore cannot leave a
     * stale COMPLETE result for a direct JVM launch to be mistaken as current.
     */
    static void resetForNewRun(Path resultPath) throws IOException {
        Files.deleteIfExists(resultPath);
        Files.deleteIfExists(resultPath.resolveSibling("progress.json"));
        Files.deleteIfExists(resultPath.resolveSibling(resultPath.getFileName() + ".tmp"));
        Files.deleteIfExists(resultPath.resolveSibling("progress.json.tmp"));
    }
}
