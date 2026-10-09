package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.server.RingAtlasPregenerationService;
import dev.ringworld.world.AtlasPregenerationOptions;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Opt-in native regression on a disposable copied save; never runs in normal play. */
public final class RingFloatingAtlasCaptureClient {
    private static final RingFloatingAtlasCaptureClient INSTANCE = new RingFloatingAtlasCaptureClient();
    private static final String WORLD = "RingWorld Floating Atlas Review";
    private final CopiedWorldFileFixUpgrade fileFix = new CopiedWorldFileFixUpgrade();
    private CompletableFuture<Void> operation;
    private int stage, ticks, settle;
    private boolean opening, capturePending, finished;
    private long previousFrame, frameNanos, maximumFrame;
    private int frames, slowFrames;
    private volatile int[][] baseline;
    private volatile boolean serverReady;
    private volatile int[] probeX;

    public static boolean tickIfEnabled(Minecraft client) {
        return Boolean.getBoolean("ringworld.captureFloatingAtlas") && INSTANCE.tick(client);
    }

    public static void frameRendered() {
        if (!Boolean.getBoolean("ringworld.captureFloatingAtlas")) return;
        var fixture = INSTANCE;
        long now = System.nanoTime();
        if (fixture.stage >= 1 && fixture.stage <= 5 && fixture.settle >= 80 && fixture.settle < 240
                && fixture.previousFrame != 0) {
            long duration = now - fixture.previousFrame;
            fixture.frameNanos += duration;
            fixture.maximumFrame = Math.max(fixture.maximumFrame, duration);
            fixture.frames++;
            if (duration > 50_000_000L) fixture.slowFrames++;
        }
        fixture.previousFrame = now;
    }

