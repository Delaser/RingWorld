package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.ConfirmScreenAccessor;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/**
 * Menu-only graphical acceptance fixture for the immutable RingWorld layout
 * editor. It is intentionally inert unless {@value #ENABLE_PROPERTY} is
 * supplied and never calls CreateWorldScreen's create-level action.
 */
public final class RingWorldCreationUiTestClient {
    public static final String ENABLE_PROPERTY = "ringworld.creationUiTest";
    private static final int REQUIRED_FRAMEBUFFER_WIDTH = 1_920;
    private static final int MINIMUM_FRAMEBUFFER_HEIGHT = 1_080;
    private static final int SCALE_FOUR_LOGICAL_WIDTH = 480;
    private static final int MINIMUM_SCALE_FOUR_LOGICAL_HEIGHT = 270;
    private static final int SETTLE_FRAMES = 3;
    private static final int STARTUP_SETTLE_FRAMES = 120;
    private static final int TIMEOUT_TICKS = 1_200;
    private static final int CAPTURE_COUNT = 11;

    private static RingWorldCreationUiTestClient activeFixture;

    private long renderedFrames;
    private long readyAfterFrame;
    private int stage;
    private int ticks;
    private boolean windowResizeRequested;
    private boolean titleScreenObserved;
    private boolean menuRequestSubmitted;
    private boolean capturePending;
    private boolean finished;
    private int capturesSaved;

    public boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    /**
     * Creates only Minecraft's Create World menu. The fixture stops before
     * its real confirmation could ever return to the normal world-create path.
     */
    public boolean startMenuIfEnabled(Minecraft client) {
        if (!enabled()) return false;
        if (finished) return true;
        activeFixture = this;
        if (client.level != null) {
            fail(client, "a level was already active; the creation UI fixture is menu-only");
            return true;
        }
        if (!windowResizeRequested) {
            setGuiScale(client, 1);
            if (!hasRequiredFramebuffer(client)) resizeToRequiredFramebuffer(client);
            windowResizeRequested = true;
            RingWorldMod.LOGGER.info("[creation-ui-test] normalizing a 1920-wide framebuffer from {}x{} pixels",
                    client.getWindow().getWidth(), client.getWindow().getHeight());
            arm();
            return true;
        }
        if (hasRequiredFramebuffer(client) && !menuRequestSubmitted
                && client.screen instanceof TitleScreen) {
            if (!titleScreenObserved) {
                titleScreenObserved = true;
                readyAfterFrame = renderedFrames + STARTUP_SETTLE_FRAMES;
                return true;
            }
            if (!settled()) return true;
            // Vanilla may retain a loading screen while it prepares the
            // world-creation context. Stage zero waits for the real screen
            // and its injected footer instead of submitting another request.
            menuRequestSubmitted = true;
            RingWorldMod.LOGGER.info("[creation-ui-test] framebuffer ready; opening Create World");
            CreateWorldScreen.openFresh(client, () -> { });
            arm();
        }
        return true;
    }

