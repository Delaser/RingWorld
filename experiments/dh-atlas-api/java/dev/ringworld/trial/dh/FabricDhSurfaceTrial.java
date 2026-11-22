package dev.ringworld.trial.dh;

import net.fabricmc.api.ClientModInitializer;

public final class FabricDhSurfaceTrial implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DhSurfaceTrial.register();
    }
}
