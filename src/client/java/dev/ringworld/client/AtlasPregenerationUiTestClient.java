package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.client.mixin.ConfirmScreenAccessor;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.compat.ClientWorldLifecycle;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;

/** Opt-in real-client GUI-scale-4 acceptance fixture for the player atlas map. */
public final class AtlasPregenerationUiTestClient {
    public static final String ENABLE_PROPERTY = "ringworld.atlasUiTest";
    public static final String OPTIONAL_VISUAL_PROPERTY = "ringworld.optionalVisualSmoke";
    public static final String EXPECTED_BUILD_LABEL_PROPERTY = "ringworld.atlasUiExpectedBuildLabel";
    private static final int SETTLE_FRAMES = 3;
    private static final int PARTIAL_HANDOFF_SETTLE_FRAMES = 60;
    private static final int PARTIAL_HANDOFF_VIEW_DISTANCE_CHUNKS = 6;
    private static final int PARTIAL_HANDOFF_FOV = 70;
    private static final long PARTIAL_HANDOFF_DAY_TIME = 6_000L;
    private static final double PROGRESSIVE_CAPTURE_COMPLETION = 0.25;
    private static final int TIMEOUT_TICKS = 14_400;
    private static final int DISCONNECT_TIMEOUT_TICKS = 200;
    private long renderedFrames;
    private long readyAfterFrame;
    private int stage;
    private int ticks;
    private boolean capturedInitial;
    private boolean finalCaptureSaved;
    private long revisionBeforeEdit;
    private int editedCellColumn;
    private int editedCellRow;
    private int editedBlockX;
    private int editedBlockZ;
    private boolean worldScreenOpened;
    private boolean worldStarted;
    private int menuTicks;
    private String lastMenuScreen = "";
    private boolean clientReadyLogged;
    private int disconnectTicks;
    private boolean disconnectInProgress;
    private boolean partialSetupRequested;
    private volatile boolean partialSetupComplete;
    private volatile String partialSetupFailure;
    private boolean partialCaptureSettling;
    private boolean capturePolicyLogged;
    private final OptionalVisualPhase optionalVisual = new OptionalVisualPhase();

