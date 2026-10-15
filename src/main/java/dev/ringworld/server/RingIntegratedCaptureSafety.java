package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Local, opt-in fixture setup; never changes ordinary or dedicated-server players. */
public final class RingIntegratedCaptureSafety {
    private RingIntegratedCaptureSafety() { }

    /** Runs at login, before a copied Survival world's first capture/Atlas wait. */
    public static void onPlayerJoined(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        if (server.isDedicatedServer() || !server.isSingleplayerOwner(player.getGameProfile())) return;
        if (!(Boolean.getBoolean("ringworld.captureRingProjection")
                || Boolean.getBoolean("ringworld.captureRingVisualParity")
                || Boolean.getBoolean("ringworld.captureRingHandoffFoliage")
                || Boolean.getBoolean("ringworld.captureFloatingAtlas")
                || Boolean.getBoolean("ringworld.captureUnderwater")
                || Boolean.getBoolean("ringworld.captureHorizon")
                || Boolean.getBoolean("ringworld.captureAtlasWalls")
                || Boolean.getBoolean("ringworld.cacheIdentityTest")
                || Boolean.getBoolean("ringworld.layoutSwitchTest")
                || Boolean.getBoolean("ringworld.productionLifecycleTest"))) return;
        prepareCreative(player);
        RingWorldMod.LOGGER.info("[capture-player] initial Creative flight ready player={} alive={}",
                player.getName().getString(), player.isAlive());
    }

    /** Server-thread only. A dead test player must fail; never heal or respawn it. */
    public static void prepareCreative(ServerPlayer player) {
        if (!player.isAlive()) throw new IllegalStateException("capture player died before completion");
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }
}
