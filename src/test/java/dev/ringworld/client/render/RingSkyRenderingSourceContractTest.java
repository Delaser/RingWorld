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

/** Exact 1.21.1 descriptor and source-policy guard for the profile sky hook. */
class RingSkyRenderingSourceContractTest {
    private static final String RENDER_SKY_DESCRIPTOR =
            "(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;"
                    + "ZLjava/lang/Runnable;)V";
    private static final Path ROOT = findProjectRoot();

    @Test
    void mappedMinecraftTargetHasReviewedRenderSkyDescriptor() throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "net/minecraft/client/renderer/LevelRenderer.class")) {
            if (input == null) throw new AssertionError("mapped LevelRenderer bytecode unavailable");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals("renderSky") && descriptor.equals(RENDER_SKY_DESCRIPTOR)) {
                        found.set(true);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(found.get(), "official 1.21.1 renderSky descriptor changed");
    }

    @Test
    void skyHookKeepsProfilePolicyAndRingBeforeSelectedSun() throws IOException {
        String source = Files.readString(ROOT.resolve(
                "src/client/java/dev/ringworld/client/mixin/SkyRenderingMixin.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("method = \"renderSky(Lorg/joml/Matrix4f;"));
        assertTrue(source.contains("RingGeometry geometry = ClientRingState.geometry()"));
        assertTrue(source.contains("RingSkyCycle.fixedBackdropRgb(profile)"));
        assertTrue(source.contains("RingSkyCycle.starBrightness("));
        assertTrue(source.contains("RingSkyCycle.starFieldAngleRadians("));
        assertTrue(source.contains("RingSkyCycle.FIXED_SUN_ANGLE_RADIANS"));
        assertTrue(source.contains("profile.lightSource().halfWidth()"));
        assertTrue(source.contains("RingSkyCycle.sunAlphaScale(profile)"));
        assertTrue(source.contains("profile.lightSource() == RingSkyProfile.LightSource.NONE"));
        assertFalse(source.contains("MOON_LOCATION"));

        int upper = source.indexOf("skyBuffer.drawWithShader(");
        int lower = source.indexOf("darkBuffer.drawWithShader(");
        int ring = source.indexOf("RingSurfaceTextureRenderer.render(poseStack");
        int selectedSun = source.indexOf("RenderSystem.setShaderTexture(0, SUN_LOCATION)");
        assertTrue(upper >= 0 && lower > upper, "matching lower hemisphere must follow upper sky");
        assertTrue(ring >= 0 && selectedSun > ring, "ring proxy must precede selected sun");
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