    private boolean tick(Minecraft client) {
        if (finished) return true;
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        client.options.enableVsync().set(false);
        client.options.framerateLimit().set(60);
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        client.options.renderDistance().set(stage == 2 ? 10 : 4);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        if (++ticks > 12000) throw new IllegalStateException("Floating Atlas watchdog stage=" + stage
                + " screen=" + CopiedWorldFileFixUpgrade.currentScreen(client));
        if (fileFix.handleIfRequired(client, "floating-atlas", WORLD)) return true;
        if (operation != null) {
            if (!operation.isDone()) return true;
            operation.join();
            operation = null;
        }
        if (stage == 8 && client.level == null) {
            require(RingWorldClientSession.isCleared(), "disconnect left client Atlas state");
            stage = 9;
            opening = false;
        }
        if (client.level == null || client.player == null) {
            if (!opening && client.isGameLoadFinished()
                    && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                opening = true;
                client.createWorldOpenFlows().openWorld(WORLD,
                        () -> { throw new IllegalStateException("Floating Atlas open cancelled"); });
            }
            return true;
        }
        var atlas = ClientRingState.terrainAtlas();
        if (ClientRingState.geometry() == null || atlas == null || capturePending
                || RingMinecraftClientAccess.screen(client) != null) return true;
        if (stage == 0) {
            operate(client, context -> {
                RingIntegratedCaptureControl.normalizeEnvironment(context, 6000, false);
                int circumference = RingAtlasPregenerationService.atlas(context.world()).geometry().circumferenceBlocks();
                probeX = new int[]{512, 513, 514, 515, 0, circumference - 1, 520, 521, 522, 523};
                var source = context.server().createCommandSourceStack();
                context.server().getCommands().performPrefixedCommand(source, "forceload add 480 -8 543 8");
                context.server().getCommands().performPrefixedCommand(source, "forceload add 0 0");
                context.server().getCommands().performPrefixedCommand(source, "forceload add " + (circumference - 1) + " 0");
                for (int x : probeX) {
                    var chunk = context.world().getChunkAt(new BlockPos(x, 64, 0));
                    for (int y = 65; y <= 270; y++)
                        context.world().setBlock(new BlockPos(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
                    context.world().setBlock(new BlockPos(x, 64, 0),
                            x == 513 ? Blocks.WATER.defaultBlockState() : x == 514
                                    ? Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)
                                    : Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    require(chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, 0) == 64,
                            "fixture floor placement failed at x=" + x);
                }
                for (int y : new int[]{224, 225}) {
                    context.world().setBlock(new BlockPos(520, y, 0), Blocks.STONE.defaultBlockState(), 3);
                    context.world().setBlock(new BlockPos(521, y, 0), Blocks.OAK_LOG.defaultBlockState(), 3);
                }
                context.world().setBlock(new BlockPos(522, 72, 0), Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                for (int y = 73; y <= 137; y++) context.world().setBlock(new BlockPos(523, y, 0),
                        Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                RingAtlasPregenerationService.pregenerate(context.world(),
                        AtlasPregenerationOptions.backgroundDefaults(), progress -> { }).completion()
                        .thenRun(() -> serverReady = true);
            });
            stage = 1;
            return true;
        }
        if (!serverReady || !atlas.isComplete() || !RingSurfaceTextureRenderer.displayReady()) return true;
        if (stage == 1 && baseline == null) {
            operate(client, context -> {
                var serverAtlas = RingAtlasPregenerationService.atlas(context.world());
                baseline = Arrays.stream(probeX).mapToObj(x -> sample(serverAtlas, x)).toArray(int[][]::new);
                require(baseline[0][0] == 65 && baseline[1][4] == 15, "baseline grass/water sampling: " + Arrays.deepToString(baseline));
                require(baseline[2][1] != baseline[0][1], "foliage retains its own colour");
                require(baseline[6][0] == 226 && baseline[7][0] == 226
                        && baseline[8][0] == 73 && baseline[9][0] == 138,
                        "natural stone/log, short-gap and thick-layer controls");
                RingWorldMod.LOGGER.info("[floating-atlas] baseline: {}", Arrays.deepToString(baseline));
            });
            return true;
        }
        if (stage >= 1 && stage <= 5) {
            if (settle++ == 0) {
                int captureStage = stage;
                if (stage >= 3) RingClientLodTuning.select(dev.ringworld.world.RingLodQuality.values()[stage - 3]);
                operate(client, context -> {
                    if (captureStage == 2) floating(context);
                    Vec3 pose = captureStage == 2 ? new Vec3(570, 275, 55) : new Vec3(850, 185, 45);
                    Vec3 target = new Vec3(512, captureStage == 2 ? 235 : 100, 0);
                    Vec3 direction = RingAtlasPregenerationService.atlas(context.world()).geometry().toCameraLocal(target,
                            pose.add(0, context.player().getEyeHeight(), 0));
                    float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
                    context.player().teleportTo(context.world(), pose.x, pose.y, pose.z,
                            Set.<Relative>of(), yaw, pitch, false);
                });
                return true;
            }
            if (settle < 240) return true;
            if (stage >= 2) {
                if (!baselineMatches(atlas)) return true;
                if (settle == 240) {
                    operate(client, context -> {
                        require(baselineMatches(RingAtlasPregenerationService.atlas(context.world())),
                                "live stacked-platform recapture changed ground height/colour/light/water");
                        for (int x : probeX) require(context.world().getBlockState(new BlockPos(x, 251, 0))
                                .is(x == 515 ? Blocks.POLISHED_DEEPSLATE : Blocks.GOLD_BLOCK), "real floating build was removed");
                    });
                    return true;
                }
            }
            capture(client, stage == 1 ? "baseline" : stage == 2 ? "near"
                    : dev.ringworld.world.RingLodQuality.values()[stage - 3].command());
            stage++; settle = 0;
            return true;
        }
        if (stage == 6) {
            if (settle++ == 0) {
                operate(client, context -> {
                    for (int y = 65; y < 250; y++) context.world().setBlock(new BlockPos(512, y, 0),
                            Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                });
                return true;
            }
            if (sample(atlas, 512)[0] != 252 || settle < 120) return true;
            operate(client, context -> {
                require(sample(RingAtlasPregenerationService.atlas(context.world()), 512)[0] == 252,
                        "ground-connected column did not reappear in authoritative Atlas");
                for (int y = 65; y < 250; y++) context.world().setBlock(new BlockPos(512, y, 0),
                        Blocks.AIR.defaultBlockState(), 3);
                context.world().setBlock(new BlockPos(512, 220, 0), Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                context.world().setBlock(new BlockPos(512, 221, 0), Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
            });
            stage = 7; settle = 0;
            return true;
        }
        if (stage == 7) {
            if (++settle < 240 || !baselineMatches(atlas)) return true;
            operate(client, context -> {
                require(baselineMatches(RingAtlasPregenerationService.atlas(context.world())),
                        "support removal did not restore underlying terrain");
                // Exercise initial capture separately from the live edit queue, with the build still present.
                RingAtlasPregenerationService.captureLoadedChunk(context.world(), context.world().getChunkAt(new BlockPos(512, 0, 0)));
                require(baselineMatches(RingAtlasPregenerationService.atlas(context.world())), "initial chunk capture differs from live edits");
                RingWorldMod.LOGGER.info("[floating-atlas] live edits, stacked layers, seam, colour/light/water: PASS");
            });
            stage = 8; settle = 0;
            return true;
        }
        if (stage == 8 && ++settle > 40) {
            client.disconnectFromWorld(Component.literal("Floating Atlas save/reopen check"));
            return true;
        }
        if (stage == 9 && ++settle > 160) {
            require(baselineMatches(atlas), "reopened client cache lost filtered terrain");
            operate(client, context -> {
                require(baselineMatches(RingAtlasPregenerationService.atlas(context.world())), "reopened server cache lost filtered terrain");
                require(context.world().getBlockState(new BlockPos(512, 251, 0)).is(Blocks.GOLD_BLOCK),
                        "reopened real floating build missing");
                RingWorldMod.LOGGER.info("[floating-atlas] NATIVE PASS: initial/live/server/client/seam/stacked/materials/save/reopen format={}",
                        RingTerrainAtlas.FORMAT_VERSION);
            });
            stage = 10;
            return true;
        }
        if (stage == 10) { finished = true; client.stop(); }
        return true;
    }

    private void floating(RingIntegratedCaptureControl.Context context) {
        for (int x = 480; x < 544; x++) for (int z = -8; z < 8; z++)
            for (int y : new int[]{220, 221, 250, 251}) context.world().setBlock(new BlockPos(x, y, z),
                    (y < 250 ? Blocks.QUARTZ_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState(), 3);
        for (int x : probeX) for (int y : new int[]{220, 221, 250, 251})
            context.world().setBlock(new BlockPos(x, y, 0),
                    (x == 515 && y >= 250 ? Blocks.POLISHED_DEEPSLATE
                            : y < 250 ? Blocks.QUARTZ_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState(), 3);
    }

    private boolean baselineMatches(RingTerrainAtlas atlas) {
        for (int i = 0; i < probeX.length; i++) if (!Arrays.equals(baseline[i], sample(atlas, probeX[i]))) return false;
        return true;
    }

    private static int[] sample(RingTerrainAtlas atlas, int x) {
        int column = x / atlas.sampleStep();
        int row = -atlas.geometry().minWidthZ() / atlas.sampleStep();
        return new int[]{atlas.cellHeight(column, row), atlas.cellColor(column, row), atlas.cellSideColor(column, row),
                atlas.cellBlockLight(column, row), atlas.cellWaterCoverage(column, row)};
    }

    private void operate(Minecraft client, Consumer<RingIntegratedCaptureControl.Context> action) {
        operation = new CompletableFuture<>();
        RingIntegratedCaptureControl.execute(client, "floating-atlas stage " + stage, action,
                () -> operation.complete(null), detail -> operation.completeExceptionally(new IllegalStateException(detail)));
    }

    private void capture(Minecraft client, String name) {
        require(frames > 0, "no native frame pacing sample");
        RingWorldMod.LOGGER.info("[floating-atlas] frames stage={} count={} meanMs={} maximumMs={} over50ms={}",
                name, frames, frameNanos / 1_000_000.0 / frames, maximumFrame / 1_000_000.0, slowFrames);
        frames = slowFrames = 0; frameNanos = maximumFrame = previousFrame = 0;
        capturePending = true;
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "floating-atlas-" + name + ".png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> client.execute(() -> {
                    capturePending = false;
                    RingWorldMod.LOGGER.info("[floating-atlas] screenshot {}", message.getString());
                }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
