package dev.ringworld.platform.fabric;

import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.server.FabricRingWorldServer;
import dev.ringworld.world.RingWorldConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric bootstrap adapter; shared RingWorld code contains no loader calls. */
public final class FabricRingWorldMod implements ModInitializer {
    @Override
    public void onInitialize() {
        RingWorldConfig.configureDirectory(FabricLoader.getInstance().getConfigDir());
        FabricCompatibilityProbe.logLoadedConflicts();
        RingWorldConfig.load();
        RingWorldNetworking.registerPayloads();
        FabricRingWorldServer.register();
    }
}
