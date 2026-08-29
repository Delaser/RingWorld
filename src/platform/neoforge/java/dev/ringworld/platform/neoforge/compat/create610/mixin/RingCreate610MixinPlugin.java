package dev.ringworld.platform.neoforge.compat.create610.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.platform.neoforge.compat.create610.Create610CompatibilityDecision;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Enables the strict adapter only for the one qualified NeoForge/Create ABI. */
public final class RingCreate610MixinPlugin implements IMixinConfigPlugin {
    private static final List<String> SERVER_TARGETS = List.of(
            "com.simibubi.create.content.kinetics.belt.BeltBlockEntity",
            "com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem",
            "com.simibubi.create.api.connectivity.ConnectivityHandler",
            "com.simibubi.create.content.fluids.tank.FluidTankBlockEntity");
    private static final List<String> CLIENT_TARGETS = List.of(
            "com.simibubi.create.content.kinetics.belt.item.BeltConnectorHandler",
            "com.simibubi.create.content.kinetics.belt.BeltBlockEntity",
            "com.simibubi.create.content.fluids.tank.FluidTankBlockEntity",
            "com.simibubi.create.content.contraptions.render.ContraptionVisual",
            "com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity",
            "com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer",
            "dev.engine_room.flywheel.impl.visualization.storage.Storage",
            "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl");
    private static final List<String> KINETIC_EMBEDDING_DEPENDENCIES = List.of(
            "com.simibubi.create.content.kinetics.base.KineticBlockEntity",
            "com.simibubi.create.foundation.render.RenderTypes",
            "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage",
            "dev.engine_room.flywheel.api.visualization.VisualEmbedding");
    private static final Set<String> CLIENT_MIXINS = Set.of(
            "BeltConnectorHandlerMixin",
            "BeltBlockEntityClientMixin",
            "FluidTankBlockEntityClientMixin",
            "ContraptionVisualMixin",
            "GantryContraptionEntityMixin",
            "ContraptionEntityRendererMixin",
            "StorageKineticEmbeddingMixin",
            "VisualizationManagerKineticEmbeddingMixin");
    private static final Set<String> APPLIED_SERVER_MIXINS = ConcurrentHashMap.newKeySet();
    private static final Set<String> APPLIED_CLIENT_MIXINS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean UNQUALIFIED_WARNING_EMITTED = new AtomicBoolean();
    private static volatile boolean exactTupleEnabled;

    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        String createVersion = modVersion("create");
        String flywheelVersion = modVersion("flywheel");
        var versionInfo = FMLLoader.versionInfo();
        Create610CompatibilityDecision.Result decision = Create610CompatibilityDecision.evaluate(
                versionInfo.mcVersion(), versionInfo.neoForgeVersion(), createVersion, flywheelVersion);
        if (decision.state() == Create610CompatibilityDecision.State.CREATE_ABSENT) return;
        if (!decision.enabled() && UNQUALIFIED_WARNING_EMITTED.compareAndSet(false, true)) {
            RingWorldMod.LOGGER.warn(
                    "Create compatibility adapter disabled for unqualified tuple {}; exact support requires "
                            + "Minecraft 1.21.1, NeoForge 21.1.239, Create 6.0.10, Flywheel 1.0.6",
                    decision.observedTuple());
        }
        if (!decision.enabled()) {
            return;
        }

