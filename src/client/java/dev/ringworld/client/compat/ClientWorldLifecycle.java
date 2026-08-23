package dev.ringworld.client.compat;

import dev.ringworld.RingWorldMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Adapts mainline's reason-bearing local-world disconnect to 1.21.1. */
public final class ClientWorldLifecycle {
    private ClientWorldLifecycle() { }

    public static void disconnect(Minecraft client, Component reason) {
        RingWorldMod.LOGGER.info("Disconnecting test client: {}", reason.getString());
        if (client.level != null) client.level.disconnect();
        client.disconnect(new TitleScreen());
    }
}
