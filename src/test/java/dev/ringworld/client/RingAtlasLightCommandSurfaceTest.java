package dev.ringworld.client;

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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasLightCommandSurfaceTest {
    @Test
    void activeLoaderUsesOnlyItsClientCommandRegistrationApi() throws Exception {
        List<Platform> present = new ArrayList<>();
        for (Platform candidate : List.of(
                new Platform("dev/ringworld/client/RingWorldClient", "registerAtlasLightCommand", true),
                new Platform("dev/ringworld/platform/neoforge/NeoForgeRingWorldClient",
                        "onRegisterClientCommands", false))) {
            if (isPresent(candidate.className())) present.add(candidate);
        }
        assertEquals(1, present.size(), "each loader test graph must expose one client adapter");
        Platform platform = present.getFirst();
        ClassSignals client = read(platform.className());
        MethodSignals registration = client.methodFamily(platform.registrationMethod());
        assertNotNull(registration);
        assertTrue(registration.constants.containsAll(Set.of(
                "ringworld", "ringlights", "show", "reset", "falloff", "peak")));
        assertTrue(registration.calls("dev/ringworld/client/RingAtlasLightCommand", "show"));
        assertTrue(registration.calls("dev/ringworld/client/RingAtlasLightCommand", "reset"));
        assertTrue(registration.calls("dev/ringworld/client/RingAtlasLightCommand", "tune"));
        if (platform.fabric()) {
            assertTrue(registration.calls(
                    "net/fabricmc/fabric/api/client/command/v2/ClientCommandManager", "literal"));
            assertFalse(registration.calls("net/minecraft/commands/Commands", "literal"));
        } else {
            assertTrue(registration.calls("net/minecraft/commands/Commands", "literal"));
            assertTrue(registration.descriptor.contains(
                    "net/neoforged/neoforge/client/event/RegisterClientCommandsEvent"));
        }
        assertFalse(registration.callsOwnerContaining("network"),
                "the client command must not use a payload or command-send path");

        MethodSignals feedback = client.method("applyAtlasLightResult");
        assertNotNull(feedback);
        if (platform.fabric()) {
            assertTrue(feedback.calls(
                    "net/fabricmc/fabric/api/client/command/v2/FabricClientCommandSource",
                    "sendFeedback"));
            assertTrue(feedback.calls(
                    "net/fabricmc/fabric/api/client/command/v2/FabricClientCommandSource",
                    "sendError"));
        } else {
            assertTrue(feedback.calls("net/minecraft/commands/CommandSourceStack", "sendSuccess"));
            assertTrue(feedback.calls("net/minecraft/commands/CommandSourceStack", "sendFailure"));
        }

        ClassSignals server = read("dev/ringworld/server/RingTerrainAtlasServer");
        MethodSignals serverRegistration = server.method("registerCommands");
        assertNotNull(serverRegistration);
        assertFalse(serverRegistration.constants.contains("ringlights"),
                "the process-local command must never be registered on the server");
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
                    MethodSignals method = new MethodSignals(descriptor);
                    result.methods.put(name, method);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String string) method.constants.add(string);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode, String owner, String calledName,
                                String calledDescriptor, boolean isInterface) {
                            method.calls.add(new MethodCall(owner, calledName));
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static InputStream resource(String internalName) {
        return RingAtlasLightCommandSurfaceTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class");
    }

    private static boolean isPresent(String internalName) throws Exception {
        try (InputStream input = resource(internalName)) {
            return input != null;
        }
    }

    private record Platform(String className, String registrationMethod, boolean fabric) { }
    private record MethodCall(String owner, String name) { }

    private static final class ClassSignals {
        private final Map<String, MethodSignals> methods = new HashMap<>();
        private MethodSignals method(String name) { return methods.get(name); }
        private MethodSignals methodFamily(String name) {
            MethodSignals exact = method(name);
            if (exact == null) return null;
            MethodSignals combined = new MethodSignals(exact.descriptor);
            String lambdaPrefix = "lambda$" + name + "$";
            methods.forEach((methodName, method) -> {
                if (methodName.equals(name) || methodName.startsWith(lambdaPrefix)) {
                    combined.constants.addAll(method.constants);
                    combined.calls.addAll(method.calls);
                }
            });
            return combined;
        }
    }

    private static final class MethodSignals {
        private final String descriptor;
        private final Set<String> constants = new HashSet<>();
        private final List<MethodCall> calls = new ArrayList<>();

        private MethodSignals(String descriptor) { this.descriptor = descriptor; }
        private boolean calls(String owner, String name) {
            return calls.contains(new MethodCall(owner, name));
        }
        private boolean callsOwnerContaining(String text) {
            return calls.stream().anyMatch(call -> call.owner().toLowerCase().contains(text));
        }
    }
}
