package dev.ringworld.platform.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FabricHeadlessNetworkingAdmissionTest {
    private static final String NETWORKING_RESOURCE =
            "dev/ringworld/net/RingWorldNetworking.class";
    private static final String HEADLESS_OWNER =
            "dev/ringworld/server/HeadlessPrewarmCoordinator";
    private static final String NETWORKING_OWNER =
            "dev/ringworld/net/RingWorldNetworking";

    @Test
    void headlessJoinReturnsBeforeFabricStartsTheHandshake() throws Exception {
        InputStream networkingBytes = getClass().getClassLoader().getResourceAsStream(NETWORKING_RESOURCE);
        if (networkingBytes == null) {
            // The same source test suite runs against both platform modules.
            // NeoForge must not package the Fabric networking adapter.
            assertNull(getClass().getClassLoader().getResource(NETWORKING_RESOURCE));
            return;
        }

        GuardedJoin guardedJoin;
        try (InputStream classBytes = networkingBytes) {
            guardedJoin = readGuardedJoin(classBytes);
        }
        assertNotNull(guardedJoin, "Fabric JOIN networking must consult headless admission");
        assertTrue(guardedJoin.guardCall < guardedJoin.conditionalJump,
                "headless admission must be evaluated before the branch");
        assertTrue(guardedJoin.conditionalJump < guardedJoin.earlyReturn,
                "the rejected branch must return from the JOIN listener");
        assertTrue(guardedJoin.earlyReturn < guardedJoin.settingsCall,
                "headless JOIN must return before settings and handshake work");
    }

    private static GuardedJoin readGuardedJoin(InputStream classBytes) throws Exception {
        GuardedJoin[] match = new GuardedJoin[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                GuardedJoin candidate = new GuardedJoin();
                return new MethodVisitor(Opcodes.ASM9) {
                    private int instruction;

                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName, String methodDescriptor,
                            boolean isInterface) {
                        if (owner.equals(HEADLESS_OWNER) && methodName.equals("rejectPlayerJoins")) {
                            candidate.guardCall = instruction;
                        }
                        if (owner.equals(NETWORKING_OWNER) && methodName.equals("sendSettings")) {
                            candidate.settingsCall = instruction;
                        }
                        instruction++;
                    }

                    @Override
                    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                        if ((opcode == Opcodes.IFEQ || opcode == Opcodes.IFNE)
                                && candidate.guardCall >= 0 && candidate.conditionalJump < 0) {
                            candidate.conditionalJump = instruction;
                        }
                        instruction++;
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN && candidate.conditionalJump >= 0
                                && candidate.earlyReturn < 0) {
                            candidate.earlyReturn = instruction;
                        }
                        instruction++;
                    }

                    @Override
                    public void visitEnd() {
                        if (candidate.complete()) match[0] = candidate;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return match[0];
    }

    private static final class GuardedJoin {
        private int guardCall = -1;
        private int conditionalJump = -1;
        private int earlyReturn = -1;
        private int settingsCall = -1;

        private boolean complete() {
            return guardCall >= 0 && conditionalJump >= 0 && earlyReturn >= 0 && settingsCall >= 0;
        }
    }
}
