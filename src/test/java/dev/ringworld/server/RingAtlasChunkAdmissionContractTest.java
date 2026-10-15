package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Audits the real pinned Minecraft ABI, not a fake completed future. */
class RingAtlasChunkAdmissionContractTest {
    private record Call(String owner, String name) { }

    private static List<Call> calls(String resource, String method) throws IOException {
        List<Call> calls = new ArrayList<>();
        int[] found = {0};
        try (var bytes = RingAtlasChunkAdmissionContractTest.class.getClassLoader()
                .getResourceAsStream(resource + ".class")) {
            assertNotNull(bytes, resource);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                    if (method != null && !name.equals(method)) return null;
                    found[0]++;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                              String descriptor, boolean isInterface) {
                            calls.add(new Call(owner, name));
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(found[0] > 0, "required ABI method missing: " + method);
        return calls;
    }

    @Test void publicFutureWaitIsBypassedWhileVanillaSchedulingIsRetained() throws IOException {
        String minecraft = "net/minecraft/server/level/ServerChunkCache";
        assertTrue(calls(minecraft, "getChunkFuture").stream().anyMatch(c -> c.name.equals("managedBlock")),
                "re-audit this ABI if the public acquisition contract changes");
        var admission = calls(minecraft, "getChunkFutureMainThread");
        assertTrue(admission.stream().anyMatch(c -> c.name.equals("scheduleChunkGenerationTask")));
        assertFalse(admission.stream().anyMatch(c -> c.name.equals("managedBlock")
                || c.owner.equals("java/util/concurrent/CompletableFuture")
                && (c.name.equals("join") || c.name.equals("get"))));
    }

    @Test void atlasServiceNeverReintroducesPublicBlockingAcquisition() throws IOException {
        var service = calls("dev/ringworld/server/RingAtlasPregenerationService$Job", null);
        assertFalse(service.stream().anyMatch(c -> c.owner.equals("net/minecraft/server/level/ServerChunkCache")
                && c.name.equals("getChunkFuture")));
        assertTrue(service.stream().anyMatch(c -> c.owner.equals("dev/ringworld/mixin/AtlasChunkCacheInvoker")
                && c.name.equals("ringworld$requestChunkWithoutWaiting")));
    }
}
