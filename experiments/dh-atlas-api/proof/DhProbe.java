package rw.dhqa;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.compat.DhAtlasSupport;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class DhProbe {
    private static boolean placeholder;
    private static int settled;
    private static int elapsed;

    @Inject(method = "tick", at = @At("TAIL"))
    private void inspect(CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || ClientRingState.geometry(mc.level) == null) return;
        if (elapsed == 0 && mc.getSingleplayerServer() != null) {
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(
                    () -> {
                        var player = server.getPlayerList().getPlayer(id);
                        if (player == null) return;
                        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                        player.teleportTo(
                                server.overworld(),
                                2.0,
                                120.0,
                                0.5,
                                java.util.Set.of(),
                                90,
                                -18,
                                false);
                    });
        }
        var nativeAtlas = ClientRingState.terrainAtlas();
        var visible = DhAtlasSupport.displayedSource();
        boolean dh = Boolean.getBoolean("probe.expectDh");
        if (visible != null
                && visible != nativeAtlas
                && !visible.isComplete()
                && RingSurfaceTextureRenderer.displayReady()) placeholder = true;
        if (Boolean.getBoolean("probe.expectPartial")
                && placeholder
                && elapsed >= 200
                && visible != null
                && visible != nativeAtlas
                && !visible.isComplete()) {
            System.out.println("RW_DH_PARTIAL_PASS");
            mc.stop();
            return;
        }
        if (visible != null
                && visible.isComplete()
                && RingSurfaceTextureRenderer.captureReady()
                && (dh ? placeholder && visible != nativeAtlas : visible == nativeAtlas)) {
            if (++settled == 8) {
                System.out.println(
                        "RW_DH_PROBE_PASS placeholder="
                                + placeholder
                                + " samples="
                                + visible.presentCount()
                                + " player="
                                + mc.player.getUUID());
                mc.stop();
            }
        } else settled = 0;
        if (++elapsed > 3600) {
            System.out.println(
                    "RW_DH_PROBE_TIMEOUT placeholder="
                            + placeholder
                            + " visible="
                            + (visible != null));
            mc.stop();
        }
    }
}
