package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/** Opt-in disposable real-block specimens for the world-creation style selector. */
final class RingWallSelectorSamples {
    private static final long SEED = 8128L;
    private static final int CIRCUMFERENCE = 16384;
    private final RingWallStyle.Pattern[] patterns = RingWallStyle.Pattern.selectableValues();
    private final Method sample;
    private final int sourceCenter;
    private int index, column, ticks, settle;
    private boolean initialized, captured;
    private CompletableFuture<Void> pending;

    RingWallSelectorSamples() {
        try {
            sample = RingGenerationBoundary.class.getDeclaredMethod("texturedRimBlock",
                    RingWallStyle.class, int.class, int.class, int.class, int.class, long.class);
            sample.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        int center = -1;
        for (int x = 0; x < CIRCUMFERENCE; x++) {
            var f = RingIndustrialElements.feature(x, CIRCUMFERENCE, SEED, 1);
            if (f.kind() == 6 && f.offsetX() == 0) { center = x; break; }
        }
        if (center < 0) throw new IllegalStateException("No machinery specimen found");
        sourceCenter = center;
    }

    void tick(Minecraft client) {
        if (++ticks > 24000) throw new IllegalStateException("Wall selector capture timeout");
        if (!initialized) {
            initialized = true;
            client.options.fov().set(60);
            client.options.renderDistance().set(6);
            client.options.bobView().set(false);
            client.getConnection().sendCommand("gamemode spectator @s");
            client.getConnection().sendCommand("weather clear");
            client.getConnection().sendCommand("time set noon");
            client.getConnection().sendCommand("tp @s 512.5 206.0 -32.5 0 0");
            RingWorldMod.LOGGER.info("[wall-selector] sample seed={} sourceCenter={} sourceY=64..96", SEED, sourceCenter);
            return;
        }
        if (ticks < 100) return;
        if (index == RingWallStyle.Palette.values().length * patterns.length) {
            RingWorldMod.LOGGER.info("[wall-selector] PASS: {} screenshots", index);
            client.stop();
            return;
        }
        if (pending != null) {
            if (!pending.isDone()) return;
            pending.join();
            pending = null;
        }
        RingWallStyle style = RingWallStyle.custom(7,
                RingWallStyle.Palette.values()[index / patterns.length], patterns[index % patterns.length], 0);
        if (column < 96) {
            int start = column;
            column += 4;
            pending = new CompletableFuture<>();
            var completion = pending;
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                try {
                    var level = server.overworld();
                    for (int u = start; u < start + 4; u++) {
                        int sourceX = sourceCenter + u - 48;
                        var feature = RingIndustrialElements.feature(sourceX, CIRCUMFERENCE, SEED, 1);
                        for (int y = 0; y < 33; y++) for (int z = -3; z <= 6; z++) {
                            BlockState block = z < 0 ? Blocks.AIR.defaultBlockState()
                                    : (BlockState) sample.invoke(null, style, sourceX, y + 64, 6 - z,
                                            CIRCUMFERENCE, SEED);
                            if (RingIndustrialElements.enabled(style)) {
                                int roll = RingIndustrialElements.roll(feature, y, 33, -z, 7);
                                if (roll != RingIndustrialElements.UNCHANGED) block = roll < 0
                                        ? Blocks.AIR.defaultBlockState()
                                        : RingGenerationBoundary.styledRimBlockForRoll(style, roll);
                            }
                            level.setBlock(new BlockPos(464 + u, 192 + y, z), block, 2);
                        }
                    }
                    completion.complete(null);
                } catch (Throwable e) { completion.completeExceptionally(e); }
            });
            return;
        }
        client.player.setYRot(0);
        client.player.setXRot(0);
        if (++settle < 100) return;
        if (!captured) {
            captured = true;
            String name = "wall-" + style.palette().name().toLowerCase(java.util.Locale.ROOT)
                    + "-" + style.pattern().name().toLowerCase(java.util.Locale.ROOT);
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name + ".png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info("[wall-selector] {}: {}", name, message.getString()));
        }
        if (settle >= 120) {
            index++; column = 0; settle = 0; captured = false;
            client.getConnection().sendCommand("time set noon");
        }
    }
}
