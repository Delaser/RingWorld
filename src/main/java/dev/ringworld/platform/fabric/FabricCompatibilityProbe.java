package dev.ringworld.platform.fabric;

import dev.ringworld.RingWorldMod;
import dev.ringworld.api.RingCompatibilityContract;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric-only discovery adapter for the loader-neutral compatibility inventory. */
public final class FabricCompatibilityProbe {
    private FabricCompatibilityProbe() { }

    public static void logLoadedConflicts() {
        var loadedIds = FabricLoader.getInstance().getAllMods().stream()
                .map(container -> container.getMetadata().getId())
                .toList();
        for (RingCompatibilityContract.Conflict conflict
                : RingCompatibilityContract.findLoadedConflicts(loadedIds)) {
            RingWorldMod.LOGGER.error(
                    "Unsupported RingWorld combination detected: {} ({}): {}. "
                            + "Remove it before reporting RingWorld rendering or topology defects.",
                    conflict.displayName(), conflict.modId(), conflict.reason());
        }
    }
}
