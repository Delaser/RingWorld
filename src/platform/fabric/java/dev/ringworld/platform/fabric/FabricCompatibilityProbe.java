package dev.ringworld.platform.fabric;

import dev.ringworld.RingWorldMod;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric-only discovery adapter for the loader-neutral compatibility inventory. */
public final class FabricCompatibilityProbe {
    private FabricCompatibilityProbe() { }

    public static void logLoadedConflicts() {
        var loadedIds = FabricLoader.getInstance().getAllMods().stream()
                .map(container -> container.getMetadata().getId())
                .toList();
        RingWorldMod.logLoadedConflicts(loadedIds);
    }
}
