package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingMultiplayerTestPayload;
import dev.ringworld.world.RingGeometry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundSource;

/** Checks actual render extraction as well as network presence; dormant without the test flag. */
public final class OffRingVisibilityTestClient {
    private static final boolean ENABLED = Boolean.getBoolean("ringworld.offRingVisibilityTest");
    private static final Set<Integer> extracted = new HashSet<>();
    private static int stage;
    private static int stableTicks;
    private static int samples;
    private static boolean sent;

    public static void recordExtraction(Entity entity) {
        if (ENABLED) extracted.add(entity.getId());
    }

    public static boolean finishIfDisconnected(Minecraft client) {
        if (stage < 7 || client.level != null) return false;
        client.stop();
        return true;
    }

    public static void tick(Minecraft client, String role) {
        client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        client.options.setCameraType(role.equals("A") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || stage > 6) return;
        Entity a = role.equals("A") ? client.player : client.level.players().stream()
                .filter(p -> p.getName().getString().equals("RingTesterA")).findFirst().orElse(null);
        double x = stage == 5 ? geometry.circumferenceBlocks() - 0.5 : 64.5 + stage * 16;
        double z = stage == 2 || stage == 6 ? geometry.minWidthZ() - 8.5
                : stage == 1 ? 0.5 : geometry.maxWidthZ() + 9.5;
        double bz = stage == 2 || stage == 6 ? geometry.minWidthZ() + 8.5
                : stage == 1 ? 0.5 : geometry.maxWidthZ() - 8.5;
        boolean positioned = role.equals("A")
                ? Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), x)) < 1
                    && Math.abs(client.player.getZ() - z) < 1
                : Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(),
                    stage == 3 ? x + 300 : geometry.wrapX(x + 6))) < 1
                    && Math.abs(client.player.getZ() - bz) < 1;
        if (!positioned) {
            if (sent) { stage++; sent = false; samples = 0; }
            stableTicks = 0;
            extracted.clear();
            return;
        }
        if (sent) return;
        if (role.equals("B") && stage != 3) {
            double dx = geometry.shortestCircumferenceDelta(client.player.getX(), x);
            client.player.setYRot((float)Math.toDegrees(Math.atan2(-dx, z - client.player.getZ())));
            client.player.setXRot(a == null ? 0 : (float)-Math.toDegrees(Math.atan2(
                    a.getY() - client.player.getY(), Math.hypot(dx, z - client.player.getZ()))));
        }
        if (++stableTicks < 40) { extracted.clear(); return; }
        boolean visible = stage == 3 && role.equals("B") ? a == null
                : a != null && extracted.contains(a.getId());
        if (role.equals("B") && stage != 1 && stage != 3 && stage != 6) {
            long objects = java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                    .filter(e -> e.getCustomName() != null && e.getCustomName().getString().startsWith("RingVisibility-"))
                    .filter(e -> extracted.contains(e.getId())).count();
            visible &= objects == 2;
        }
        extracted.clear();
        samples = visible ? samples + 1 : 0;
        if (samples < 10 || !RingClientPayloadTransport.canSend(RingMultiplayerTestPayload.ID)) return;
        RingWorldMod.LOGGER.info("[off-ring:{}] stage={} result=true renderSamples={} A={}", role, stage, samples,
                a == null ? "absent" : a.position());
        RingClientPayloadTransport.send(new RingMultiplayerTestPayload(role, "off_ring_" + stage, true, samples));
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "off-ring-" + role + '-' + stage + ".png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> {});
        sent = true;
    }
}