    public boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY) || optionalVisualEnabled();
    }
    private boolean optionalVisualEnabled() {
        return Boolean.getBoolean(OPTIONAL_VISUAL_PROPERTY);
    }
    public void frameRendered() { renderedFrames++; }

    /**
     * Opens one disposable creative world for either loader's isolated UI
     * fixture. The map assertion itself never creates a second generation job.
     */
    public boolean startWorldIfEnabled(Minecraft client) {
        if (!enabled()) return false;
        // This fixture is launched unattended. Keep the integrated server
        // ticking after the final map screen closes so its revisioned block
        // placement/removal probe cannot be stranded by lost window focus.
        client.options.pauseOnLostFocus = false;
        // Publish the fixture radius before login so the integrated server
        // actually supplies the entire window used by the handoff capture.
        client.options.renderDistance().set(PARTIAL_HANDOFF_VIEW_DISTANCE_CHUNKS);
        client.options.graphicsMode().set(GraphicsStatus.FANCY);
        client.options.cloudStatus().set(CloudStatus.OFF);
        client.options.fov().set(PARTIAL_HANDOFF_FOV);
        if (!capturePolicyLogged) {
            capturePolicyLogged = true;
            RingWorldMod.LOGGER.info(
                    "[atlas-ui-test] applied pre-login view distance={}, "
                            + "graphics=fancy, clouds=off, fov={}",
                    PARTIAL_HANDOFF_VIEW_DISTANCE_CHUNKS, PARTIAL_HANDOFF_FOV);
        }
        if (client.level != null || worldStarted) return false;
        String currentScreen = client.screen == null ? "null" : client.screen.getClass().getName();
        if (!currentScreen.equals(lastMenuScreen)) {
            RingWorldMod.LOGGER.info("[atlas-ui-test] menu screen: {}", currentScreen);
            lastMenuScreen = currentScreen;
        }
        if (++menuTicks > 2_400) return fail(client,
                "timed out opening disposable world from " + currentScreen);
        if (!worldScreenOpened) {
            // Minecraft initially shows a GenericMessageScreen while its
            // title resources finish loading. openFresh invoked there can be
            // superseded by the later TitleScreen transition, leaving the
            // fixture waiting forever for an editor that was discarded.
            if (!(client.screen instanceof TitleScreen)) return true;
            RingWorldMod.LOGGER.info("[atlas-ui-test] opening fresh-world editor");
            CreateWorldScreen.openFresh(client, client.screen);
            worldScreenOpened = true;
            return true;
        }
        if (client.screen instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName(optionalVisualEnabled()
                    ? OptionalVisualPhase.WORLD_NAME : "RingWorld Atlas UI Regression");
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed("-2162056627494116761");
            RingWorldMod.LOGGER.info("[atlas-ui-test] creating disposable world");
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            worldStarted = true;
        }
        return true;
    }

    public boolean tick(Minecraft client) {
        if (!enabled()) return false;
        if (optionalVisualEnabled()) return optionalVisual.tick(client);
        client.options.guiScale().set(4);
        if (++ticks > TIMEOUT_TICKS) return fail(client, "timed out before completion");
        // A normal integrated-server disconnect clears player/level before
        // this fixture may claim the final teardown evidence.
        if (stage == 18) return disconnectInProgress || verifyDisconnectClear(client);
        if (client.player == null) return false;
        AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
        switch (stage) {
            case 0 -> {
                if (!verifyClientReady(client)) return true;
                client.setScreen(new PauseScreen(true)); arm(); stage++;
            }
            case 1 -> {
                if (!(client.screen instanceof PauseScreen) || !settled()) return true;
                capture(client, "atlas-ui-01-pause-menu", false);
                client.setScreen(new RingWorldMapScreen(client.screen)); arm(); stage++;
            }
            case 2 -> {
                if (!(client.screen instanceof RingWorldMapScreen screen) || !settled()) return true;
                if (status == null) return true;
                String expectedBuildLabel = System.getProperty(EXPECTED_BUILD_LABEL_PROPERTY, "").trim();
                if (expectedBuildLabel.isEmpty()) {
                    return fail(client, "missing expected embedded build identity property");
                }
                if (!screen.buildLabelForAutomation().equals(expectedBuildLabel)) {
                    return fail(client, "map screen showed the wrong embedded build identity: "
                            + screen.buildLabelForAutomation() + " (expected " + expectedBuildLabel + ")");
                }
                if (!screen.worldgenLabelForAutomation().equals("Worldgen: annular-complete-v2 (4)")) {
                    return fail(client, "map screen showed the wrong persisted worldgen identity: "
                            + screen.worldgenLabelForAutomation());
                }
                if (!capturedInitial) {
                    capture(client, "atlas-ui-02-map-initial", false);
                    capturedInitial = true;
                }
                if (status.progress().state() == AtlasPregenerationState.IDLE) {
                    screen.openStartConfirmationForAutomation(); arm(); stage++;
                } else if (status.progress().state() == AtlasPregenerationState.RUNNING) {
                    stage = 4; // Verify the automatic background handle is viewed, never duplicated.
                }
            }
            case 3 -> {
                if (!(client.screen instanceof ConfirmScreen confirm) || !settled()) return true;
                capture(client, "atlas-ui-03-confirm-cost", false);
                // Exercise the real affirmative widget/callback, not a direct packet.
                ((ConfirmScreenAccessor)confirm).ringworld$exitButtons().get(0).onPress(); arm(); stage++;
            }
            case 4 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.RUNNING || !settled()) return true;
                capture(client, "atlas-ui-04-running", false);
                // The next capture is world-only evidence. Keep the map/UI
                // screenshots unchanged while excluding HUD pixels from the
                // terrain/proxy continuity measurement.
                client.options.hideGui = true;
                client.setScreen(null); arm(); stage++;
            }
            case 5 -> {
                if (!settled() || status == null
                        || status.progress().state() != AtlasPregenerationState.RUNNING
                        || status.progress().totalCells() == 0
                        || (double)status.progress().presentCells() / status.progress().totalCells()
                        < PROGRESSIVE_CAPTURE_COMPLETION) return true;
                AtlasPregenerationClientState.control(
                        status.worldHash(), AtlasPregenerationAction.PAUSE);
                arm(); stage++;
            }
            case 6 -> {
                if (status == null) return true;
                AtlasPregenerationState partialState = status.progress().state();
                if (partialState.isTerminal()) {
                    return fail(client, "Atlas generation reached " + partialState
                            + " before the partial handoff could be captured");
                }
                if (partialState != AtlasPregenerationState.PAUSED) return true;
                if (partialSetupFailure != null) {
                    return fail(client, partialSetupFailure);
                }
                RingGeometry geometry = ClientRingState.geometry();
                var atlas = ClientRingState.terrainAtlas();
                if (geometry == null || atlas == null || atlas.isComplete()) {
                    return fail(client, "partial handoff lost its incomplete Atlas");
                }
                double targetX = geometry.circumferenceBlocks() / 4.0;
                double targetZ = 0.5;
                if (!partialSetupRequested) {
                    partialSetupRequested = true;
                    RingIntegratedCaptureControl.execute(client, "partial Atlas handoff setup",
                            context -> {
                                RingIntegratedCaptureControl.normalizeEnvironment(
                                        context, 6_000, false);
                                RingIntegratedCaptureControl.teleport(
                                        context, targetX, 120.0, targetZ);
                            },
                            () -> partialSetupComplete = true,
                            detail -> partialSetupFailure = detail);
                    return true;
                }
                if (!partialSetupComplete) return true;
                boolean atPosition = Math.abs(geometry.shortestCircumferenceDelta(
                        targetX, client.player.getX())) < 1.5
                        && Math.abs(client.player.getY() - 120.0) < 1.5
                        && Math.abs(client.player.getZ() - targetZ) < 1.5;
                if (!atPosition) return true;
                int effectiveChunks = client.options.getEffectiveRenderDistance();
                int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
                int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
                int loadedPositiveX = contiguousLoadedChunks(
                        client, cameraChunkX, cameraChunkZ, 1, effectiveChunks);
                int loadedNegativeX = contiguousLoadedChunks(
                        client, cameraChunkX, cameraChunkZ, -1, effectiveChunks);
                if (effectiveChunks != PARTIAL_HANDOFF_VIEW_DISTANCE_CHUNKS
                        || loadedPositiveX != effectiveChunks
                        || loadedNegativeX != effectiveChunks
                        || !client.levelRenderer.hasRenderedAllSections()
                        || !RingSurfaceTextureRenderer
                                .legacyStreamingWindowComplete()) return true;

                if (client.options.graphicsMode().get() != GraphicsStatus.FANCY
                        || client.options.cloudStatus().get() != CloudStatus.OFF
                        || client.options.fov().get() != PARTIAL_HANDOFF_FOV
                        || !client.options.hideGui) {
                    return fail(client, "partial handoff capture policy changed: graphics="
                            + client.options.graphicsMode().get() + ", clouds="
                            + client.options.cloudStatus().get() + ", fov="
                            + client.options.fov().get() + ", hudHidden="
                            + client.options.hideGui);
                }
                long dayTime = Math.floorMod(client.level.getDayTime(), 24_000L);
                float rainLevel = client.level.getRainLevel(1.0F);
                if (dayTime != PARTIAL_HANDOFF_DAY_TIME || rainLevel > 0.001F) {
                    partialCaptureSettling = false;
                    return true;
                }

                RingRenderProfile profile = RingRenderProfile.create(
                        geometry, effectiveChunks * 16.0);
                double targetDistance = profile.effectiveViewDistanceBlocks();
                float capturePitch = (float)geometry.pitchDegreesToIntrinsic(
                        client.player.getY(), ClientRingState.surfaceReferenceY(),
                        targetDistance, 0.0);
                client.player.setYRot(90.0F);
                client.player.setXRot(capturePitch);
                if (!partialCaptureSettling) {
                    partialCaptureSettling = true;
                    readyAfterFrame = renderedFrames + PARTIAL_HANDOFF_SETTLE_FRAMES;
                    return true;
                }
                if (!settled()) return true;
                if (!RingSurfaceTextureRenderer.legacyStreamingWindowComplete()) {
                    partialCaptureSettling = false;
                    return true;
                }

                float visibleCompletion =
                        RingSurfaceTextureRenderer.legacyProxyVisibleCompletion();
                float generationFog = RingSurfaceTextureRenderer.legacyProxyGenerationFog();
                float revealScale = RingSurfaceTextureRenderer.legacyProxyRevealScale();
                float expectedScale = 1.0F - generationFog;
                if (!(visibleCompletion > 0.0F && visibleCompletion < 0.95F)
                        || !(revealScale > 0.0F && revealScale < 0.95F)
                        || Math.abs(revealScale - expectedScale) > 0.01F) {
                    return fail(client, "partial handoff reveal envelope did not settle: "
                            + "visibleCompletion=" + visibleCompletion
                            + ", generationFog=" + generationFog
                            + ", revealScale=" + revealScale
                            + ", expectedScale=" + expectedScale);
                }
                RingWorldMod.LOGGER.info(
                        "[atlas-ui-test] partial-handoff requestedChunks={}, "
                                + "effectiveChunks={}, loadedX=+{}/-{}, presentCells={}, "
                                + "totalCells={}, visibleCompletion={}, generationFog={}, "
                                + "proxyRevealScale={}, streamingWindowComplete={}, pitch={}, "
                                + "graphics=fancy, clouds=off, hudHidden=true, fov={}, "
                                + "time={}, rain={}",
                        PARTIAL_HANDOFF_VIEW_DISTANCE_CHUNKS, effectiveChunks,
                        loadedPositiveX, loadedNegativeX,
                        status.progress().presentCells(), status.progress().totalCells(),
                        visibleCompletion, generationFog, revealScale, true, capturePitch,
                        PARTIAL_HANDOFF_FOV, dayTime, rainLevel);
                capture(client, "atlas-ui-05-progressive-handoff", false);
                AtlasPregenerationClientState.control(
                        status.worldHash(), AtlasPregenerationAction.RESUME);
                arm(); stage++;
            }
            case 7 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.RUNNING
                        || !settled()) return true;
                client.options.hideGui = false;
                client.setScreen(new RingWorldMapScreen(new PauseScreen(true))); arm(); stage++;
            }
            case 8 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.RUNNING || !settled()) return true;
                capture(client, "atlas-ui-06-reopened", false);
                AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.PAUSE); arm(); stage++;
            }
            case 9 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.PAUSED || !settled()) return true;
                capture(client, "atlas-ui-07-paused", false);
                AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.RESUME); arm(); stage++;
            }
            case 10 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.RUNNING || !settled()) return true;
                capture(client, "atlas-ui-08-resumed", false);
                AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.CANCEL); arm(); stage++;
            }
            case 11 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.CANCELLED || !settled()) return true;
                capture(client, "atlas-ui-09-cancelled", false);
                if (!(client.screen instanceof RingWorldMapScreen screen)) return true;
                Button retry = screen.children().stream().filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> button.getMessage().getString().contains("Retry Generate Entire Ring"))
                        .findFirst().orElse(null);
                if (retry == null) return fail(client, "retry button was not present after cancellation");
                retry.onPress(); arm(); stage++;
            }
            case 12 -> {
                if (!(client.screen instanceof ConfirmScreen confirm) || !settled()) return true;
                capture(client, "atlas-ui-10-retry-confirm", false);
                ((ConfirmScreenAccessor)confirm).ringworld$exitButtons().get(0).onPress(); arm(); stage++;
            }
            case 13 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.COMPLETE
                        || ClientRingState.terrainAtlas() == null
                        || !ClientRingState.terrainAtlas().isComplete()) return true;
                // Let RingWorldMapScreen consume the new status and rebuild
                // its widgets, and let the renderer perform its one detailed
                // texture/mesh transition, before accepting completion.
                arm(); stage++;
            }
            case 14 -> {
                if (!(client.screen instanceof RingWorldMapScreen) || !settled()) return true;
                if (!hasOnlyButton(client, "Done")) {
                    return fail(client, "completed screen retained an invalid action button");
                }
                capture(client, "atlas-ui-11-complete", true); arm(); stage++;
            }
            case 15 -> {
                if (!settled() || !finalCaptureSaved) return true;
                var atlas = ClientRingState.terrainAtlas();
                if (atlas == null) return fail(client, "complete atlas disappeared before revision test");
                client.setScreen(null);
                int step = atlas.sampleStep();
                editedCellColumn = atlas.geometry().wrapBlockX(client.player.getBlockX()) / step;
                editedCellRow = Math.floorDiv(client.player.getBlockZ() - atlas.geometry().minWidthZ(), step);
                editedCellRow = Math.max(0, Math.min(atlas.rows() - 1, editedCellRow));
                editedBlockX = editedCellColumn * step + step / 2;
                editedBlockZ = atlas.geometry().minWidthZ() + editedCellRow * step + step / 2;
                revisionBeforeEdit = atlas.revision();
                client.getConnection().sendCommand("setblock " + editedBlockX + " 200 " + editedBlockZ
                        + " minecraft:gold_block");
                stage++;
            }
            case 16 -> {
                var atlas = ClientRingState.terrainAtlas();
                if (atlas == null || atlas.revision() <= revisionBeforeEdit) return true;
                if (atlas.cellHeight(editedCellColumn, editedCellRow) != 201) {
                    return fail(client, "placed surface block did not reach the client atlas");
                }
                revisionBeforeEdit = atlas.revision();
                client.getConnection().sendCommand("setblock " + editedBlockX + " 200 " + editedBlockZ
                        + " minecraft:air");
                stage++;
            }
            case 17 -> {
                var atlas = ClientRingState.terrainAtlas();
                if (atlas == null || atlas.revision() <= revisionBeforeEdit) return true;
                if (atlas.cellHeight(editedCellColumn, editedCellRow) == 201) {
                    return fail(client, "removed surface block remained in the client atlas");
                }
                RingWorldMod.LOGGER.info("[atlas-ui-test] requesting normal integrated-server disconnect after revision proof");
                stage++;
                disconnectInProgress = true;
                try {
                    ClientWorldLifecycle.disconnect(client,
                            Component.literal("Atlas UI revision proof complete"));
                } finally {
                    disconnectInProgress = false;
                }
            }
            default -> { }
        }
        return true;
    }

    private void arm() { readyAfterFrame = renderedFrames + SETTLE_FRAMES; }
    private boolean settled() { return renderedFrames >= readyAfterFrame; }

    /**
     * This runs only after the real integrated world has connected and at
     * least one level frame has rendered. The server log separately proves
     * acceptance of the format-3 acknowledgement; this client proof binds the
     * resulting state to the fresh mapping-4 world actually being rendered.
     */
    private boolean verifyClientReady(Minecraft client) {
        if (client.level == null || client.getSingleplayerServer() == null || renderedFrames == 0) {
            return false;
        }
        if (ClientRingState.geometry() == null || ClientRingState.layoutFingerprint() == 0L) {
            return false;
        }
        if (RingWorldSettings.FORMAT_VERSION != 3
                || ClientRingState.terrainNoiseMapping() != RingTerrainNoiseMapping.CURRENT) {
            fail(client, "live settings identity was not format-3/mapping-4");
            return false;
        }
        if (!clientReadyLogged) {
            clientReadyLogged = true;
            RingWorldMod.LOGGER.info("[atlas-ui-test] client-ready renderedFrames={}", renderedFrames);
            RingWorldMod.LOGGER.info("[atlas-ui-test] settings-v3-mapping-4 fingerprint={}",
                    Long.toUnsignedString(ClientRingState.layoutFingerprint(), 16));
        }
        return true;
    }

    private boolean verifyDisconnectClear(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) {
            if (++disconnectTicks <= DISCONNECT_TIMEOUT_TICKS) return true;
            return fail(client, "normal disconnect did not complete");
        }
        if (!RingWorldClientSession.isCleared()) {
            if (++disconnectTicks <= DISCONNECT_TIMEOUT_TICKS) return true;
            return fail(client, "normal disconnect did not clear RingWorld client state");
        }
        RingWorldMod.LOGGER.info("[atlas-ui-test] disconnect-clear client-session=true");
        RingWorldMod.LOGGER.info("[atlas-ui-test] PASS: GUI scale 4 progressive-handoff/confirmation/running/background/reopen/pause/resume/cancel/retry/complete/revisioned-edit/normal-disconnect");
        client.stop();
        stage++;
        return true;
    }
    private static boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[atlas-ui-test] FAIL: {}", reason);
        client.stop();
        return true;
    }
    private static boolean hasOnlyButton(Minecraft client, String label) {
        if (client.screen == null) return false;
        var buttons = client.screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
        return buttons.size() == 1 && buttons.getFirst().getMessage().getString().equals(label);
    }
    private static int contiguousLoadedChunks(Minecraft client, int cameraChunkX,
                                              int cameraChunkZ, int stepX, int limit) {
        int loaded = 0;
        while (loaded < limit && client.level.getChunkSource().hasChunk(
                cameraChunkX + stepX * (loaded + 1), cameraChunkZ)) {
            loaded++;
        }
        return loaded;
    }
    private void capture(Minecraft client, String name, boolean finalCapture) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1, message -> {
            if (finalCapture) finalCaptureSaved = true;
            RingWorldMod.LOGGER.info("[atlas-ui-test] screenshot {}: {}", name, message.getString());
        });
    }

    /** Optional, compact world-render phase over this fixture's existing safe-small world. */
    private final class OptionalVisualPhase {
        private static final String WORLD_NAME = "RingWorld Optional Visual Smoke";
        private static final int SETTLE_FRAMES = 60;
        private static final int TIMEOUT_TICKS = 36_000;
        private static final int DISCONNECT_TIMEOUT_TICKS = 400;
        private static final RingWallStyle EXPECTED_WALL = RingWallStyle.custom(
                9, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.ENGINEERED, 37);
        private static final dev.ringworld.world.RingWorldGenerationSettings EXPECTED_GENERATION =
                new dev.ringworld.world.RingWorldGenerationSettings(
                        dev.ringworld.world.RingAtlasFidelity.HIGH,
                        dev.ringworld.world.RingWorldLayout.ARCHIPELAGO, true, true, 1);

        private VisualStage visualStage = VisualStage.WAIT_READY;
        private VisualStage afterScreenshot;
        private long visualReadyAfterFrame;
        private long reopenRequestedFrame;
        private int visualTicks;
        private int visualStageTicks;
        private int screenshotRequests;
        private int screenshotWrites;
        private int latestPreviewStage = -1;
        private boolean partialCaptureSaved;
        private boolean captureSettling;
        private boolean operationRequested;
        private volatile boolean operationComplete;
        private volatile String operationFailure;
        private boolean reopenRequested;
        private int disconnectCount;
        private int lightColumn;
        private int lightRow;
        private int baselineBlockLight;
        private BlockPos lightPosition;
        private long revisionBeforeLightChange;
        private long lightCaptureAfterNanos;

        private boolean tick(Minecraft client) {
            // Fabric invokes startWorldIfEnabled only after an unhandled
            // client tick. Preserve that existing Atlas fixture handoff until
            // Create World has actually been invoked; NeoForge calls the same
            // opener explicitly before tick.
            if (!worldStarted && client.player == null) return false;
            client.options.hideGui = true;
            if (++visualTicks > TIMEOUT_TICKS) return visualFail(client,
                    "timed out in stage " + visualStage);
            visualStageTicks++;
            if (operationFailure != null) return visualFail(client, operationFailure);
            observePreviewStage();
            if (client.screen instanceof PauseScreen) client.setScreen(null);

            switch (visualStage) {
                case WAIT_READY -> visualWaitReady(client);
                case WAIT_PREVIEW -> visualWaitPreview(client);
                case WAIT_PARTIAL_PAUSE -> visualWaitPartialPause(client);
                case PARTIAL_POSE -> visualPartialPose(client);
                case WAIT_COMPLETE -> visualWaitComplete(client);
                case COMPLETE_TERRAIN_POSE -> visualCompleteTerrainPose(client);
                case WALL_INNER_POSE -> visualWallPose(client, true);
                case WALL_OUTER_POSE -> visualWallPose(client, false);
                case APPLY_NIGHT_LARGE -> visualApplyNightLarge(client);
                case LIGHT_ON -> visualSetLight(client, true);
                case LIGHT_ON_POSE -> visualLightPose(client, true);
                case LIGHT_OFF -> visualSetLight(client, false);
                case LIGHT_OFF_POSE -> visualLightPose(client, false);
                case APPLY_VOID_NONE -> visualApplyVoidNone(client);
                case VOID_POSE -> visualVoidPose(client, false);
                case SCREENSHOT_PENDING -> visualWaitScreenshot();
                case FIRST_DISCONNECT -> visualDisconnect(client, false);
                case WAIT_FIRST_CLEAR -> visualWaitClear(client, false);
                case REOPEN -> visualReopen(client);
                case WAIT_REOPEN_READY -> visualWaitReopenReady(client);
                case REOPEN_POSE -> visualVoidPose(client, true);
                case FINAL_DISCONNECT -> visualDisconnect(client, true);
                case WAIT_FINAL_CLEAR -> visualWaitClear(client, true);
                case DONE -> { }
            }
            return true;
        }

        private void visualWaitReady(Minecraft client) {
            if (client.player == null || client.level == null
                    || client.getSingleplayerServer() == null || renderedFrames == 0) return;
            RingGeometry geometry = ClientRingState.geometry();
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
            if (geometry == null || atlas == null || status == null) return;
            if (!EXPECTED_GENERATION.equals(ClientRingState.generationSettings())) {
                visualFail(client, "generation settings mismatch: " + ClientRingState.generationSettings());
                return;
            }
            if (!EXPECTED_WALL.equals(ClientRingState.wallStyle())) {
                visualFail(client, "saved custom wall style mismatch: "
                        + ClientRingState.wallStyle());
                return;
            }
            if (!RingSkyProfile.DEFAULT.equals(ClientRingState.skyProfile())) {
                visualFail(client, "initial sky profile was not Atmosphere+Small: "
                        + ClientRingState.skyProfile());
                return;
            }
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] client-ready worldHash={} wall={} sky={}+{}",
                    Long.toUnsignedString(atlas.worldHash(), 16),
                    ClientRingState.wallStyle().conciseLabel(),
                    ClientRingState.skyProfile().backdrop(),
                    ClientRingState.skyProfile().lightSource());
            if (status.progress().state() == AtlasPregenerationState.IDLE) {
                AtlasPregenerationClientState.control(
                        status.worldHash(), AtlasPregenerationAction.START);
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] requested one safe-small Atlas generation");
            }
            visualAdvance(VisualStage.WAIT_PREVIEW);
        }

        private void visualWaitPreview(Minecraft client) {
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
            if (atlas == null || status == null) return;
            if (atlas.isComplete()
                    || status.progress().state() == AtlasPregenerationState.COMPLETE) {
                visualFail(client, "Atlas completed before mandatory staged-preview capture; "
                        + "latestStage=" + previewLabel(latestPreviewStage)
                        + ", presentCells=" + atlas.presentCount()
                        + "/" + atlas.cellCount());
                return;
            }
            if (status.progress().state() != AtlasPregenerationState.RUNNING
                    || atlas.presentCount() == 0
                    || ClientRingState.terrainPreview() == null) return;
            AtlasPregenerationClientState.control(
                    status.worldHash(), AtlasPregenerationAction.PAUSE);
            visualAdvance(VisualStage.WAIT_PARTIAL_PAUSE);
        }

        private void visualWaitPartialPause(Minecraft client) {
            AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            if (status == null || atlas == null) return;
            if (atlas.isComplete() || status.progress().state().isTerminal()) {
                visualFail(client, "Atlas reached " + status.progress().state()
                        + " before mandatory staged-preview pause; latestStage="
                        + previewLabel(latestPreviewStage) + ", presentCells="
                        + atlas.presentCount() + "/" + atlas.cellCount());
                return;
            }
            if (status.progress().state() == AtlasPregenerationState.PAUSED) {
                visualAdvance(VisualStage.PARTIAL_POSE);
            }
        }

        private void visualPartialPose(Minecraft client) {
            RingGeometry geometry = ClientRingState.geometry();
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
            if (geometry == null || atlas == null || status == null
                    || client.player == null) return;
            if (atlas.isComplete()) {
                visualFail(client,
                        "Atlas completed during mandatory staged-preview pose");
                return;
            }
            double x = geometry.circumferenceBlocks() / 4.0;
            double y = 120.0;
            if (!visualEnsurePose(client, "partial preview", x, y, 0.5,
                    6_000, 90.0F, visualTangentPitch(geometry, y))) return;
            float visible = RingSurfaceTextureRenderer.legacyProxyVisibleCompletion();
            float fog = RingSurfaceTextureRenderer.legacyProxyGenerationFog();
            float reveal = RingSurfaceTextureRenderer.legacyProxyRevealScale();
            if (!(visible > 0.0F && visible < 1.0F) || !(reveal > 0.0F)) {
                visualFail(client,
                        "incomplete preview renderer did not become visible: completion="
                                + visible + ", fog=" + fog + ", reveal=" + reveal);
                return;
            }
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] partial-preview stage={} presentCells={}/{} "
                            + "visibleCompletion={} generationFog={} reveal={} "
                            + "mixinRender=true shaderDraw=true",
                    previewLabel(latestPreviewStage),
                    atlas.presentCount(), atlas.cellCount(), visible, fog, reveal);
            partialCaptureSaved = true;
            AtlasPregenerationClientState.control(
                    status.worldHash(), AtlasPregenerationAction.RESUME);
            visualCapture(client, "optional-visual-01-partial-preview",
                    VisualStage.WAIT_COMPLETE);
        }

        private void visualWaitComplete(Minecraft client) {
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
            if (atlas == null || status == null
                    || status.progress().state() != AtlasPregenerationState.COMPLETE
                    || !atlas.isComplete()) return;
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] complete-atlas cells={}/{} revision={} "
                            + "latestPreviewStage={} partialCapture={}",
                    atlas.presentCount(), atlas.cellCount(), atlas.revision(),
                    previewLabel(latestPreviewStage), partialCaptureSaved);
            if (atlas.sampleStep() != 4) {
                visualFail(client, "High Atlas source did not use 4-block samples"); return;
            }
            if (!operationRequested) {
                visualRequestServerOperation(client, "new feature generation verification", context -> {
                    var settings = RingWorldSettings.get(context.world());
                    if (!EXPECTED_GENERATION.equals(settings.generationSettings()))
                        throw new IllegalStateException("server generation settings mismatch");
                    var policy = dev.ringworld.world.RingStructurePolicy.get(context.world());
                    if (!policy.increasesStructureDensity())
                        throw new IllegalStateException("more-structures policy missing");
                    var macro = new dev.ringworld.world.RingMacroTerrain(
                            settings.geometry(), settings.generatorSeed(), settings.generationSettings());
                    int waterColumns = 0;
                    for (int x = 0; x < settings.circumferenceBlocks(); x += 64) {
                        int z = (int)Math.round(macro.riverCenterZ(x));
                        var pos = new BlockPos(x, 62, z);
                        if (context.world().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) waterColumns++;
                    }
                    if (waterColumns < 24) throw new IllegalStateException(
                            "river centerline water missing: " + waterColumns + "/32 columns");
                    RingWorldMod.LOGGER.info("[optional-visual-smoke] new-features generation=ARCHIPELAGO riverWater={}/32 moreStructures=true atlasStep=4", waterColumns);
                });
                return;
            }
            if (!operationComplete) return;
            visualAdvance(VisualStage.COMPLETE_TERRAIN_POSE);
        }

        private void visualCompleteTerrainPose(Minecraft client) {
            RingGeometry geometry = ClientRingState.geometry();
            if (geometry == null || client.player == null) return;
            double y = 120.0;
            if (!visualEnsurePose(client, "complete terrain",
                    geometry.circumferenceBlocks() / 4.0, y, 0.5,
                    6_000, 90.0F, visualTangentPitch(geometry, y))) return;
            if (!visualRequireProfile(client, RingSkyProfile.Backdrop.ATMOSPHERE,
                    RingSkyProfile.LightSource.SMALL, "complete terrain")) return;
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] renderer-runtime completeAtlas=true "
                            + "mixinRender=true shaderDraw=true profile=ATMOSPHERE+SMALL");
            visualCapture(client, "optional-visual-02-complete-atmosphere-small",
                    VisualStage.WALL_INNER_POSE);
        }

        private void visualWallPose(Minecraft client, boolean inner) {
            RingGeometry geometry = ClientRingState.geometry();
            if (geometry == null || client.player == null || client.level == null) return;
            int thickness = ClientRingState.wallStyle().thicknessBlocks();
            double x = 2.0;
            double y = client.level.getMinBuildHeight()
                    + ClientRingState.wallHeightBlocks() + 12.0;
            double z = inner
                    ? geometry.minWidthZ() + thickness + 16.5
                    : geometry.minWidthZ() - 16.5;
            String face = inner ? "inner-top" : "outer-top";
            if (!visualEnsurePose(client, "custom wall " + face, x, y, z,
                    6_000, inner ? 135.0F : 45.0F, 20.0F)) return;
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] custom-wall face={} seamX={} thickness={} "
                            + "palette={} pattern={} decay={} continuity=true",
                    face, x, thickness, ClientRingState.wallStyle().palette(),
                    ClientRingState.wallStyle().pattern(),
                    ClientRingState.wallStyle().decayPercent());
            visualCapture(client, inner
                            ? "optional-visual-03-custom-wall-inner-top-seam"
                            : "optional-visual-04-custom-wall-outer-top-seam",
                    inner ? VisualStage.WALL_OUTER_POSE
                            : VisualStage.APPLY_NIGHT_LARGE);
        }

        private void visualApplyNightLarge(Minecraft client) {
            if (client.getConnection() == null) return;
            RingSkyProfile profile = ClientRingState.skyProfile();
            if (profile.backdrop() == RingSkyProfile.Backdrop.NIGHT
                    && profile.lightSource() == RingSkyProfile.LightSource.LARGE) {
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] live-profile commands=sky-night,sun-large "
                                + "applied=NIGHT+LARGE");
                visualAdvance(VisualStage.LIGHT_ON);
                return;
            }
            if (!operationRequested) {
                operationRequested = true;
                client.getConnection().sendCommand("ringworld sky night");
                client.getConnection().sendCommand("ringworld sun large");
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] sent live /ringworld sky night and "
                                + "/ringworld sun large");
            }
        }

        private void visualSetLight(Minecraft client, boolean lit) {
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            RingGeometry geometry = ClientRingState.geometry();
            if (atlas == null || geometry == null || client.player == null) return;
            if (!operationRequested) {
                if (lightPosition == null) selectUnlitCell(atlas, geometry);
                int y = lit ? atlas.cellHeight(lightColumn, lightRow)
                        : lightPosition.getY();
                lightPosition = new BlockPos(lightPosition.getX(), y, lightPosition.getZ());
                revisionBeforeLightChange = atlas.revision();
                visualRequestServerOperation(client,
                        "set authored block light " + lit,
                        context -> {
                            context.world().setChunkForced(lightPosition.getX() >> 4,
                                    lightPosition.getZ() >> 4, true);
                            context.world().setBlockAndUpdate(lightPosition.below(), lit
                                    ? Blocks.REDSTONE_BLOCK.defaultBlockState()
                                    : Blocks.STONE.defaultBlockState());
                            context.world().setBlockAndUpdate(lightPosition,
                                    Blocks.REDSTONE_LAMP.defaultBlockState()
                                            .setValue(RedstoneLampBlock.LIT, lit));
                        });
                return;
            }
            if (!operationComplete) return;
            int observed = atlas.cellBlockLight(lightColumn, lightRow);
            boolean lightReady = lit
                    ? observed > baselineBlockLight
                    : observed == baselineBlockLight;
            if (atlas.revision() <= revisionBeforeLightChange || !lightReady) {
                if (visualStageTicks > 1200) visualFail(client,
                        "lamp revision did not converge: lit=" + lit + " cell=" + lightColumn + "," + lightRow
                                + " observed=" + observed + " revision=" + atlas.revision());
                return;
            }
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] authored-block-light lit={} position={} "
                            + "cell={},{} blockLight={} baseline={} revision={}",
                    lit, lightPosition, lightColumn, lightRow, observed,
                    baselineBlockLight, atlas.revision());
            // Complete Atlas updates coalesce for up to ten seconds before
            // the asynchronous upload and 750 ms texture morph. Frame counts
            // alone can finish before even the three-second quiet window.
            lightCaptureAfterNanos = System.nanoTime() + 12_000_000_000L;
            visualAdvance(lit ? VisualStage.LIGHT_ON_POSE
                    : VisualStage.LIGHT_OFF_POSE);
        }

        private void selectUnlitCell(RingTerrainAtlas atlas, RingGeometry geometry) {
            int preferred = (geometry.circumferenceBlocks() / 2) / atlas.sampleStep();
            lightRow = Math.clamp(
                    Math.floorDiv(-geometry.minWidthZ(), atlas.sampleStep()),
                    0, atlas.rows() - 1);
            lightColumn = -1;
            for (int offset = 0; offset < atlas.columns(); offset++) {
                int candidate = Math.floorMod(preferred + offset, atlas.columns());
                if (atlas.cellBlockLight(candidate, lightRow) == 0) {
                    lightColumn = candidate;
                    break;
                }
            }
            if (lightColumn < 0) lightColumn = Math.clamp(
                    preferred, 0, atlas.columns() - 1);
            baselineBlockLight = atlas.cellBlockLight(lightColumn, lightRow);
            int x = lightColumn * atlas.sampleStep() + atlas.sampleStep() / 2;
            int z = geometry.minWidthZ() + lightRow * atlas.sampleStep()
                    + atlas.sampleStep() / 2;
            lightPosition = new BlockPos(x, atlas.cellHeight(lightColumn, lightRow), z);
        }

        private void visualLightPose(Minecraft client, boolean lit) {
            RingGeometry geometry = ClientRingState.geometry();
            if (geometry == null || client.player == null) return;
            if (System.nanoTime() < lightCaptureAfterNanos) return;
            if (!visualEnsurePose(client, "night block light " + lit,
                    0.5, 120.0, 0.5, 18_000, 90.0F, -90.0F)) return;
            if (!visualRequireProfile(client, RingSkyProfile.Backdrop.NIGHT,
                    RingSkyProfile.LightSource.LARGE, "night block light")) return;
            visualCapture(client, lit
                            ? "optional-visual-05-night-large-light-on"
                            : "optional-visual-06-night-large-light-off",
                    lit ? VisualStage.LIGHT_OFF : VisualStage.APPLY_VOID_NONE);
        }

        private void visualApplyVoidNone(Minecraft client) {
            if (client.getConnection() == null) return;
            if (lightPosition != null && client.getSingleplayerServer() != null) {
                var position = lightPosition;
                var server = client.getSingleplayerServer();
                server.execute(() -> server.overworld().setChunkForced(
                        position.getX() >> 4, position.getZ() >> 4, false));
                lightPosition = null;
            }
            RingSkyProfile profile = ClientRingState.skyProfile();
            if (profile.backdrop() == RingSkyProfile.Backdrop.VOID
                    && profile.lightSource() == RingSkyProfile.LightSource.NONE) {
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] live-profile commands=sky-void,sun-none "
                                + "applied=VOID+NONE");
                visualAdvance(VisualStage.VOID_POSE);
                return;
            }
            if (!operationRequested) {
                operationRequested = true;
                client.getConnection().sendCommand("ringworld sky void");
                client.getConnection().sendCommand("ringworld sun none");
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] sent live /ringworld sky void and "
                                + "/ringworld sun none");
            }
        }

        private void visualVoidPose(Minecraft client, boolean reopened) {
            RingGeometry geometry = ClientRingState.geometry();
            if (geometry == null || client.player == null) return;
            double y = 120.0;
            if (!visualEnsurePose(client,
                    reopened ? "reopened Void+None" : "Void+None",
                    geometry.circumferenceBlocks() / 4.0, y, 0.5,
                    18_000, 90.0F, visualTangentPitch(geometry, y))) return;
            if (!visualRequireProfile(client, RingSkyProfile.Backdrop.VOID,
                    RingSkyProfile.LightSource.NONE, "Void+None")) return;
            visualCapture(client, reopened
                            ? "optional-visual-08-reopen-void-none"
                            : "optional-visual-07-void-none",
                    reopened ? VisualStage.FINAL_DISCONNECT
                            : VisualStage.FIRST_DISCONNECT);
        }

        private void visualDisconnect(Minecraft client, boolean finalDisconnect) {
            if (client.level == null || client.getSingleplayerServer() == null) return;
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] requesting normal disconnect final={}",
                    finalDisconnect);
            disconnectCount++;
            ClientWorldLifecycle.disconnect(client, Component.literal(
                    finalDisconnect ? "Optional visual smoke complete"
                            : "Optional visual smoke reopen checkpoint"));
            visualAdvance(finalDisconnect
                    ? VisualStage.WAIT_FINAL_CLEAR : VisualStage.WAIT_FIRST_CLEAR);
        }

        private void visualWaitClear(Minecraft client, boolean finalClear) {
            if (client.level != null || client.getSingleplayerServer() != null
                    || !RingWorldClientSession.isCleared()) {
                if (visualStageTicks <= DISCONNECT_TIMEOUT_TICKS) return;
                visualFail(client, "normal disconnect did not clear the client session");
                return;
            }
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] disconnect-clear ordinal={} "
                            + "clientSession=true renderer=true textures=true",
                    disconnectCount);
            if (finalClear) {
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] PASS captures={} partialCapture={} "
                                + "oneWorld=true reopen=true",
                        screenshotWrites, partialCaptureSaved);
                visualStage = VisualStage.DONE;
                client.stop();
            } else {
                visualAdvance(VisualStage.REOPEN);
            }
        }

        private void visualReopen(Minecraft client) {
            if (client.level != null || client.getSingleplayerServer() != null) return;
            if (!reopenRequested && client.isGameLoadFinished()) {
                reopenRequested = true;
                reopenRequestedFrame = renderedFrames;
                RingWorldMod.LOGGER.info(
                        "[optional-visual-smoke] reopening same disposable world '{}'",
                        WORLD_NAME);
                client.createWorldOpenFlows().openWorld(WORLD_NAME,
                        () -> visualFail(client, "same-world reopen was cancelled"));
                visualAdvance(VisualStage.WAIT_REOPEN_READY);
            }
        }

        private void visualWaitReopenReady(Minecraft client) {
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            if (client.player == null || client.level == null || atlas == null
                    || !atlas.isComplete() || renderedFrames <= reopenRequestedFrame) return;
            if (!EXPECTED_GENERATION.equals(ClientRingState.generationSettings())) {
                visualFail(client, "generation settings mismatch: " + ClientRingState.generationSettings());
                return;
            }
            if (!EXPECTED_WALL.equals(ClientRingState.wallStyle())) {
                visualFail(client, "reopen lost the saved custom wall style");
                return;
            }
            RingSkyProfile expected = new RingSkyProfile(
                    RingSkyProfile.Backdrop.VOID,
                    RingSkyProfile.LightSource.NONE,
                    RingSkyProfile.FORMAT_VERSION);
            if (!expected.equals(ClientRingState.skyProfile())) return;
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] reopen-ready completeAtlas=true "
                            + "wallPersisted=true profile=VOID+NONE");
            visualAdvance(VisualStage.REOPEN_POSE);
        }

        private boolean visualEnsurePose(
                Minecraft client, String label,
                double x, double y, double z, int time,
                float yaw, float pitch) {
            RingGeometry geometry = ClientRingState.geometry();
            if (geometry == null || client.player == null) return false;
            if (!operationRequested) {
                visualRequestServerOperation(client, label + " pose", context -> {
                    RingIntegratedCaptureControl.normalizeEnvironment(context, time, false);
                    RingIntegratedCaptureControl.teleport(context, x, y, z);
                });
                return false;
            }
            if (!operationComplete) return false;
            boolean atPose = Math.abs(geometry.shortestCircumferenceDelta(
                    x, client.player.getX())) < 1.5
                    && Math.abs(client.player.getY() - y) < 1.5
                    && Math.abs(client.player.getZ() - z) < 1.5;
            if (!atPose) return false;
            client.player.setYRot(yaw);
            client.player.setXRot(pitch);
            RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
            boolean incompleteAtlas = atlas == null || !atlas.isComplete();
            if ((incompleteAtlas && !client.levelRenderer.hasRenderedAllSections())
                    || RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame() < 0.5F) {
                captureSettling = false;
                return false;
            }
            if (!captureSettling) {
                captureSettling = true;
                visualReadyAfterFrame = renderedFrames + SETTLE_FRAMES;
                return false;
            }
            return renderedFrames >= visualReadyAfterFrame
                    && RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame() >= 0.5F;
        }

        private void visualRequestServerOperation(
                Minecraft client, String label,
                java.util.function.Consumer<RingIntegratedCaptureControl.Context> action) {
            operationRequested = true;
            operationComplete = false;
            operationFailure = null;
            RingIntegratedCaptureControl.execute(client, label, action,
                    () -> operationComplete = true,
                    detail -> operationFailure = detail);
        }

        private boolean visualRequireProfile(
                Minecraft client, RingSkyProfile.Backdrop backdrop,
                RingSkyProfile.LightSource light, String label) {
            RingSkyProfile profile = ClientRingState.skyProfile();
            if (profile.backdrop() == backdrop && profile.lightSource() == light) {
                return true;
            }
            visualFail(client, label + " profile mismatch: " + profile);
            return false;
        }

        private float visualTangentPitch(RingGeometry geometry, double cameraY) {
            double distance = RingRenderProfile.create(
                    geometry, PARTIAL_HANDOFF_VIEW_DISTANCE_CHUNKS * 16.0)
                    .effectiveViewDistanceBlocks();
            return (float)geometry.pitchDegreesToIntrinsic(
                    cameraY, ClientRingState.surfaceReferenceY(), distance, 0.0);
        }

        private void visualCapture(
                Minecraft client, String name, VisualStage next) {
            screenshotRequests++;
            afterScreenshot = next;
            Screenshot.grab(client.gameDirectory, name + ".png",
                    client.getMainRenderTarget(), 1, message -> {
                        screenshotWrites++;
                        RingWorldMod.LOGGER.info(
                                "[optional-visual-smoke] screenshot {}: {}",
                                name, message.getString());
                    });
            visualAdvance(VisualStage.SCREENSHOT_PENDING);
        }

        private void visualWaitScreenshot() {
            if (screenshotWrites >= screenshotRequests) {
                visualAdvance(afterScreenshot);
            }
        }

        private void observePreviewStage() {
            int observed = ClientRingState.terrainPreviewStage();
            if (observed < 0 || observed == latestPreviewStage) return;
            latestPreviewStage = observed;
            RingWorldMod.LOGGER.info(
                    "[optional-visual-smoke] preview-transition stage={} wire={}",
                    previewLabel(observed), observed);
        }

        private String previewLabel(int wireValue) {
            return wireValue < 0 ? "none"
                    : RingTerrainPreviewStage.fromWireValue(wireValue).logLabel();
        }

        private void visualAdvance(VisualStage next) {
            visualStage = next;
            visualStageTicks = 0;
            operationRequested = false;
            operationComplete = false;
            operationFailure = null;
            captureSettling = false;
            visualReadyAfterFrame = renderedFrames;
        }

        private boolean visualFail(Minecraft client, String reason) {
            if (visualStage == VisualStage.DONE) return true;
            RingWorldMod.LOGGER.error(
                    "[optional-visual-smoke] FAIL stage={} reason={}",
                    visualStage, reason);
            visualStage = VisualStage.DONE;
            client.stop();
            return true;
        }

        private enum VisualStage {
            WAIT_READY,
            WAIT_PREVIEW,
            WAIT_PARTIAL_PAUSE,
            PARTIAL_POSE,
            WAIT_COMPLETE,
            COMPLETE_TERRAIN_POSE,
            WALL_INNER_POSE,
            WALL_OUTER_POSE,
            APPLY_NIGHT_LARGE,
            LIGHT_ON,
            LIGHT_ON_POSE,
            LIGHT_OFF,
            LIGHT_OFF_POSE,
            APPLY_VOID_NONE,
            VOID_POSE,
            SCREENSHOT_PENDING,
            FIRST_DISCONNECT,
            WAIT_FIRST_CLEAR,
            REOPEN,
            WAIT_REOPEN_READY,
            REOPEN_POSE,
            FINAL_DISCONNECT,
            WAIT_FINAL_CLEAR,
            DONE
        }
    }
}