    public boolean tick(Minecraft client) {
        if (!enabled()) return false;
        if (finished) return true;
        activeFixture = this;
        if (client.level != null) return fail(client, "a level became active during the menu-only fixture");
        if (++ticks > TIMEOUT_TICKS) return fail(client, "timed out in stage " + stage
                + "; framebuffer=" + client.getWindow().getWidth() + "x" + client.getWindow().getHeight()
                + ", window=" + client.getWindow().getScreenWidth() + "x" + client.getWindow().getScreenHeight()
                + ", screen=" + (client.screen == null ? "none" : client.screen.getClass().getSimpleName())
                + ", menuRequestSubmitted=" + menuRequestSubmitted);
        if (capturePending || !settled()) return true;

        switch (stage) {
            case 0 -> openLayoutFromFooter(client);
            case 1 -> captureDefaultAndChangeScale(client, 1, "creation-ui-02-default-scale1", 2);
            case 2 -> captureDefaultAndChangeScale(client, 2, "creation-ui-03-default-scale2", 3);
            case 3 -> captureDefaultAndChangeScale(client, 3, "creation-ui-04-default-scale3", 4);
            case 4 -> {
                RingWorldCreationScreen screen = creationScreen(client);
                if (screen == null || !hasLogicalSize(
                        client, SCALE_FOUR_LOGICAL_WIDTH, MINIMUM_SCALE_FOUR_LOGICAL_HEIGHT)) {
                    fail(client, "GUI scale 4 did not produce a 480-wide layout at least 270 pixels tall");
                    return true;
                }
                capture(client, "creation-ui-05-default-scale4");
                screen.ringworld$automationSetLayout(1_001, 255, 31);
                arm();
                stage++;
            }
            case 5 -> {
                RingWorldCreationScreen screen = creationScreen(client);
                if (screen == null || screen.ringworld$automationCanApply()
                        || screen.ringworld$automationValidationMessageCount() != 5) {
                    fail(client, "the invalid layout did not expose all five expected validation errors");
                    return true;
                }
                capture(client, "creation-ui-06-invalid-five-errors-scale4");
                screen.ringworld$automationPressSafeSmall();
                arm();
                stage++;
            }
            case 6 -> {
                RingWorldCreationScreen screen = creationScreen(client);
                if (screen == null || !screen.ringworld$automationCanApply()
                        || !screen.ringworld$automationHasLayout(2_048, 416, 160)) {
                    fail(client, "the safe-small preset did not restore its valid 2048x416x160 layout");
                    return true;
                }
                capture(client, "creation-ui-07-safe-small-scale4");
                screen.ringworld$automationPressProduction();
                arm();
                stage++;
            }
            case 7 -> {
                RingWorldCreationScreen screen = creationScreen(client);
                if (screen == null || !screen.ringworld$automationCanApply()
                        || !screen.ringworld$automationHasLayout(
                                RingWorldSettings.DEFAULT_CIRCUMFERENCE,
                                RingWorldSettings.DEFAULT_WIDTH,
                                RingWorldSettings.DEFAULT_WALL_HEIGHT)) {
                    fail(client, "the recommended production preset was not applied");
                    return true;
                }
                capture(client, "creation-ui-08-production-scale4");
                screen.ringworld$automationSetLayout(4_096, 640, 192);
                if (!screen.ringworld$automationMonumentRequested()) {
                    screen.ringworld$automationToggleMonument();
                }
                screen.ringworld$automationPressSavedConfig();
                if (!screen.ringworld$automationHasLayout(2_048, 416, 160)
                        || screen.ringworld$automationMonumentRequested()) {
                    fail(client, "Saved config values did not restore dimensions and monument choice together");
                    return true;
                }
                screen.ringworld$automationSetLayout(4_096, 640, 192);
                screen.ringworld$automationToggleMonument();
                arm();
                stage++;
            }
            case 8 -> {
                RingWorldCreationScreen screen = creationScreen(client);
                if (screen == null || !screen.ringworld$automationCanApply()
                        || !screen.ringworld$automationHasLayout(4_096, 640, 192)
                        || !screen.ringworld$automationMonumentRequested()) {
                    fail(client, "the custom 4096x640x192 monument layout was not applied");
                    return true;
                }
                capture(client, "creation-ui-09-custom-monument-scale4");
                screen.ringworld$automationApply();
                arm();
                stage++;
            }
            case 9 -> {
                if (!(client.screen instanceof ConfirmScreen confirm)
                        || !"Lock and use".equals(((ConfirmScreenAccessor) confirm)
                                .ringworld$yesButton().getMessage().getString())) {
                    fail(client, "the real layout confirmation screen was not opened");
                    return true;
                }
                capture(client, "creation-ui-10-confirm-layout-scale4");
                ((ConfirmScreenAccessor) confirm).ringworld$yesButton()
                        .onPress(RingWorldCreationScreen.AutomationInput.INSTANCE);
                arm();
                stage++;
            }
            case 10 -> verifyAppliedFooterAndStop(client);
            default -> { }
        }
        return true;
    }

    /** Called after every actual client frame, including menus on both loaders. */
    public static void frameRendered() {
        RingWorldCreationUiTestClient fixture = activeFixture;
        if (fixture != null && fixture.enabled()) fixture.renderedFrames++;
    }

