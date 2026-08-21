package dev.ringworld.client;

import dev.ringworld.RingWorldBuildIdentity;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationStatus;
import dev.ringworld.world.AtlasPregenerationView;
import dev.ringworld.world.RingTerrainNoiseMapping;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Responsive, non-pausing map/progress screen for the single atlas service. */
public final class RingWorldMapScreen extends Screen {
    private final Screen parent;
    private long requestedWorldHash = Long.MIN_VALUE;

    public RingWorldMapScreen(Screen parent) {
        super(Component.literal("RingWorld Map"));
        this.parent = parent;
    }

    public static boolean canOpen() {
        return ClientRingState.geometry() != null && AtlasPregenerationClientState.canRequestCurrent();
    }

    @Override
    public boolean isPauseScreen() {
        // Replacing PauseScreen with this explicitly non-pausing screen lets
        // an integrated server tick while its owner watches live progress.
        return false;
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        Optional<AtlasPregenerationStatus> current = AtlasPregenerationClientState.status();
        if (current.isEmpty()) AtlasPregenerationClientState.requestCurrent();
        if (current.isPresent() && current.get().worldHash() != requestedWorldHash) {
            requestedWorldHash = current.get().worldHash();
            AtlasPregenerationClientState.request(requestedWorldHash);
        }
        rebuildIfActionsChanged(current);
    }

    private String lastActions = "";
    private void rebuildIfActionsChanged(Optional<AtlasPregenerationStatus> current) {
        String actions = current.map(value -> AtlasPregenerationView.from(value).actions().toString()).orElse("loading");
        if (!actions.equals(lastActions)) rebuild();
    }

    private void rebuild() {
        clearWidgets();
        Optional<AtlasPregenerationStatus> current = AtlasPregenerationClientState.status();
        if (current.isPresent()) {
            AtlasPregenerationStatus status = current.get();
            requestedWorldHash = status.worldHash();
            AtlasPregenerationClientState.request(status.worldHash());
            AtlasPregenerationView view = AtlasPregenerationView.from(status);
            lastActions = view.actions().toString();
            int y = Math.min(height - 74, 190);
            if (view.actions().contains(AtlasPregenerationAction.START)) {
                addRenderableWidget(Button.builder(Component.literal(status.progress().state().isTerminal()
                                ? "Retry Generate Entire Ring" : "Generate Entire Ring"), button -> confirmStart(status))
                        .bounds(width / 2 - 100, y, 200, 20).build());
                y += 24;
            }
            if (view.actions().contains(AtlasPregenerationAction.PAUSE)) {
                addRenderableWidget(controlButton("Pause", AtlasPregenerationAction.PAUSE, status, y)); y += 24;
            }
            if (view.actions().contains(AtlasPregenerationAction.RESUME)) {
                addRenderableWidget(controlButton("Resume", AtlasPregenerationAction.RESUME, status, y)); y += 24;
            }
            if (view.actions().contains(AtlasPregenerationAction.CANCEL)) {
                addRenderableWidget(controlButton("Cancel", AtlasPregenerationAction.CANCEL, status, y)); y += 24;
            }
        } else {
            lastActions = "loading";
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private Button controlButton(String label, AtlasPregenerationAction action,
                                 AtlasPregenerationStatus status, int y) {
        return Button.builder(Component.literal(label), button -> {
                    AtlasPregenerationClientState.control(status.worldHash(), action);
                    rebuild();
                })
                .bounds(width / 2 - 100, y, 200, 20).build();
    }

    private void confirmStart(AtlasPregenerationStatus status) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.START);
            minecraft.setScreen(this);
        }, Component.literal("Generate Entire Ring?"),
                Component.literal("This generates and saves %,d canonical terrain chunks. "
                        .formatted(status.canonicalChunks())
                        + "It can take time and will create real region files on disk."),
                Component.literal("Generate"), Component.literal("Back")));
    }

    /** Package-local hook for the opt-in real-client GUI-scale regression. */
    void openStartConfirmationForAutomation() {
        AtlasPregenerationClientState.status().ifPresent(this::confirmStart);
    }

    /** Package-local build identity hook for the real menu fixture. */
    String buildLabelForAutomation() {
        return RingWorldBuildIdentity.displayLabel();
    }

    /** Package-local saved-world identity hook for the real menu fixture. */
    String worldgenLabelForAutomation() {
        return worldgenLabel();
    }

    @Override
    public void onClose() {
        // Do not return to PauseScreen: this is intentionally a backgroundable
        // integrated-server workflow, so Done resumes normal play immediately.
        minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        super.render(context, mouseX, mouseY, deltaTicks);

        int center = width / 2;
        context.drawCenteredString(font, title, center, 18, 0xFFFFFFFF);
        context.drawCenteredString(font, Component.literal(RingWorldBuildIdentity.displayLabel()),
                center, 30, 0xFF909090);

        Optional<AtlasPregenerationStatus> current = AtlasPregenerationClientState.status();
        if (current.isEmpty()) {
            context.drawCenteredString(font,
                    Component.literal("Requesting authoritative generation status…"),
                    center, 55, 0xFFD0D0D0);
            return;
        }

        AtlasPregenerationView view = AtlasPregenerationView.from(current.get());

        String[] lines = {
                worldgenLabel(),
                "Dimensions: " + view.dimensions(),
                view.chunks(),
                view.cells(),
                "State: " + view.state(),
                "Elapsed: " + view.elapsed(),
                "Rate: " + view.rate(),
                "ETA: " + view.eta()
        };

        for (int i = 0; i < lines.length; i++) {
            context.drawCenteredString(font, Component.literal(lines[i]),
                    center, 43 + i * 15, 0xFFD0D0D0);
        }

        if (!current.get().canControl()) {
            context.drawCenteredString(font,
                    Component.literal("Read-only: ask the owner or a gamemaster to control generation."),
                    center, 162, 0xFFFFD060);
        } else if (!view.error().isEmpty()) {
            context.drawCenteredString(font,
                    Component.literal(view.error()),
                    center, 162, 0xFFFF8080);
        }
    }

    private static String worldgenLabel() {
        int mapping = ClientRingState.terrainNoiseMapping();
        return "Worldgen: " + RingTerrainNoiseMapping.diagnosticName(mapping)
                + " (" + mapping + ")";
    }
}
