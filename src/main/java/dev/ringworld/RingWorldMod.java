package dev.ringworld;

import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.platform.fabric.FabricCompatibilityProbe;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingWorldConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint for authoritative ring-world behaviour. */
public final class RingWorldMod implements ModInitializer {
    public static final String MOD_ID = "ringworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FabricCompatibilityProbe.logLoadedConflicts();
        RingWorldConfig.load();
        RingWorldNetworking.registerPayloads();
        RingWorldServer.register();
    }
}
