package rwqa;

import dev.ringworld.client.ClientRingState;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rwqa_state.Seen;

@Mixin(Minecraft.class)
abstract class LargeView {
    private static long start, last;
    private static int stop;

    @Inject(method = "tick", at = @At("TAIL"))
    private void qa(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || ClientRingState.geometry(mc.level) == null
                || mc.getSingleplayerServer() == null) return;
        if (ClientRingState.geometry(mc.level).circumferenceBlocks() != 32768
                || ClientRingState.geometry(mc.level).widthBlocks() != 512) return;
        long now = System.nanoTime();
        if (start == 0) {
            start = now;
            System.out.println("LARGE_FIXTURE " + ClientRingState.geometry(mc.level));
        }
        if (Seen.full) {
            if (Seen.swapped != 0 && now - Seen.swapped > 10_000_000_000L) {
                Seen.flush();
                System.out.println("LARGE_DONE missed=" + Seen.missed + " draws=" + Seen.frames);
                mc.stop();
            }
            return;
        }
        if (stop == 0
                || (Boolean.getBoolean("trial.tour")
                        && now - start > 60_000_000_000L
                        && now - last > Long.getLong("trial.dwellMs", 2000) * 1_000_000L)) {
            last = now;
            // Alternate travel directions on opposite sides and transversely nudge subsequent laps.
            int stations = 171,
                    lap = stop / (stations * 2),
                    lane = (stop / stations) % 2,
                    offset = stop % stations;
            double x =
                    (((lane == 0 ? offset : stations - 1 - offset) * 192 + 96 + lap * 47) % 32768)
                            + 0.5;
            double z = (lane == 0 ? -128 : 128) + 0.5;
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(
                    () -> {
                        var p = server.getPlayerList().getPlayer(id);
                        if (p == null) return;
                        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                        p.teleportTo(
                                server.overworld(), x, 160, z, java.util.Set.of(), 90, -12, false);
                    });
            if (stop % 20 == 0) System.out.println("LARGE_TOUR " + stop + " x=" + x + " z=" + z);
            stop++;
        }
    }
}
