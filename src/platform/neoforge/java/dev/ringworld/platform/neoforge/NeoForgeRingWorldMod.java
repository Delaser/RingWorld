package dev.ringworld.platform.neoforge;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingWorldConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge bootstrap adapter for the shared RingWorld implementation. */
@Mod(RingWorldMod.MOD_ID)
public final class NeoForgeRingWorldMod {
    public NeoForgeRingWorldMod(IEventBus modEventBus, ModContainer modContainer) {
        RingWorldConfig.configureDirectory(FMLPaths.CONFIGDIR.get());
        logLoadedConflicts();
        RingWorldConfig.load();
        NeoForgeTerrainAtlasPlatform.configure();
        modEventBus.addListener(NeoForgeRingWorldNetworking::registerPayloads);
        if (FMLEnvironment.getDist().isClient()) {
            NeoForgeRingWorldClient.register(modEventBus);
        }
        NeoForge.EVENT_BUS.register(new NeoForgeRingWorldServer());
        RingWorldMod.LOGGER.info("RingWorld NeoForge platform bootstrap active");
    }

    private static void logLoadedConflicts() {
        var loadedIds = ModList.get().getMods().stream()
                .map(info -> info.getModId())
                .toList();
        for (var conflict : dev.ringworld.api.RingCompatibilityContract.findLoadedConflicts(loadedIds)) {
            RingWorldMod.LOGGER.error(
                    "Unsupported RingWorld combination detected: {} ({}): {}. "
                            + "Remove it before reporting RingWorld rendering or topology defects.",
                    conflict.displayName(), conflict.modId(), conflict.reason());
        }
    }
}
