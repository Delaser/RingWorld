package dev.ringworld.world;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingSavedDataGuardTest {
    @Test void onlyAConfirmedMissingPathPermitsDefaults(@TempDir Path root) throws Exception {
        Path file = root.resolve("data/ringworld/building_settings.dat");
        assertDoesNotThrow(() -> RingSavedDataGuard.requireMissing(file));
        Files.createDirectories(file.getParent());
        byte[] corrupt = {31, -117, 0, 0};
        Files.write(file, corrupt);
        var failure = assertThrows(IllegalStateException.class, () -> RingSavedDataGuard.requireMissing(file));
        assertTrue(failure.getMessage().contains("Refusing to replace saved state"));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
    }

    @Test void anInvalidDirectoryAtTheFilePathIsNotAbsence(@TempDir Path root) throws Exception {
        Path file = root.resolve("settings.dat");
        Files.createDirectory(file);
        assertThrows(IllegalStateException.class, () -> RingSavedDataGuard.requireMissing(file));
        assertTrue(Files.isDirectory(file));
    }
}
