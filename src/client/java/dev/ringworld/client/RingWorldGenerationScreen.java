package dev.ringworld.client;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldGenerationSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/** New-world terrain options; render detail is a separate client choice. */
public final class RingWorldGenerationScreen extends Screen {
    private static final int PANEL_COLOR = 0xE0101116;
    private static final int BORDER_COLOR = 0xFF606872;
    private static final int LABEL_COLOR = 0xFFB8BDC5;
    private static final int ACCENT_COLOR = 0xFF74C7EC;

    private final Screen parent;
    private final @Nullable RingGeometry geometry;
    private final Consumer<RingWorldGenerationSettings> apply;
    private RingWorldGenerationSettings settings;
    private Button riverButton;
    private Button structuresButton;

    public RingWorldGenerationScreen(
            Screen parent, @Nullable RingGeometry geometry,
            RingWorldGenerationSettings initial,
            Consumer<RingWorldGenerationSettings> apply) {
        super(Component.literal("World generation"));
        this.parent = parent;
        this.geometry = geometry;
        this.settings = initial;
        this.apply = apply;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        riverButton = addRenderableWidget(Button.builder(riverMessage(), button -> {
            settings = settings.withContinuousRiver(!settings.continuousRiver());
            refresh();
        }).bounds(layout.left() + 12, layout.top() + 38, layout.width() - 24, 20).build());
        structuresButton = addRenderableWidget(Button.builder(structuresMessage(), button -> {
            settings = settings.withMoreStructures(!settings.moreStructures());
            refresh();
        }).bounds(layout.left() + 12, layout.top() + 72, layout.width() - 24, 20).build());

        int gap = 8;
        int actionWidth = (layout.width() - 24 - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> apply.accept(settings))
                .bounds(layout.left() + 12, layout.bottom() - 30, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(layout.left() + 12 + actionWidth + gap,
                        layout.bottom() - 30, actionWidth, 20).build());
    }

    private void refresh() {
        riverButton.setMessage(riverMessage());
        structuresButton.setMessage(structuresMessage());
    }

    private Component riverMessage() {
        return Component.literal("Continuous ring river: "
                + (settings.continuousRiver() ? "On" : "Off"));
    }

    private Component structuresMessage() {
        return Component.literal("More structures: "
                + (settings.moreStructures() ? "On" : "Off"));
    }

    @Override
    public void onClose() {
        RingMinecraftClientAccess.setScreen(minecraft, parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float deltaTicks) {
        Layout layout = layout();
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), PANEL_COLOR);
        graphics.outline(layout.left(), layout.top(), layout.width(), layout.height(), BORDER_COLOR);
        super.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
        graphics.centeredText(font, title, width / 2, layout.top() + 12, 0xFFFFFFFF);
        graphics.text(font, Component.literal("A seeded, terrain-integrated water loop."),
                layout.left() + 14, layout.top() + 60, LABEL_COLOR);
        graphics.text(font, Component.literal("Moderately increases eligible landmarks."),
                layout.left() + 14, layout.top() + 94, LABEL_COLOR);
        if (geometry != null) {
            graphics.centeredText(font, Component.literal(resourceSummary()), width / 2,
                    layout.bottom() - 46, ACCENT_COLOR);
        }
    }

    private String resourceSummary() {
        long cells = (long)geometry.circumferenceBlocks() * geometry.widthBlocks();
        return String.format(Locale.ROOT, "Atlas %,d cells (%s) · one sample per block",
                cells, dataSize(cells * RingTerrainAtlas.ESTIMATED_BYTES_PER_CELL));
    }

    private static String dataSize(long bytes) {
        return bytes < 1024L * 1024L
                ? String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
                : String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private Layout layout() {
        int panelWidth = Math.min(470, Math.max(304, width - 16));
        int panelHeight = Math.min(182, Math.max(152, height - 12));
        return new Layout((width - panelWidth) / 2, (height - panelHeight) / 2,
                panelWidth, panelHeight);
    }

    RingWorldGenerationSettings ringworld$automationSettings() {
        return settings;
    }

    void ringworld$automationSelect(RingWorldGenerationSettings selected) {
        settings = selected;
        refresh();
    }

    void ringworld$automationApply() {
        apply.accept(settings);
    }

    private record Layout(int left, int top, int width, int height) {
        int right() { return left + width; }
        int bottom() { return top + height; }
    }
}
