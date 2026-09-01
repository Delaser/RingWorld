package dev.ringworld.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guard for the opt-in, one-world optional-visual Atlas phase. */
class RingOptionalVisualSmokeSourceContractTest {
    private static final Path ROOT = findProjectRoot();

    @Test
    void existingAtlasGateStaysDefaultAndOptionalPhaseFailsClosed() throws IOException {
        String source = read(
                "src/client/java/dev/ringworld/client/AtlasPregenerationUiTestClient.java");

        assertTrue(source.contains(
                "OPTIONAL_VISUAL_PROPERTY = \"ringworld.optionalVisualSmoke\""));
        assertTrue(source.contains(
                "Boolean.getBoolean(ENABLE_PROPERTY) || optionalVisualEnabled()"));
        assertTrue(source.contains(
                "if (optionalVisualEnabled()) return optionalVisual.tick(client)"));
        assertTrue(source.contains(
                "if (!worldStarted && client.player == null) return false"));
        assertTrue(source.contains(
                "Atlas completed before mandatory staged-preview capture"));
        assertTrue(source.contains(
                "incompleteAtlas && !client.levelRenderer.hasRenderedAllSections()"));
        assertFalse(source.contains("partial-preview skipped"));
    }

    @Test
    void optionalPhaseOwnsEightBoundedCapturesAndRequiredRuntimeTransitions()
            throws IOException {
        String source = read(
                "src/client/java/dev/ringworld/client/AtlasPregenerationUiTestClient.java");

        for (int index = 1; index <= 8; index++) {
            assertTrue(source.contains(String.format("optional-visual-%02d-", index)));
        }
        assertTrue(source.contains("mixinRender=true shaderDraw=true"));
        assertTrue(source.contains("sendCommand(\"ringworld sky night\")"));
        assertTrue(source.contains("sendCommand(\"ringworld sun large\")"));
        assertTrue(source.contains("sendCommand(\"ringworld sky void\")"));
        assertTrue(source.contains("sendCommand(\"ringworld sun none\")"));
        assertTrue(source.contains("RedstoneLampBlock.LIT, lit"));
        assertTrue(source.contains("ClientWorldLifecycle.disconnect"));
        assertTrue(source.contains("reopening same disposable world"));
    }

    @Test
    void bothLoaderRunsUseTheSameOptInPropertyAndVerifier() throws IOException {
        String fabric = read("build.gradle");
        String neoForge = read("neoforge/build.gradle");

        assertTrue(fabric.contains("optionalVisualSmokeClient"));
        assertTrue(fabric.contains("-Dringworld.optionalVisualSmoke=true"));
        assertTrue(fabric.contains("verifyOptionalVisualSmokeOutputs"));
        assertTrue(fabric.contains("optional-visual-01-partial-preview"));
        assertTrue(fabric.contains("optional-visual-08-reopen-void-none"));
        assertTrue(neoForge.contains("optionalVisualSmokeClient"));
        assertTrue(neoForge.contains(
                "systemProperty 'ringworld.optionalVisualSmoke', 'true'"));
        assertTrue(neoForge.contains("rootProject.ext.verifyOptionalVisualSmokeOutputs"));
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
