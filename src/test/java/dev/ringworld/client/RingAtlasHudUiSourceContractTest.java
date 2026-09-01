package dev.ringworld.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact 1.21.1 GUI descriptor and source-policy guard for compact Atlas UI. */
class RingAtlasHudUiSourceContractTest {
    private static final String GUI_RENDER_DESCRIPTOR =
            "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V";
    private static final Path ROOT = findProjectRoot();

    @Test
    void mappedMinecraftTargetHasReviewedGuiRenderDescriptor() throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "net/minecraft/client/gui/Gui.class")) {
            if (input == null) throw new AssertionError("mapped Gui bytecode unavailable");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals("render") && descriptor.equals(GUI_RENDER_DESCRIPTOR)) {
                        found.set(true);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(found.get(), "official 1.21.1 Gui.render descriptor changed");
    }

    @Test
    void guiMixinOnlyDelegatesAndPreviewRowsRemainOnMapScreen() throws IOException {
        String mixin = read("src/client/java/dev/ringworld/client/mixin/GuiMixin.java");
        String renderer = read("src/client/java/dev/ringworld/client/RingAtlasHudRenderer.java");
        String map = read("src/client/java/dev/ringworld/client/RingWorldMapScreen.java");

        assertTrue(mixin.contains("method = \"render(Lnet/minecraft/client/gui/GuiGraphics;\""));
        assertTrue(mixin.contains("RingAtlasHudRenderer.render(graphics, minecraft)"));
        assertFalse(mixin.contains("RingAtlasHudProgress"));
        assertFalse(mixin.contains("graphics.fill"));

        assertTrue(renderer.contains("SCALE = 0.5F"));
        assertTrue(renderer.contains("RingAtlasHudProgress.label("));
        assertTrue(renderer.contains("graphics.pose().pushPose()"));
        assertTrue(renderer.contains("graphics.pose().scale(SCALE, SCALE, 1.0F)"));
        assertTrue(renderer.contains("graphics.pose().popPose()"));
        assertFalse(renderer.contains("RingTerrainPreviewHud"));

        assertTrue(map.contains("RingTerrainPreviewHud.entries("));
        assertTrue(map.contains("RingTerrainPreviewHud.grid(width)"));
        assertTrue(map.contains("fitPreviewLabel(entry.label(), previewGrid.columnWidth())"));
        assertTrue(map.contains("43 + i * 12"),
                "eight status rows must end above the preview heading at y=143");
        assertFalse(map.contains("43 + i * 15"));
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
