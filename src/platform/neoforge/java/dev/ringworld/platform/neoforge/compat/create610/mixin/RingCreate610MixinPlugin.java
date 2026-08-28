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
import org.objectweb.asm.tree.ClassNode;
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
    private static final Set<String> APPLIED_SERVER_MIXINS = ConcurrentHashMap.newKeySet();
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
        // Phase 3A has no client-only mixins. Future client targets must be
        // preflighted only on Dist.CLIENT before enabling that JSON list.
        if (FMLLoader.getDist() == Dist.CLIENT) preflightTargets(List.of());
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

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return enabled;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        if (!enabled) return;
        APPLIED_SERVER_MIXINS.add(mixinClassName);
        RingWorldMod.LOGGER.info("[create-compat-mixin] applied target={} mixin={}",
                targetClassName, mixinClassName);
    }

    public static boolean exactTupleEnabled() {
        return exactTupleEnabled;
    }

    public static int appliedServerMixinCount() {
        return APPLIED_SERVER_MIXINS.size();
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
