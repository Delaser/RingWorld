package dev.ringworld.net;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingProtocolTransportSurfaceTest {
    private static final String SETTINGS = "dev/ringworld/net/RingSettingsPayload";
    private static final String SKY = "dev/ringworld/net/RingSkyProfilePayload";
    private static final String PREVIEW = "dev/ringworld/net/RingTerrainPreviewPayload";
    private static final Set<String> REQUIRED = Set.of(SETTINGS, SKY, PREVIEW);

    @Test
    void activeLoaderRegistersRequiresAndRoutesAllReviewedChannels() throws Exception {
        List<Platform> present = new ArrayList<>();
        for (Platform candidate : List.of(
                new Platform("fabric", "dev/ringworld/net/RingWorldNetworking",
                        "dev/ringworld/client/RingWorldClient", true),
                new Platform("neoforge",
                        "dev/ringworld/platform/neoforge/NeoForgeRingWorldNetworking",
                        "dev/ringworld/platform/neoforge/NeoForgeRingWorldClient", false))) {
            if (isPresent(candidate.serverClass())) present.add(candidate);
        }
        assertEquals(1, present.size(), "each loader test graph must expose exactly its own transport");
        Platform platform = present.getFirst();

        ClassSignals server = read(platform.serverClass());
        MethodSignals registrations = server.method("registerPayloads");
        assertNotNull(registrations);
        assertTrue(registrations.idOwners.containsAll(REQUIRED),
                platform.name() + " must register settings, sky, and preview clientbound IDs");
        MethodSignals earlySend = server.method("sendSettings");
        assertNotNull(earlySend);
        assertTrue(earlySend.calls("dev/ringworld/net/RingProtocolCapabilities",
                "supportsRequiredClientbound"));
        assertTrue(earlySend.calls("dev/ringworld/world/RingSkySettings", "get"));
        assertTrue(earlySend.calls("dev/ringworld/net/RingSettingsHandshake", "payloadFor"));

        ClassSignals client = read(platform.clientClass());
        if (platform.fabric()) {
            MethodSignals bootstrap = client.method("onInitializeClient");
            assertNotNull(bootstrap);
            assertTrue(bootstrap.idOwners.containsAll(REQUIRED));
            assertDirectFabricHandler(client, "hasMatchingPayloadFingerprint");
            assertDirectFabricHandler(client, "setSkyProfile");
            assertDirectFabricHandler(client, "installTerrainPreview");
        } else {
            MethodSignals router = client.method("handleClientPayload");
            assertNotNull(router);
            assertTrue(router.typeChecks.containsAll(REQUIRED));
            assertEnqueuedNeoForgeHandler(client, "handleSettings");
            assertEnqueuedNeoForgeHandler(client, "handleSkyProfile");
            assertEnqueuedNeoForgeHandler(client, "handleTerrainPreview");
        }
    }

    private static void assertDirectFabricHandler(ClassSignals client, String markerCall) {
        MethodSignals handler = client.methods.values().stream()
                .filter(method -> method.calls.stream().anyMatch(call -> call.name().equals(markerCall)))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "missing Fabric handler marker " + markerCall));
        assertFalse(handler.calls("net/minecraft/client/Minecraft", "execute"),
                "Fabric play payload handlers already run on the render thread; do not requeue "
                        + markerCall);
    }

    private static void assertEnqueuedNeoForgeHandler(ClassSignals client, String methodName) {
        MethodSignals handler = client.method(methodName);
        assertNotNull(handler);
        assertTrue(handler.calls(
                "net/neoforged/neoforge/network/handling/IPayloadContext", "enqueueWork"));
    }

    private static ClassSignals read(String internalName) throws Exception {
        try (InputStream input = resource(internalName)) {
            if (input == null) throw new AssertionError("missing class " + internalName);
            ClassSignals result = new ClassSignals();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    MethodSignals method = new MethodSignals();
                    result.methods.put(name, method);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(
                                int opcode, String owner, String fieldName, String fieldDescriptor) {
                            if (opcode == Opcodes.GETSTATIC && fieldName.equals("ID")) {
                                method.idOwners.add(owner);
                            }
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.INSTANCEOF) method.typeChecks.add(type);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode, String owner, String calledName,
                                String calledDescriptor, boolean isInterface) {
                            method.calls.add(new MethodCall(owner, calledName));
                        }
                    };
                }

                @Override
                public FieldVisitor visitField(
                        int access, String name, String descriptor,
                        String signature, Object value) {
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static InputStream resource(String internalName) {
        return RingProtocolTransportSurfaceTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class");
    }

    private static boolean isPresent(String internalName) throws Exception {
        try (InputStream input = resource(internalName)) {
            return input != null;
        }
    }

    private record Platform(String name, String serverClass, String clientClass, boolean fabric) { }
    private record MethodCall(String owner, String name) { }

    private static final class ClassSignals {
        private final Map<String, MethodSignals> methods = new HashMap<>();
        private MethodSignals method(String name) { return methods.get(name); }
    }

    private static final class MethodSignals {
        private final Set<String> idOwners = new HashSet<>();
        private final Set<String> typeChecks = new HashSet<>();
        private final List<MethodCall> calls = new ArrayList<>();
        private boolean calls(String owner, String name) {
            return calls.contains(new MethodCall(owner, name));
        }
    }
}