        preflightTargets(SERVER_TARGETS);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            preflightTargets(CLIENT_TARGETS);
            preflightTargets(KINETIC_EMBEDDING_DEPENDENCIES);
            preflightKineticEmbeddingAbi();
            preflightGantryAbi();
            preflightContraptionOffRenderAbi();
        }
        enabled = true;
        exactTupleEnabled = true;
        RingWorldMod.LOGGER.info(
                "Create compatibility adapter enabled for Minecraft 1.21.1 / NeoForge 21.1.239 / "
                        + "Create 6.0.10 / Flywheel 1.0.6");
    }

    private static String modVersion(String modId) {
        return FMLLoader.getLoadingModList().getMods().stream()
                .filter(mod -> modId.equals(mod.getModId()))
                .map(ModInfo::getVersion)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }

    private static void preflightTargets(List<String> targets) {
        for (String target : targets) {
            try {
                // NeoForge's ModLauncher provider supports only its transformed
                // resource path at this phase. This reads a ClassNode without
                // loading or initializing the optional Create target.
                ClassNode node = MixinService.getService().getBytecodeProvider()
                        .getClassNode(target);
                if (node == null) throw new ClassNotFoundException(target);
            } catch (ClassNotFoundException | IOException exception) {
                throw new IllegalStateException(
                        "Qualified Create compatibility target is missing: " + target, exception);
            }
        }
    }

    private static void preflightKineticEmbeddingAbi() {
        ClassNode storage = classNode(
                "dev.engine_room.flywheel.impl.visualization.storage.Storage");
        requireField(storage, "visuals", "Ljava/util/Map;");
        requireMethod(storage, "add",
                "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                        + "Ljava/lang/Object;F)V");
        requireMethod(storage, "remove", "(Ljava/lang/Object;)V");
        requireMethod(storage, "invalidate", "()V");
        requireMethod(storage, "lambda$recreateAll$4",
                "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;F"
                        + "Ljava/lang/Object;Ldev/engine_room/flywheel/api/visual/Visual;)"
                        + "Ldev/engine_room/flywheel/api/visual/Visual;");

        ClassNode manager = classNode(
                "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl");
        requireField(manager, "level", "Lnet/minecraft/world/level/LevelAccessor;");
        requireField(manager, "blockEntities",
                "Ldev/engine_room/flywheel/impl/visualization/VisualManagerImpl;");
        requireMethod(manager, "render",
                "(Ldev/engine_room/flywheel/api/backend/RenderContext;)V");
        requireInvocation(manager, "render",
                "(Ldev/engine_room/flywheel/api/backend/RenderContext;)V",
                Opcodes.INVOKEINTERFACE,
                "dev/engine_room/flywheel/api/backend/Engine", "render",
                "(Ldev/engine_room/flywheel/api/backend/RenderContext;)V");
    }

    private static void preflightGantryAbi() {
        String owner = "com/simibubi/create/content/contraptions/gantry/"
                + "GantryContraptionEntity";
        String packet = "Lcom/simibubi/create/content/contraptions/gantry/"
                + "GantryContraptionUpdatePacket;";
        ClassNode gantry = classNode(owner.replace('/', '.'));
        requireField(gantry, "movementAxis", "Lnet/minecraft/core/Direction;");
        requireField(gantry, "clientOffsetDiff", "D");
        MethodNode handler = requireMethod(gantry, "handlePacket", "(" + packet + ")V");
        AbstractInsnNode coord = requireSoleInvocation(handler, Opcodes.INVOKEVIRTUAL,
                "com/simibubi/create/content/contraptions/gantry/GantryContraptionUpdatePacket",
                "coord", "()D");
        AbstractInsnNode axis = requireSoleInvocation(handler, Opcodes.INVOKEVIRTUAL,
                owner, "getAxisCoord", "()D");
        AbstractInsnNode subtract = requireSoleOpcode(handler, Opcodes.DSUB);
        AbstractInsnNode write = requireSoleFieldAccess(handler, Opcodes.PUTFIELD,
                owner, "clientOffsetDiff", "D");
        requireOrderedShape(gantry.name, "handler coord-axis-subtract-write",
                coord, axis, subtract, write);

        MethodNode tick = requireMethod(gantry, "tickContraption", "()V");
        AbstractInsnNode read = requireSoleFieldAccess(tick, Opcodes.GETFIELD,
                owner, "clientOffsetDiff", "D");
        AbstractInsnNode constant = nextExecutable(read);
        AbstractInsnNode multiply = nextExecutable(constant);
        AbstractInsnNode decayWrite = nextExecutable(multiply);
        if (!(constant instanceof LdcInsnNode ldc)
                || !(ldc.cst instanceof Double value)
                || Double.compare(value, 0.75D) != 0
                || multiply == null || multiply.getOpcode() != Opcodes.DMUL
                || !(decayWrite instanceof FieldInsnNode field)
                || decayWrite.getOpcode() != Opcodes.PUTFIELD
                || !field.owner.equals(owner) || !field.name.equals("clientOffsetDiff")
                || !field.desc.equals("D")) {
            throw abiDrift(gantry.name, "instruction shape",
                    "clientOffsetDiff GETFIELD; 0.75D; DMUL; clientOffsetDiff PUTFIELD", 0);
        }
        requireSoleFieldAccess(tick, Opcodes.PUTFIELD,
                owner, "clientOffsetDiff", "D");
    }

    private static void preflightContraptionOffRenderAbi() {
        String rendererOwner = "com/simibubi/create/content/contraptions/render/"
                + "ContraptionEntityRenderer";
        String renderDescriptor = "(Lcom/simibubi/create/content/contraptions/"
                + "AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
        MethodNode render = requireMethod(
                classNode(rendererOwner.replace('/', '.')), "render", renderDescriptor);
        AbstractInsnNode supports = requireSoleInvocation(render, Opcodes.INVOKESTATIC,
                "dev/engine_room/flywheel/api/visualization/VisualizationManager",
                "supportsVisualization", "(Lnet/minecraft/world/level/LevelAccessor;)Z");
        AbstractInsnNode branch = nextExecutable(supports);
        AbstractInsnNode layers = requireSoleInvocation(render, Opcodes.INVOKESTATIC,
                "net/minecraft/client/renderer/RenderType", "chunkBufferLayers",
                "()Ljava/util/List;");
        AbstractInsnNode sink = requireSoleInvocation(render, Opcodes.INVOKEINTERFACE,
                "net/minecraft/client/renderer/MultiBufferSource", "getBuffer",
                "(Lnet/minecraft/client/renderer/RenderType;)"
                        + "Lcom/mojang/blaze3d/vertex/VertexConsumer;");
        AbstractInsnNode renderInto = requireSoleInvocation(render, Opcodes.INVOKEINTERFACE,
                "net/createmod/catnip/render/SuperByteBuffer", "renderInto",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                        + "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V");
        if (!(branch instanceof JumpInsnNode jump) || branch.getOpcode() != Opcodes.IFNE
                || !appearsBefore(renderInto, jump.label)) {
            throw abiDrift(rendererOwner, "instruction shape",
                    "supportsVisualization IFNE over OFF layer sink", 0);
        }
        requireOrderedShape(rendererOwner, "OFF supports-layers-sink-renderInto",
                supports, layers, sink, renderInto);

        ClassNode renderTypes = classNode(
                "com.simibubi.create.foundation.render.RenderTypes");
        String factoryDescriptor = "()Lnet/minecraft/client/renderer/RenderType;";
        requireMethod(renderTypes, "entitySolidBlockMipped", factoryDescriptor);
        requireMethod(renderTypes, "entityCutoutBlockMipped", factoryDescriptor);
    }

    private static ClassNode classNode(String target) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target);
            if (node == null) throw new ClassNotFoundException(target);
            return node;
        } catch (ClassNotFoundException | IOException exception) {
            throw new IllegalStateException(
                    "Qualified Create compatibility target is missing: " + target, exception);
        }
    }

    private static void requireField(ClassNode owner, String name, String descriptor) {
        long count = owner.fields.stream()
                .filter(field -> field.name.equals(name) && field.desc.equals(descriptor))
                .count();
        if (count != 1) {
            throw abiDrift(owner.name, "field", name + descriptor, count);
        }
    }

    private static MethodNode requireMethod(
            ClassNode owner, String name, String descriptor) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .toList();
        if (matches.size() != 1) {
            throw abiDrift(owner.name, "method", name + descriptor, matches.size());
        }
        return matches.getFirst();
    }

    private static void requireInvocation(
            ClassNode owner, String methodName, String methodDescriptor,
            int opcode, String targetOwner, String targetName, String targetDescriptor) {
        MethodNode method = requireMethod(owner, methodName, methodDescriptor);
        long count = 0;
        for (var instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == opcode
                    && invocation.owner.equals(targetOwner)
                    && invocation.name.equals(targetName)
                    && invocation.desc.equals(targetDescriptor)) {
                count++;
            }
        }
        if (count != 1) {
            throw abiDrift(owner.name, "invocation",
                    targetOwner + "." + targetName + targetDescriptor, count);
        }
    }

    private static AbstractInsnNode requireSoleInvocation(
            MethodNode method, int opcode, String owner, String name, String descriptor) {
        AbstractInsnNode match = null;
        long count = 0;
        for (var instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == opcode
                    && invocation.owner.equals(owner)
                    && invocation.name.equals(name)
                    && invocation.desc.equals(descriptor)) {
                match = instruction;
                count++;
            }
        }
        if (count != 1) {
            throw abiDrift(owner, "invocation", name + descriptor, count);
        }
        return match;
    }

    private static AbstractInsnNode requireSoleFieldAccess(
            MethodNode method, int opcode, String owner, String name, String descriptor) {
        AbstractInsnNode match = null;
        long count = 0;
        for (var instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == opcode
                    && field.owner.equals(owner)
                    && field.name.equals(name)
                    && field.desc.equals(descriptor)) {
                match = instruction;
                count++;
            }
        }
        if (count != 1) {
            throw abiDrift(owner, "field access", name + descriptor, count);
        }
        return match;
    }

    private static AbstractInsnNode requireSoleOpcode(MethodNode method, int opcode) {
        AbstractInsnNode match = null;
        long count = 0;
        for (var instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() == opcode) {
                match = instruction;
                count++;
            }
        }
        if (count != 1) {
            throw abiDrift(method.name, "opcode", Integer.toString(opcode), count);
        }
        return match;
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static void requireOrderedShape(
            String owner, String name, AbstractInsnNode... instructions) {
        for (int index = 1; index < instructions.length; index++) {
            if (instructions[index - 1] == null || instructions[index] == null
                    || !appearsBefore(instructions[index - 1], instructions[index])) {
                throw abiDrift(owner, "instruction shape", name, 0);
            }
        }
    }

    private static boolean appearsBefore(AbstractInsnNode first, AbstractInsnNode second) {
        for (AbstractInsnNode cursor = first.getNext(); cursor != null; cursor = cursor.getNext()) {
            if (cursor == second) return true;
        }
        return false;
    }

    private static IllegalStateException abiDrift(
            String owner, String kind, String member, long count) {
        return new IllegalStateException(
                "Qualified Create/Flywheel compatibility ABI drift: " + owner + " "
                        + kind + " " + member + " expected exactly once, found " + count);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return enabled;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        if (!enabled) return;
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (CLIENT_MIXINS.contains(simpleName)) {
            APPLIED_CLIENT_MIXINS.add(mixinClassName);
        } else {
            APPLIED_SERVER_MIXINS.add(mixinClassName);
        }
        RingWorldMod.LOGGER.info("[create-compat-mixin] applied target={} mixin={}",
                targetClassName, mixinClassName);
    }

    public static boolean exactTupleEnabled() {
        return exactTupleEnabled;
    }

    public static int appliedServerMixinCount() {
        return APPLIED_SERVER_MIXINS.size();
    }

    public static int appliedClientMixinCount() {
        return APPLIED_CLIENT_MIXINS.size();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) { }
}
