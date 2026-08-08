package dev.ringworld.platform.neoforge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NeoForgeHeadlessPlayerAdmissionTest {
    private static final String MIXIN_CLASS =
            "dev.ringworld.platform.neoforge.mixin.NeoForgePlayerListMixin";
    private static final String MIXIN_RESOURCE = MIXIN_CLASS.replace('.', '/') + ".class";
    private static final String HELPER_CLASS =
            "dev.ringworld.platform.neoforge.NeoForgeHeadlessPlayerAdmission";
    private static final String INJECT_DESCRIPTOR =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";

    @Test
    void rejectsBeforeTheBufferedPlayLoginOnlyOnNeoForgeHeadlessRuns() throws Exception {
        InputStream platformMixin = getClass().getClassLoader().getResourceAsStream(MIXIN_RESOURCE);
        if (platformMixin == null) {
            // The same source test suite runs against both platform modules.
            // Fabric must not package NeoForge admission code.
            assertFalse(isPresent(HELPER_CLASS));
            return;
        }

        Class<?> helper = Class.forName(HELPER_CLASS);

        Method decision = helper.getDeclaredMethod("rejectIfActive", boolean.class, Runnable.class);
        decision.setAccessible(true);
        AtomicInteger disconnects = new AtomicInteger();
        assertFalse((boolean) decision.invoke(null, false, (Runnable) disconnects::incrementAndGet));
        assertEquals(0, disconnects.get());
        assertTrue((boolean) decision.invoke(null, true, (Runnable) disconnects::incrementAndGet));
        assertEquals(1, disconnects.get());

        InjectionMetadata earlyAt;
        try (InputStream mixinBytes = platformMixin) {
            earlyAt = readInjection(mixinBytes, "ringworld$rejectHeadlessBeforePlayLogin");
        }
        assertNotNull(earlyAt);
        assertTrue(earlyAt.cancellable);
        assertEquals("HEAD", earlyAt.atValue);
        assertNull(earlyAt.shift);
        assertNull(earlyAt.target);

        try (InputStream secondRead = getClass().getClassLoader().getResourceAsStream(MIXIN_RESOURCE)) {
            InjectionMetadata settingsAt = readInjection(
                    secondRead, "ringworld$sendSettingsBeforeInitialWorldPackets");
            assertNotNull(settingsAt);
            assertFalse(settingsAt.cancellable);
            assertEquals("AFTER", settingsAt.shift);
            assertEquals(0, settingsAt.ordinal);
        }
    }

    private static InjectionMetadata readInjection(InputStream classBytes, String methodName)
            throws Exception {
        InjectionMetadata metadata = new InjectionMetadata();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                        if (!annotationDescriptor.equals(INJECT_DESCRIPTOR)) return null;
                        metadata.found = true;
                        return injectVisitor(metadata);
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return metadata.found ? metadata : null;
    }

    private static AnnotationVisitor injectVisitor(InjectionMetadata metadata) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if (name.equals("cancellable")) metadata.cancellable = (boolean) value;
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                if (!name.equals("at")) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                        return atVisitor(metadata);
                    }
                };
            }
        };
    }

    private static AnnotationVisitor atVisitor(InjectionMetadata metadata) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                switch (name) {
                    case "value" -> metadata.atValue = (String) value;
                    case "target" -> metadata.target = (String) value;
                    case "ordinal" -> metadata.ordinal = (int) value;
                    default -> { }
                }
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                if (name.equals("shift")) metadata.shift = value;
            }
        };
    }

    private static final class InjectionMetadata {
        private boolean found;
        private boolean cancellable;
        private String atValue;
        private String target;
        private String shift;
        private int ordinal = -1;
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
