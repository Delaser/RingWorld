package dev.ringworld.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingCompatibilityContractTest {
    @Test
    void contractIsVersionedAndMatchesOnlyExplicitLoadedIds() {
        assertEquals(1, RingCompatibilityContract.VERSION);
        assertEquals(1, RingWorldApi.API_VERSION);

        var conflicts = RingCompatibilityContract.findLoadedConflicts(
                List.of("minecraft", "fabric-api", "SODIUM", "example-safe-mod"));
        assertEquals(1, conflicts.size());
        assertEquals("sodium", conflicts.getFirst().modId());
        assertEquals(RingCompatibilityContract.Area.RENDERER, conflicts.getFirst().area());
    }

    @Test
    void baselineFabricStackHasNoKnownConflict() {
        assertTrue(RingCompatibilityContract.findLoadedConflicts(
                List.of("minecraft", "fabricloader", "fabric-api", "ringworld")).isEmpty());
    }

    @Test
    void publishedInventoryCannotBeMutatedByCallers() {
        assertThrows(UnsupportedOperationException.class,
                () -> RingCompatibilityContract.knownConflicts().clear());
    }

    @Test
    void fabricMetadataAdvertisesTheSameCompatibilityApiVersion() throws Exception {
        try (var stream = RingCompatibilityContractTest.class.getClassLoader()
                .getResourceAsStream("fabric.mod.json")) {
            if (stream == null) throw new IllegalStateException("fabric.mod.json is missing");
            var root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(RingCompatibilityContract.VERSION,
                    root.getAsJsonObject("custom")
                            .get("ringworld:compatibility_api").getAsInt());
        }
    }
}
