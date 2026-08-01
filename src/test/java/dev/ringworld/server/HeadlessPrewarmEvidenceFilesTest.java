package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HeadlessPrewarmEvidenceFilesTest {
    @Test
    void removesPreviousTerminalAndProgressEvidenceBeforeEveryRun(@TempDir Path directory) throws Exception {
        Path result = directory.resolve("result.json");
        Files.writeString(result, "old complete");
        Files.writeString(directory.resolve("progress.json"), "old progress");
        Files.writeString(directory.resolve("result.json.tmp"), "old temporary");

        HeadlessPrewarmEvidenceFiles.resetForNewRun(result);

        assertFalse(Files.exists(result));
        assertFalse(Files.exists(directory.resolve("progress.json")));
        assertFalse(Files.exists(directory.resolve("result.json.tmp")));
    }
}
