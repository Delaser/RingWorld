package dev.ringworld.client.render;

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

/** Exact 1.21.1 descriptor and source guard for sky-profile fog matching. */
class RingFogRenderingSourceContractTest {
    private static final String SETUP_COLOR_DESCRIPTOR =
            "(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V";
    private static final Path ROOT = findProjectRoot();

    @Test
    void mappedMinecraftTargetHasReviewedSetupColorDescriptor() throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "net/minecraft/client/renderer/FogRenderer.class")) {
            if (input == null) throw new AssertionError("mapped FogRenderer bytecode unavailable");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals("setupColor") && descriptor.equals(SETUP_COLOR_DESCRIPTOR)) {
                        found.set(true);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(found.get(), "official 1.21.1 setupColor descriptor changed");
    }

    @Test
    void fogHookIsOrdinaryAirColourOnlyAndProxyEdgesUseBackdrop() throws IOException {
        String mixin = read("src/client/java/dev/ringworld/client/mixin/FogRendererMixin.java");
        String mixins = read("src/main/resources/ringworld.client.mixins.json");
        String renderer = read(
                "src/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java");
        String shader = read("src/client/resources/assets/ringworld/shaders/core/ring_surface.fsh");

        assertTrue(mixin.contains("method = \"setupColor(Lnet/minecraft/client/Camera;"));
        assertTrue(mixin.contains("at = @At(\"TAIL\")"));
        assertTrue(mixin.contains("level.dimension() != Level.OVERWORLD"));
        assertTrue(mixin.contains("camera.getFluidInCamera() != FogType.NONE"));
        assertTrue(mixin.contains("MobEffects.BLINDNESS"));
        assertTrue(mixin.contains("MobEffects.DARKNESS"));
        assertTrue(mixin.contains("level.effects().isFoggyAt("));
        assertTrue(mixin.contains("shouldCreateWorldFog()"));
        assertTrue(mixin.contains("RingSkyCycle.exposedHorizonBlend("));
        assertFalse(mixin.contains("setShaderFogStart"));
        assertFalse(mixin.contains("setShaderFogEnd"));
        assertFalse(mixin.contains("setShaderFogShape"));
        assertTrue(mixins.contains("\"FogRendererMixin\""));
        assertTrue(renderer.contains("ClientRingState.skyProfile().backdrop().id()"));
        assertTrue(shader.contains("vec3 edgeColor = FogColor.rgb"));
        assertTrue(shader.contains("ColorModulator.x > 1.5"));
        assertTrue(shader.contains("5.0 / 255.0, 8.0 / 255.0, 16.0 / 255.0"));
        assertTrue(shader.contains("1.0 / 255.0, 1.0 / 255.0, 3.0 / 255.0"));
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
