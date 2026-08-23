package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.ConfirmScreenAccessor;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.Minecraft;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.compat.ClientWorldLifecycle;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

/** Opt-in real-client GUI-scale-4 acceptance fixture for the player atlas map. */
public final class AtlasPregenerationUiTestClient {
    public static final String ENABLE_PROPERTY = "ringworld.atlasUiTest";
    public static final String EXPECTED_BUILD_LABEL_PROPERTY = "ringworld.atlasUiExpectedBuildLabel";
    private static final int SETTLE_FRAMES = 3;
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

    public boolean enabled() { return Boolean.getBoolean(ENABLE_PROPERTY); }
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
            creator.setName("RingWorld Atlas UI Regression");
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
        client.options.guiScale().set(4);
        if (++ticks > TIMEOUT_TICKS) return fail(client, "timed out before completion");
        // A normal integrated-server disconnect clears player/level before
        // this fixture may claim the final teardown evidence.
        if (stage == 17) return disconnectInProgress || verifyDisconnectClear(client);
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
                client.setScreen(null); arm(); stage++;
            }
            case 5 -> {
                if (!settled() || status == null || status.progress().totalCells() == 0
                        || (double)status.progress().presentCells() / status.progress().totalCells()
                        < PROGRESSIVE_CAPTURE_COMPLETION) return true;
                client.player.setYRot(90.0F);
                client.player.setXRot(-65.0F);
                capture(client, "atlas-ui-05-progressive-world", false); arm(); stage++;
            }
            case 6 -> {
                if (!settled()) return true;
                client.setScreen(new RingWorldMapScreen(new PauseScreen(true))); arm(); stage++;
            }
            case 7 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.RUNNING || !settled()) return true;
                capture(client, "atlas-ui-06-reopened", false);
                AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.PAUSE); arm(); stage++;
            }
            case 8 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.PAUSED || !settled()) return true;
                capture(client, "atlas-ui-07-paused", false);
                AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.RESUME); arm(); stage++;
            }
            case 9 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.RUNNING || !settled()) return true;
                capture(client, "atlas-ui-08-resumed", false);
                AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.CANCEL); arm(); stage++;
            }
            case 10 -> {
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
            case 11 -> {
                if (!(client.screen instanceof ConfirmScreen confirm) || !settled()) return true;
                capture(client, "atlas-ui-10-retry-confirm", false);
                ((ConfirmScreenAccessor)confirm).ringworld$exitButtons().get(0).onPress(); arm(); stage++;
            }
            case 12 -> {
                if (status == null || status.progress().state() != AtlasPregenerationState.COMPLETE
                        || ClientRingState.terrainAtlas() == null
                        || !ClientRingState.terrainAtlas().isComplete()) return true;
                // Let RingWorldMapScreen consume the new status and rebuild
                // its widgets, and let the renderer perform its one detailed
                // texture/mesh transition, before accepting completion.
                arm(); stage++;
            }
            case 13 -> {
                if (!(client.screen instanceof RingWorldMapScreen) || !settled()) return true;
                if (!hasOnlyButton(client, "Done")) {
                    return fail(client, "completed screen retained an invalid action button");
                }
                capture(client, "atlas-ui-11-complete", true); arm(); stage++;
            }
            case 14 -> {
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
            case 15 -> {
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
            case 16 -> {
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
        RingWorldMod.LOGGER.info("[atlas-ui-test] PASS: GUI scale 4 progressive-world/confirmation/running/background/reopen/pause/resume/cancel/retry/complete/revisioned-edit/normal-disconnect");
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
    private void capture(Minecraft client, String name, boolean finalCapture) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1, message -> {
            if (finalCapture) finalCaptureSaved = true;
            RingWorldMod.LOGGER.info("[atlas-ui-test] screenshot {}: {}", name, message.getString());
        });
    }
}
