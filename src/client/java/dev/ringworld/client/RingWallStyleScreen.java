package dev.ringworld.client;

import dev.ringworld.world.RingWallStyle;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact preset and advanced editor for the immutable new-world rim style. */
public final class RingWallStyleScreen extends Screen {
    private static final int PANEL_COLOR = 0xE0101116;
    private static final int BORDER_COLOR = 0xFF606872;
    private static final int SECTION_COLOR = 0xFFE0B860;
    private static final int LABEL_COLOR = 0xFFB8BDC5;
    private static final int ERROR_COLOR = 0xFFFF7070;

    private final Screen parent;
    private final Consumer<RingWallStyle> accepted;
    private RingWallStyle draft;
    private RingWallStyle.Palette palette;
    private RingWallStyle.Pattern pattern;
    private EditBox thicknessField;
    private EditBox decayField;
    private Button paletteButton;
    private Button patternButton;
    private Button useButton;
    private String validation = "";

    public RingWallStyleScreen(Screen parent, RingWallStyle initial,
                               Consumer<RingWallStyle> accepted) {
        super(Component.literal("Rim appearance"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.draft = Objects.requireNonNull(initial, "initial");
        this.palette = initial.palette();
        this.pattern = initial.pattern();
        this.accepted = Objects.requireNonNull(accepted, "accepted");
    }

    @Override
    protected void init() {
        String thickness = thicknessField == null
                ? Integer.toString(draft.thicknessBlocks()) : thicknessField.getValue();
        String decay = decayField == null
                ? Integer.toString(draft.decayPercent()) : decayField.getValue();
        // Minecraft reuses the Screen instance while rebuilding widgets after
        // GUI-scale/window changes. Do not let new field responders consult
        // stale widgets from the previous layout during this init pass.
        useButton = null;
        thicknessField = null;
        decayField = null;
        Layout layout = layout();
        RingWallStyle.Preset[] presets = RingWallStyle.Preset.values();
        addPresetRow(layout, presets, 0, 4, layout.presetY());
        addPresetRow(layout, presets, 4, presets.length, layout.presetY() + 24);

        int gap = 5;
        int half = (layout.contentWidth() - gap) / 2;
        paletteButton = addRenderableWidget(Button.builder(paletteMessage(), button -> {
            palette = palette.next();
            button.setMessage(paletteMessage());
            updateDraft();
        }).bounds(layout.contentLeft(), layout.advancedY(), half, 20).build());
        patternButton = addRenderableWidget(Button.builder(patternMessage(), button -> {
            pattern = pattern.next();
            button.setMessage(patternMessage());
            updateDraft();
        }).bounds(layout.contentLeft() + half + gap, layout.advancedY(), half, 20).build());

        thicknessField = numericField(layout.contentLeft(), layout.valuesY(), half,
                "Thickness", thickness);
        decayField = numericField(layout.contentLeft() + half + gap, layout.valuesY(), half,
                "Decay", decay);

        useButton = addRenderableWidget(Button.builder(Component.literal("Use rim"), button -> {
            updateDraft();
            if (validation.isEmpty()) accepted.accept(draft);
        }).bounds(layout.contentLeft(), layout.actionY(), half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(layout.contentLeft() + half + gap, layout.actionY(), half, 20).build());
        updateDraft();
    }

    private void addPresetRow(Layout layout, RingWallStyle.Preset[] presets,
                              int start, int end, int y) {
        int count = end - start;
        int gap = 4;
        int buttonWidth = (layout.contentWidth() - gap * (count - 1)) / count;
        for (int index = start; index < end; index++) {
            RingWallStyle.Preset preset = presets[index];
            int x = layout.contentLeft() + (index - start) * (buttonWidth + gap);
            addRenderableWidget(Button.builder(Component.literal(preset.label()),
                    button -> applyPreset(preset))
                    .bounds(x, y, buttonWidth, 20).build());
        }
    }

    private EditBox numericField(int x, int y, int width, String label, String value) {
        EditBox field = new EditBox(font, x, y, width, 20, Component.literal(label));
        field.setMaxLength(3);
        field.setValue(value);
        field.setResponder(ignored -> updateDraft());
        return addRenderableWidget(field);
    }

    private void applyPreset(RingWallStyle.Preset preset) {
        RingWallStyle selected = preset.style();
        draft = selected;
        palette = selected.palette();
        pattern = selected.pattern();
        // Setting the first field invokes its responder immediately. Keep the
        // selected values local so that intermediate validation cannot replace
        // the preset before the second field is written.
        thicknessField.setValue(Integer.toString(selected.thicknessBlocks()));
        decayField.setValue(Integer.toString(selected.decayPercent()));
        paletteButton.setMessage(paletteMessage());
        patternButton.setMessage(patternMessage());
        updateDraft();
    }

    private void updateDraft() {
        if (useButton == null || thicknessField == null || decayField == null) return;
        try {
            int thickness = Integer.parseInt(thicknessField.getValue().trim());
            int decay = Integer.parseInt(decayField.getValue().trim());
            draft = RingWallStyle.custom(thickness, palette, pattern, decay);
            validation = "";
            useButton.active = true;
        } catch (NumberFormatException exception) {
            validation = "Thickness and decay must be whole numbers.";
            useButton.active = false;
        } catch (IllegalArgumentException exception) {
            validation = exception.getMessage();
            useButton.active = false;
        }
    }

    private Component paletteMessage() {
        return Component.literal("Material: " + palette.label());
    }

    private Component patternMessage() {
        return Component.literal("Pattern: " + pattern.label());
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
        graphics.centeredText(font, title, width / 2, layout.top() + 10, 0xFFFFFFFF);
        graphics.text(font, Component.literal("PRESETS"), layout.contentLeft(),
                layout.presetY() - 11, SECTION_COLOR);
        graphics.text(font, Component.literal("ADVANCED"), layout.contentLeft(),
                layout.advancedY() - 11, SECTION_COLOR);
        graphics.text(font, Component.literal("Thickness 1–32"), layout.contentLeft(),
                layout.valuesY() - 11, LABEL_COLOR);
        graphics.text(font, Component.literal("Decay 0–100%"),
                layout.contentLeft() + (layout.contentWidth() + 5) / 2,
                layout.valuesY() - 11, LABEL_COLOR);
        if (!validation.isEmpty()) {
            graphics.centeredText(font, Component.literal(validation), width / 2,
                    layout.actionY() - 14, ERROR_COLOR);
        } else {
            graphics.centeredText(font, Component.literal(
                    "Top-edge decay only · " + draft.conciseLabel()), width / 2,
                    layout.actionY() - 14, LABEL_COLOR);
        }
    }

    private Layout layout() {
        int panelWidth = Math.min(540, Math.max(304, width - 16));
        int panelHeight = Math.min(278, Math.max(248, height - 8));
        int left = (width - panelWidth) / 2;
        int top = Math.max(4, (height - panelHeight) / 2);
        return new Layout(left, top, panelWidth, panelHeight);
    }

    private record Layout(int left, int top, int width, int height) {
        int right() { return left + width; }
        int bottom() { return top + height; }
        int contentLeft() { return left + 8; }
        int contentWidth() { return width - 16; }
        int presetY() { return top + 43; }
        int advancedY() { return top + 116; }
        int valuesY() { return top + 162; }
        int actionY() { return bottom() - 28; }
    }

    void ringworld$automationApplyPreset(RingWallStyle.Preset preset) {
        applyPreset(preset);
    }

    boolean ringworld$automationHasStyle(RingWallStyle expected) {
        return validation.isEmpty() && draft.equals(expected) && useButton.active;
    }

    RingWallStyle ringworld$automationStyle() {
        return draft;
    }

    void ringworld$automationUse() {
        useButton.onPress(RingWorldCreationScreen.AutomationInput.INSTANCE);
    }
}
