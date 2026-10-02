package dev.ringworld.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-ABI guard for the version-owned menu-only preview adapter. */
class RingCreationPreviewSourceContractTest {
    private static final Path CLIENT_ROOT = findProjectRoot().resolve(Path.of(
            "src", "client", "java", "dev", "ringworld", "client"));

    @Test
    void adapterUsesPublicPendingCreationStateWithoutCreatingAWorld() throws IOException {
        String source = readSource(Path.of("mixin", "CreateWorldScreenMixin.java"));

        assertTrue(source.contains(".getUiState().getSeed()"));
        assertTrue(source.contains(".getUiState().setSeed(seed)"));
        assertTrue(source.contains(".getUiState().getSettings()"));
        assertTrue(source.contains(".getSettings().options().seed()"));
        assertFalse(source.contains("createNewWorld"));
        assertFalse(source.contains("createLevel"));
        assertFalse(source.contains("@Shadow"));
    }

    @Test
    void workerUsesDetachedGeneratorAndClosingInvalidatesItsPublication() throws IOException {
        String source = readSource(Path.of("RingSeedPreviewScreen.java"));
        int snapshot = source.indexOf("snapshot(owner.ringworld$creationContext())");
        int submit = source.indexOf("WORKER.submit");

        assertTrue(snapshot >= 0 && snapshot < submit,
                "creation context must be snapshotted before worker submission");
        assertTrue(source.contains(
                "ChunkGenerator detached = new NoiseBasedChunkGenerator("));
        assertTrue(source.contains("access.ringworld$setGeometry(geometry)"));
        assertTrue(source.contains("RandomState.create("));
        assertTrue(source.contains("worldgenLoadContext().asGetterLookup()"));
        assertTrue(source.contains("preview.centeredSeamSourceColumn(displayColumn)"));
        assertTrue(source.contains("requests.begin();"));
        assertTrue(source.contains("running.cancel(true)"));
        assertTrue(source.contains("releaseTexture();"));
        assertFalse(source.contains("selected.ringworld$setGeometry"));
        assertFalse(source.contains("createNewWorld"));
        assertFalse(source.contains("createLevel"));
    }

    @Test
    void menuFixtureProvesDistinctSeedsCancellationAndTextureTeardown() throws IOException {
        String fixture = readSource(Path.of("RingWorldCreationUiTestClient.java"));

        assertTrue(fixture.contains("CAPTURE_COUNT = 26"));
        assertTrue(fixture.contains("creation-ui-06-seed-preview-12345-scale4"));
        assertTrue(fixture.contains("creation-ui-07-seed-preview-67890-scale4"));
        assertTrue(fixture.contains("creation-ui-08-rim-default-scale4"));
        assertTrue(fixture.contains("creation-ui-09-rim-overgrown-narrow-scale4"));
        assertTrue(fixture.contains("ringworld$automationCancellationCount()"));
        assertTrue(fixture.contains("ringworld$automationReleased()"));
        assertTrue(fixture.contains("centeredSeam=true"));
        assertTrue(fixture.contains("ringworld$automationHasSkyProfile("));
        assertFalse(fixture.contains("createNewWorld"));
        assertFalse(fixture.contains("createLevel"));
    }

    private static String readSource(Path relative) throws IOException {
        return Files.readString(CLIENT_ROOT.resolve(relative)).replace("\r\n", "\n");
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
