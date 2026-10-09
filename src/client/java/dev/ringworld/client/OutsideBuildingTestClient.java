package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingBuildingControlPayload;
import dev.ringworld.net.RingMultiplayerTestPayload;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in companion to the server placement and persistence fixture. */
public final class OutsideBuildingTestClient {
    private static final boolean RESUME = Boolean.getBoolean("ringworld.outsideBuildingResume");
    private static int stage, age;
    private static boolean sent, action, uiOpened, uiChecked;
    private static double seamTravel;

    public static boolean finishIfDisconnected(Minecraft client) {
        if (client.level != null || !sent || stage != (RESUME ? 1 : 5)) return false;
        client.stop();
        return true;
    }

    public static void tick(Minecraft client, String role) {
        client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        client.options.framerateLimit().set(40);
        var geometry = ClientRingState.geometry();
        if (geometry == null) return;
        boolean actor = role.equals("A");
        int edge = stage == 1 ? geometry.minWidthZ() : geometry.maxWidthZ();
        int outside = edge + (stage == 1 ? -1 : 1);
        int x = stage == 4 || RESUME ? 64 + (stage == 1 ? 16 : 0) : 64 + stage * 16;
        double expectedX = stage == 5 && !RESUME ? actor ? geometry.circumferenceBlocks() - .5 : 4.5 : x + (actor ? .5 : 4.5);
        double expectedZ = actor && (stage == 4 || RESUME || stage == 5) ? outside + .5 : edge + (stage == 1 ? 1.5 : -.5);
        boolean positioned = Math.abs(geometry.shortestCircumferenceDelta(expectedX, client.player.getX())) < (stage == 5 ? 4 : 1)
                && Math.abs(expectedZ - client.player.getZ()) < 1;
        if (!positioned) {
            if (sent) { stage++; sent = action = uiOpened = uiChecked = false; seamTravel = 0; }
            age = 0;
            return;
        }
        if (sent || ++age < 30) return;
        BlockPos target = new BlockPos(stage == 5 && !RESUME ? 0 : x, stage == 5 && !RESUME ? 121 : 120, outside);
        if (!client.level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) return;
        if (!RESUME && actor && !action) {
            if (stage == 0 || stage == 1 || stage == 3) {
                if (!client.player.getMainHandItem().is(net.minecraft.world.item.Items.STONE)) return;
                client.player.setYRot(stage == 1 ? 180 : 0);
                BlockPos anchor = new BlockPos(x, 120, edge);
                Direction direction = stage == 1 ? Direction.NORTH : Direction.SOUTH;
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(x + .5, 120.5, edge + (stage == 1 ? 0 : 1)), direction, anchor, false));
            } else if (stage == 2) client.getConnection().sendCommand("ringworld building outside off");
            if (stage != 5) action = true;
        }
        if (!RESUME && !actor && stage == 3 && !action) {
            RingClientPayloadTransport.send(new RingBuildingControlPayload(ClientRingState.layoutFingerprint(), true));
            client.getConnection().sendCommand("ringworld building outside on");
            action = true;
        }
        if (!RESUME && stage == 5) {
            if (!uiOpened) {
                RingWorldMapScreen screen = new RingWorldMapScreen(null);
                RingMinecraftClientAccess.setScreen(client, screen);
                button(screen, "World").onPress(RingWorldCreationScreen.AutomationInput.INSTANCE);
                uiOpened = true;
                return;
            }
            if (!uiChecked && RingMinecraftClientAccess.screen(client) instanceof RingWorldMapScreen screen) {
                Button toggle = button(screen, "Outside building:");
                if (toggle.active != actor) throw new IllegalStateException("Incorrect world-control permissions");
                capture(client, role, "world-menu");
                if (actor) toggle.onPress(RingWorldCreationScreen.AutomationInput.INSTANCE);
                uiChecked = true;
                return;
            }
            if (!ClientRingState.outsideBuilding()) return;
            RingMinecraftClientAccess.setScreen(client, null);
            if (actor && seamTravel < 2) {
                double nextX = client.player.getX() + .25;
                client.player.setPos(nextX, client.player.getY(), client.player.getZ());
                client.getConnection().send(new ServerboundMovePlayerPacket.PosRot(nextX, client.player.getY(),
                        client.player.getZ(), client.player.getYRot(), client.player.getXRot(), client.player.onGround(), false));
                seamTravel += .25;
                return;
            }
            target = new BlockPos(0, 121, outside);
            if (actor && !action) {
                BlockPos anchor = new BlockPos((int)geometry.nearestImageX(0, client.player.getX()), 120, outside);
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(anchor).add(0, .5, 0), Direction.UP, anchor, false));
                action = true;
                return;
            }
        }
        boolean ready = RESUME || stage == 0 || stage == 1 || stage == 4 || stage == 5
                ? client.level.getBlockState(target).is(Blocks.STONE)
                : !ClientRingState.outsideBuilding() && (stage != 3 || client.level.getBlockState(target).isAir());
        if (RESUME || stage == 4) ready &= !actor || (client.player.onGround() && Math.abs(client.player.getY() - 121) < .1);
        if (!ready || age < 65) return;
        RingWorldMod.LOGGER.info("[outside-building:{}] stage={} result=true resume={}", role, stage, RESUME);
        RingClientPayloadTransport.send(new RingMultiplayerTestPayload(role, "outside_" + stage, true, age));
        capture(client, role, Integer.toString(stage));
        sent = true;
    }

    private static Button button(RingWorldMapScreen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
    }
    private static void capture(Minecraft client, String role, String suffix) {
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "outside-" + role + '-' + (RESUME ? "reopen-" : "") + suffix + ".png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> {});
    }
}
