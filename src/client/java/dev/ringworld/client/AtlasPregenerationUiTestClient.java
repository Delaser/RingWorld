package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.ConfirmScreenAccessor;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.InputWithModifiers;

/** Opt-in real-client GUI-scale-4 acceptance fixture for the player atlas map. */
public final class AtlasPregenerationUiTestClient {
    public static final String ENABLE_PROPERTY = "ringworld.atlasUiTest";
    private static final int SETTLE_FRAMES = 3;
    private static final double PROGRESSIVE_CAPTURE_COMPLETION = 0.25;
    private static final int TIMEOUT_TICKS = 14_400;
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

    public boolean enabled() { return Boolean.getBoolean(ENABLE_PROPERTY); }
    public void frameRendered() { renderedFrames++; }

    /**
     * Opens one disposable creative world for either loader's isolated UI
     * fixture. The map assertion itself never creates a second generation job.
     */
    public boolean startWorldIfEnabled(Minecraft client) {
        if (!enabled() || client.level != null || worldStarted) return false;
        if (!worldScreenOpened) {
            CreateWorldScreen.openFresh(client, () -> worldScreenOpened = false);
            worldScreenOpened = true;
            return true;
        }
        if (client.screen instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName("RingWorld Atlas UI Regression");
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed("-2162056627494116761");
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            worldStarted = true;
        }
        return true;
    }

    public boolean tick(Minecraft client) {
        if (!enabled() || client.player == null) return false;
        client.options.guiScale().set(4);
        if (++ticks > TIMEOUT_TICKS) return fail(client, "timed out before completion");
        AtlasPregenerationStatus status = AtlasPregenerationClientState.status().orElse(null);
        switch (stage) {
            case 0 -> { client.setScreen(new PauseScreen(true)); arm(); stage++; }
            case 1 -> {
                if (!(client.screen instanceof PauseScreen) || !settled()) return true;
                capture(client, "atlas-ui-01-pause-menu", false);
                client.setScreen(new RingWorldMapScreen(client.screen)); arm(); stage++;
            }
            case 2 -> {
                if (!(client.screen instanceof RingWorldMapScreen screen) || !settled()) return true;
                if (status == null) return true;
                if (!screen.buildLabelForAutomation().equals("Alpha 4 · 0.2.0+mc26.1.2")) {
                    return fail(client, "map screen showed the wrong embedded build identity: "
                            + screen.buildLabelForAutomation());
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
                ((ConfirmScreenAccessor)confirm).ringworld$yesButton().onPress(new TestInput()); arm(); stage++;
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
                retry.onPress(new TestInput()); arm(); stage++;
            }
            case 11 -> {
                if (!(client.screen instanceof ConfirmScreen confirm) || !settled()) return true;
                capture(client, "atlas-ui-10-retry-confirm", false);
                ((ConfirmScreenAccessor)confirm).ringworld$yesButton().onPress(new TestInput()); arm(); stage++;
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
                RingWorldMod.LOGGER.info("[atlas-ui-test] PASS: GUI scale 4 progressive-world/confirmation/running/background/reopen/pause/resume/cancel/retry/complete/revisioned-edit");
                client.stop();
                stage++;
            }
            default -> { }
        }
        return true;
    }

    private void arm() { readyAfterFrame = renderedFrames + SETTLE_FRAMES; }
    private boolean settled() { return renderedFrames >= readyAfterFrame; }
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
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> {
                    if (finalCapture) finalCaptureSaved = true;
                    RingWorldMod.LOGGER.info("[atlas-ui-test] screenshot {}", message.getString());
                });
    }
    private static final class TestInput implements InputWithModifiers {
        @Override public int input() { return 0; }
        @Override public int modifiers() { return 0; }
    }
}
