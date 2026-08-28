package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.RingWorldMod;
import dev.ringworld.platform.neoforge.compat.create610.mixin.RingCreate610MixinPlugin;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Self-halting dedicated-server proof for absent and exact Create tuples. */
public final class RingCreate610BootFixture {
    private static final String PROPERTY = "ringworld.createCompatBoot";
    private static int readyTicks;
    private static boolean complete;

    private RingCreate610BootFixture() { }

    public static void tick(MinecraftServer server) {
        if (complete) return;
        String mode = System.getProperty(PROPERTY);
        if (mode == null || server.overworld() == null || ++readyTicks < 20) return;

        try {
            switch (mode) {
                case "absent" -> verifyAbsent();
                case "exact" -> verifyExact(server);
                default -> throw new IllegalStateException("unknown create compatibility boot mode " + mode);
            }
            complete = true;
            RingWorldMod.LOGGER.info(
                    "[create-compat-boot] PASS mode={} appliedServerMixins={} appliedClientMixins={}",
                    mode, RingCreate610MixinPlugin.appliedServerMixinCount(),
                    RingCreate610MixinPlugin.appliedClientMixinCount());
        } catch (Throwable failure) {
            complete = true;
            RingWorldMod.LOGGER.error("[create-compat-boot] FAIL mode={}", mode, failure);
        } finally {
            server.halt(false);
        }
    }

    private static void verifyAbsent() {
        if (ModList.get().isLoaded("create") || ModList.get().isLoaded("flywheel")) {
            throw new IllegalStateException("ordinary compatibility boot unexpectedly loaded Create/Flywheel");
        }
        if (RingCreate610MixinPlugin.exactTupleEnabled()
                || RingCreate610MixinPlugin.appliedServerMixinCount() != 0
                || RingCreate610MixinPlugin.appliedClientMixinCount() != 0) {
            throw new IllegalStateException("optional Create mixins applied without Create");
        }
    }

    private static void verifyExact(MinecraftServer server) {
        requireVersion("create", "6.0.10");
        requireVersion("flywheel", "1.0.6");
        if (!RingCreate610MixinPlugin.exactTupleEnabled()) {
            throw new IllegalStateException("exact Create tuple did not enable its strict mixin config");
        }
        RingCreate610ServerFixture.verify(server.overworld());
        if (RingCreate610MixinPlugin.appliedServerMixinCount() != 4) {
            throw new IllegalStateException("expected four strict server mixins, observed "
                    + RingCreate610MixinPlugin.appliedServerMixinCount());
        }
        if (RingCreate610MixinPlugin.appliedClientMixinCount() != 0) {
            throw new IllegalStateException("dedicated server applied client compatibility mixins");
        }
    }

    private static void requireVersion(String modId, String expected) {
        Optional<String> actual = ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
        if (!actual.orElse("absent").equals(expected)) {
            throw new IllegalStateException(modId + " version must be " + expected
                    + ", observed " + actual.orElse("absent"));
        }
    }
}
