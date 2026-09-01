package dev.ringworld.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loader-adapter guard for the shared client/server command root. */
class RingClientCommandRoutingSourceContractTest {
    private static final Path ROOT = findProjectRoot();

    @Test
    void fabricAndNeoForgeUseTheSameServerFallbackBoundary() throws IOException {
        String fabric = read(
                "src/platform/fabricClient/java/dev/ringworld/client/RingWorldClient.java");
        String neoForge = read("src/platform/neoforgeClient/java/dev/ringworld/platform/"
                + "neoforge/NeoForgeRingWorldClient.java");

        assertTrue(fabric.contains(
                "RingClientCommandRouting.withServerFallback("));
        assertTrue(fabric.contains(
                "ClientCommandManager.literal(\"ringworld\").then(ringLights)"));
        assertTrue(neoForge.contains(
                "RingClientCommandRouting.withServerFallback("));
        assertTrue(neoForge.contains(
                "Commands.literal(\"ringworld\").then(ringLights)"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "client", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("RingWorld project root is unavailable");
    }
}