    private void openLayoutFromFooter(Minecraft client) {
        if (!(client.screen instanceof CreateWorldScreen screen)
                || !(screen instanceof RingWorldCreationScreen.LayoutButtonOwner owner)
                || !owner.ringworld$layoutButtonReadyForAutomation()) return;
        capture(client, "creation-ui-01-footer-scale1");
        owner.ringworld$openLayoutEditorForAutomation();
        arm();
        stage++;
    }

    private void captureDefaultAndChangeScale(
            Minecraft client, int scale, String name, int nextScale) {
        RingWorldCreationScreen screen = creationScreen(client);
        int expectedWidth = REQUIRED_FRAMEBUFFER_WIDTH / scale;
        int minimumHeight = MINIMUM_FRAMEBUFFER_HEIGHT / scale;
        if (screen == null || !hasLogicalSize(client, expectedWidth, minimumHeight)) {
            fail(client, "GUI scale " + scale + " did not keep the creation editor "
                    + expectedWidth + " pixels wide and at least " + minimumHeight + " pixels tall");
            return;
        }
        capture(client, name);
        setGuiScale(client, nextScale);
        arm();
        stage++;
    }

    private void verifyAppliedFooterAndStop(Minecraft client) {
        if (!(client.screen instanceof CreateWorldScreen screen)
                || !(screen instanceof RingWorldCreationScreen.LayoutButtonOwner owner)
                || !"RingWorld 4096×640".equals(owner.ringworld$layoutButtonMessageForAutomation().getString())) {
            fail(client, "the accepted confirmation did not refresh the real Create World footer");
            return;
        }
        capture(client, "creation-ui-11-footer-applied-scale4");
        // Screenshot's callback stops the client immediately after the last
        // write completes. There is deliberately no world-creation call here.
    }

    private void capture(Minecraft client, String name) {
        capturePending = true;
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1, message -> {
            // Screenshot writes on Minecraft's I/O pool. Keep fixture state
            // and the shutdown path on the client thread.
            client.execute(() -> {
                capturesSaved++;
                capturePending = false;
                RingWorldMod.LOGGER.info("[creation-ui-test] screenshot {}", message.getString());
                if (capturesSaved == CAPTURE_COUNT) {
                    RingWorldMod.LOGGER.info("[creation-ui-test] PASS: 11 menu-only captures across GUI scales 1-4; "
                            + "the confirmed 4096x640x192 monument layout refreshed the footer.");
                    activeFixture = null;
                    client.stop();
                }
            });
        });
    }

    private void setGuiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeGui();
    }

    private static void resizeToRequiredFramebuffer(Minecraft client) {
        int framebufferWidth = client.getWindow().getWidth();
        int framebufferHeight = client.getWindow().getHeight();
        int screenWidth = client.getWindow().getScreenWidth();
        int screenHeight = client.getWindow().getScreenHeight();
        double pixelRatioX = screenWidth > 0 ? (double) framebufferWidth / screenWidth : 1.0;
        double pixelRatioY = screenHeight > 0 ? (double) framebufferHeight / screenHeight : 1.0;
        client.getWindow().setWindowed(
                Math.max(1, (int) Math.round(REQUIRED_FRAMEBUFFER_WIDTH / pixelRatioX)),
                Math.max(1, (int) Math.round(MINIMUM_FRAMEBUFFER_HEIGHT / pixelRatioY)));
    }

    private static boolean hasRequiredFramebuffer(Minecraft client) {
        return client.getWindow().getWidth() == REQUIRED_FRAMEBUFFER_WIDTH
                && client.getWindow().getHeight() >= MINIMUM_FRAMEBUFFER_HEIGHT;
    }

    private static boolean hasLogicalSize(Minecraft client, int width, int minimumHeight) {
        return client.getWindow().getGuiScaledWidth() == width
                && client.getWindow().getGuiScaledHeight() >= minimumHeight;
    }

    private RingWorldCreationScreen creationScreen(Minecraft client) {
        return client.screen instanceof RingWorldCreationScreen screen ? screen : null;
    }

    private void arm() {
        readyAfterFrame = renderedFrames + SETTLE_FRAMES;
    }

    private boolean settled() {
        return renderedFrames >= readyAfterFrame;
    }

    private boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[creation-ui-test] FAIL: {}", reason);
        finished = true;
        activeFixture = null;
        client.stop();
        return true;
    }
}
