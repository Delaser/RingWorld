package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.ConfirmScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Narrow automation for the 26.2 file-fixer flow on an already opt-in copied
 * qualification world. It intentionally never handles downgrade, snapshot,
 * experimental, or arbitrary confirmation screens.
 */
final class CopiedWorldFileFixUpgrade {
    private static final String[] FIXTURE_ENABLE_PROPERTIES = {
            "ringworld.captureRingProjection",
            "ringworld.captureRingVisualParity",
            "ringworld.productionLifecycleTest"
    };
    private static final String BACKUP_CONFIRM_SCREEN =
            "net.minecraft.client.gui.screens.BackupConfirmScreen";
    private static final Component FILE_FIX_TITLE =
            Component.translatable("selectWorld.backupQuestion.file_fixing_required");
    private static final Component BACKUP_AND_JOIN =
            Component.translatable("selectWorld.backupJoinConfirmButton");
    private static final Component COMPLETE_TITLE = Component.translatable("upgradeWorld.done");
    private static final Component JOIN_NOW = Component.translatable("upgradeWorld.joinNow");
    private static final InputWithModifiers AUTOMATION_INPUT = new InputWithModifiers() {
        @Override public int input() { return 0; }
        @Override public int modifiers() { return 0; }
    };

    private Screen acceptedBackupScreen;
    private boolean acceptedFileFixBackup;
    private Screen acceptedCompletionScreen;

    boolean handleIfRequired(Minecraft client, String fixture, String copiedWorld) {
        if (!fixtureEnabled()) return false;
        Screen screen = RingMinecraftClientAccess.screen(client);
        if (screen == null) return false;
        if (isFileFixBackup(screen)) {
            if (screen != acceptedBackupScreen) {
                Button backup = matchingButton(screen, BACKUP_AND_JOIN);
                if (backup == null || !backup.isActive() || !backup.visible) return true;
                acceptedBackupScreen = screen;
                acceptedFileFixBackup = true;
                RingWorldMod.LOGGER.info(
                        "[{}] accepting copied-world file-fix backup confirmation world='{}' titleKey={} buttonKey={}",
                        fixture, copiedWorld, "selectWorld.backupQuestion.file_fixing_required",
                        "selectWorld.backupJoinConfirmButton");
                backup.onPress(AUTOMATION_INPUT);
            }
            return true;
        }
        if (acceptedFileFixBackup && isFileFixCompletion(screen)) {
            if (screen != acceptedCompletionScreen) {
                Button joinNow = ((ConfirmScreenAccessor) screen).ringworld$yesButton();
                if (joinNow == null || !joinNow.isActive() || !joinNow.visible) return true;
                acceptedCompletionScreen = screen;
                RingWorldMod.LOGGER.info(
                        "[{}] accepting copied-world file-fix completion world='{}' titleKey={} messageKey={}",
                        fixture, copiedWorld, "upgradeWorld.done", "upgradeWorld.joinNow");
                joinNow.onPress(AUTOMATION_INPUT);
            }
            return true;
        }
        return false;
    }

    static String currentScreen(Minecraft client) {
        Screen screen = RingMinecraftClientAccess.screen(client);
        if (screen == null) return "none";
        return screen.getClass().getName() + " title='" + screen.getTitle().getString() + "'";
    }

    private static boolean isFileFixBackup(Screen screen) {
        return screen.getClass().getName().equals(BACKUP_CONFIRM_SCREEN)
                && screen.getTitle().getString().equals(FILE_FIX_TITLE.getString());
    }

    private static boolean isFileFixCompletion(Screen screen) {
        return screen instanceof ConfirmScreen confirm
                && screen.getTitle().getString().equals(COMPLETE_TITLE.getString())
                && ((ConfirmScreenAccessor) confirm).ringworld$message().getString()
                .equals(JOIN_NOW.getString());
    }

    private static Button matchingButton(Screen screen, Component label) {
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label.getString()))
                .findFirst()
                .orElse(null);
    }

    private static boolean fixtureEnabled() {
        for (String property : FIXTURE_ENABLE_PROPERTIES) {
            if (Boolean.getBoolean(property)) return true;
        }
        return false;
    }
}
