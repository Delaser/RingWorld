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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
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
            "dev.engine_room.flywheel.impl.visualization.storage.Storage",
            "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl");
    private static final List<String> KINETIC_EMBEDDING_DEPENDENCIES = List.of(
            "com.simibubi.create.content.kinetics.base.KineticBlockEntity",
            "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage",
            "dev.engine_room.flywheel.api.visualization.VisualEmbedding");
    private static final Set<String> CLIENT_MIXINS = Set.of(
            "BeltConnectorHandlerMixin",
            "BeltBlockEntityClientMixin",
            "FluidTankBlockEntityClientMixin",
            "ContraptionVisualMixin",
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
