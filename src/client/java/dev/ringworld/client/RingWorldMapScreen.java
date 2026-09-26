package dev.ringworld.client;

import dev.ringworld.RingWorldBuildIdentity;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationStatus;
import dev.ringworld.world.AtlasPregenerationView;
import dev.ringworld.world.RingLodQuality;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingTerrainPreviewHud;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Non-pausing generation progress and player-local Atlas display controls. */
public final class RingWorldMapScreen extends Screen {
    private enum Page { GENERATION, DISPLAY }
    private Page page = Page.GENERATION;
    private boolean showDetails;
    private long requestedWorldHash = Long.MIN_VALUE;
    private String lastActions = "";

    public RingWorldMapScreen(Screen parent) {
        super(Component.literal("RingWorld"));
    }

    public static boolean canOpen() {
        return ClientRingState.geometry() != null && AtlasPregenerationClientState.canRequestCurrent();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() { rebuild(); }

    @Override
    public void tick() {
        Optional<AtlasPregenerationStatus> current = AtlasPregenerationClientState.status();
        if (current.isEmpty()) AtlasPregenerationClientState.requestCurrent();
        if (current.isPresent() && current.get().worldHash() != requestedWorldHash) {
            requestedWorldHash = current.get().worldHash();
            AtlasPregenerationClientState.request(requestedWorldHash);
        }
        String actions = current.map(value -> AtlasPregenerationView.from(value).actions().toString())
                .orElse("loading");
        if (!actions.equals(lastActions)) rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int panel = Math.min(360, width - 16), left = (width - panel) / 2;
        int half = (panel - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Generation"
                        + (page == Page.GENERATION ? " *" : "")), button -> {
                    page = Page.GENERATION; rebuild();
                }).bounds(left, 36, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Display"
                        + (page == Page.DISPLAY ? " *" : "")), button -> {
                    page = Page.DISPLAY; rebuild();
                }).bounds(left + half + 6, 36, half, 20).build());
        if (page == Page.DISPLAY) {
            RingLodQuality[] levels = RingLodQuality.values();
            int gap = 4, each = (panel - gap * 2) / 3;
            for (int i = 0; i < levels.length; i++) {
                RingLodQuality level = levels[i];
                addRenderableWidget(Button.builder(Component.literal(level.label()
                                + (RingClientLodTuning.quality() == level ? " *" : "")), button -> {
                            RingClientLodTuning.select(level); rebuild();
                        }).bounds(left + i * (each + gap), 85, each, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal(
                            showDetails ? "Hide detail information" : "Detail information"),
                    button -> { showDetails = !showDetails; rebuild(); })
                    .bounds(left, 156, panel, 20).build());
        } else {
            Optional<AtlasPregenerationStatus> current = AtlasPregenerationClientState.status();
            if (current.isPresent()) {
                AtlasPregenerationStatus status = current.get();
                requestedWorldHash = status.worldHash();
                AtlasPregenerationClientState.request(status.worldHash());
                AtlasPregenerationView view = AtlasPregenerationView.from(status);
                lastActions = view.actions().toString();
                int y = Math.min(height - 92, 150);
                if (!(showDetails && height < 320)) {
                if (view.actions().contains(AtlasPregenerationAction.START)) {
                    addRenderableWidget(Button.builder(Component.literal(status.progress().state().isTerminal()
                                    ? "Retry Generate Entire Ring" : "Generate Entire Ring"),
                            button -> confirmStart(status)).bounds(left, y, panel, 20).build());
                    y += 23;
                }
                if (view.actions().contains(AtlasPregenerationAction.PAUSE)) {
                    addRenderableWidget(controlButton("Pause", AtlasPregenerationAction.PAUSE,
                            status, left, y, panel)); y += 23;
                }
                if (view.actions().contains(AtlasPregenerationAction.RESUME)) {
                    addRenderableWidget(controlButton("Resume", AtlasPregenerationAction.RESUME,
                            status, left, y, panel)); y += 23;
                }
                if (view.actions().contains(AtlasPregenerationAction.CANCEL)) {
                    addRenderableWidget(Button.builder(Component.literal("Stop generation..."),
                            button -> confirmStop(status)).bounds(left, y, panel, 20).build());
                }
                }
            } else lastActions = "loading";
            addRenderableWidget(Button.builder(Component.literal(
                            showDetails ? "Hide technical details" : "Technical details"),
                    button -> { showDetails = !showDetails; rebuild(); })
                    .bounds(left, height - 56, panel, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Return to game"), button -> onClose())
                .bounds(width / 2 - 85, height - 27, 170, 20).build());
    }

    private Button controlButton(String label, AtlasPregenerationAction action,
                                 AtlasPregenerationStatus status, int x, int y, int w) {
        return Button.builder(Component.literal(label), button -> {
            AtlasPregenerationClientState.control(status.worldHash(), action);
            rebuild();
        }).bounds(x, y, w, 20).build();
    }

    private void confirmStart(AtlasPregenerationStatus status) {
        RingMinecraftClientAccess.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.START);
            RingMinecraftClientAccess.setScreen(minecraft, this);
        }, Component.literal("Generate Entire Ring?"),
                Component.literal("This generates and saves %,d canonical terrain chunks. "
                        .formatted(status.canonicalChunks())
                        + "It can take time and create real region files on disk."),
                Component.literal("Generate"), Component.literal("Back")));
    }

    private void confirmStop(AtlasPregenerationStatus status) {
        RingMinecraftClientAccess.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) AtlasPregenerationClientState.control(status.worldHash(), AtlasPregenerationAction.CANCEL);
            RingMinecraftClientAccess.setScreen(minecraft, this);
        }, Component.literal("Stop generation?"),
                Component.literal("Already generated chunks remain saved."),
                Component.literal("Stop"), Component.literal("Back")));
    }

    void openDisplayForAutomation() { page = Page.DISPLAY; rebuild(); }
    void openGenerationForAutomation() { page = Page.GENERATION; rebuild(); }
    void toggleTechnicalForAutomation() { showDetails = !showDetails; rebuild(); }

    void cycleDetailForAutomation() {
        RingClientLodTuning.select(RingClientLodTuning.quality().next());
        if (page == Page.DISPLAY) rebuild();
    }
    void openStartConfirmationForAutomation() {
        AtlasPregenerationClientState.status().ifPresent(this::confirmStart);
    }
    String buildLabelForAutomation() { return RingWorldBuildIdentity.displayLabel(); }
    String worldgenLabelForAutomation() {
        int mapping = ClientRingState.terrainNoiseMapping();
        return "Worldgen: " + RingTerrainNoiseMapping.diagnosticName(mapping) + " (" + mapping + ")";
    }

    @Override
    public void onClose() { RingMinecraftClientAccess.setScreen(minecraft, null); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int center = width / 2, left = center - Math.min(360, width - 16) / 2;
        g.centeredText(font, title, center, 11, 0xFFFFFFFF);
        if (page == Page.DISPLAY) {
            g.text(font, Component.literal("Distant terrain detail"), left, 69, 0xFFE5ECE5);
            g.text(font, Component.literal("Applies to this player immediately."), left, 116, 0xFFB4C4BA);
            g.text(font, Component.literal("Other players can choose differently."), left, 129, 0xFFB4C4BA);
            if (showDetails) {
                g.text(font, Component.literal("Low 8-block; Medium 2-block; High 1-block samples."),
                        left, 184, 0xFFB4C4BA);
                g.text(font, Component.literal("Source Atlas: one sample per block."),
                        left, 197, 0xFFB4C4BA);
            }
            return;
        }
        Optional<AtlasPregenerationStatus> current = AtlasPregenerationClientState.status();
        if (current.isEmpty()) {
            g.centeredText(font, Component.literal("Requesting generation status..."),
                    center, 80, 0xFFD0D0D0);
            return;
        }
        AtlasPregenerationStatus status = current.get();
        AtlasPregenerationView view = AtlasPregenerationView.from(status);
        if (showDetails && height < 320) {
            g.text(font, Component.literal(worldgenLabelForAutomation()), left, 69, 0xFFB4C4BA);
            g.text(font, Component.literal(view.cells()), left, 86, 0xFFB4C4BA);
            g.text(font, Component.literal("Elapsed: " + view.elapsed()), left, 103, 0xFFB4C4BA);
            g.text(font, Component.literal(view.rate()), left, 120, 0xFFB4C4BA);
            g.text(font, Component.literal(RingWorldBuildIdentity.displayLabel()),
                    left, 137, 0xFFB4C4BA);
            var entries = RingTerrainPreviewHud.entries(ClientRingState.terrainPreviewStage());
            for (int i = 0; i < Math.min(entries.size(), Math.max(0, (height - 205) / 12)); i++) {
                var entry = entries.get(i);
                g.text(font, Component.literal(entry.name() + ": " + entry.state().label()),
                        left, 155 + i * 12, 0xFFB4C4BA);
            }
            return;
        }
        g.text(font, Component.literal("Entire-ring generation · " + view.dimensions()),
                left, 65, 0xFFE5ECE5);
        g.text(font, Component.literal(view.state() + " · " + view.chunks()),
                left, 79, 0xFFA6CC91);
        int barWidth = Math.min(360, width - 16);
        g.fill(left, 97, left + barWidth, 105, 0xFF253638);
        long total = status.progress().totalCells();
        int done = total == 0 ? barWidth : (int)(barWidth * Math.min(total,
                status.progress().presentCells()) / total);
        g.fill(left, 97, left + done, 105, 0xFFA6CC91);
        g.text(font, Component.literal("ETA: " + view.eta()), left, 114, 0xFFB4C4BA);
        if (!status.canControl())
            g.text(font, Component.literal("Read-only: ask the owner or gamemaster."),
                    left, 128, 0xFFFFD060);
        else if (!view.error().isEmpty())
            g.text(font, Component.literal(view.error()), left, 128, 0xFFFF9080);
        else
            g.text(font, Component.literal("Generated chunks remain saved."),
                    left, 128, 0xFFB4C4BA);
        if (showDetails && height >= 320) {
            g.text(font, Component.literal(worldgenLabelForAutomation()), left, 208, 0xFFB4C4BA);
            g.text(font, Component.literal(view.cells()), left, 220, 0xFFB4C4BA);
            g.text(font, Component.literal("Elapsed: " + view.elapsed() + " · " + view.rate()),
                    left, 232, 0xFFB4C4BA);
            g.text(font, Component.literal(RingWorldBuildIdentity.displayLabel()),
                    left, 244, 0xFFB4C4BA);
            var entries = RingTerrainPreviewHud.entries(ClientRingState.terrainPreviewStage());
            int n = Math.min(entries.size(), Math.max(0, (height - 56 - 257) / 12));
            for (int i = 0; i < n; i++) {
                var entry = entries.get(i);
                g.text(font, Component.literal(entry.name() + ": " + entry.state().label()),
                        left, 257 + i * 12, 0xFFB4C4BA);
            }
        }
    }
}
