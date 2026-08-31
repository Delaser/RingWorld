package dev.ringworld.client;

import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldCreationUiModel;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWorldGenerationSettings;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Pre-creation editor and cost preview for immutable RingWorld layout. */
public final class RingWorldCreationScreen extends Screen {
    private static final int COMPACT_HEIGHT = 360;
    private static final int PANEL_MAX_WIDTH = 536;
    private static final int PANEL_REGULAR_HEIGHT = 336;
    private static final int PANEL_COMPACT_HEIGHT = 262;
    private static final int PANEL_COLOR = 0xD0101116;
    private static final int PANEL_BORDER_COLOR = 0xFF606872;
    private static final int FACTS_COLOR = 0xA008090C;
    private static final int FACTS_BORDER_COLOR = 0xFF343A42;
    private static final int SECTION_COLOR = 0xFFE0B860;
    private static final int LABEL_COLOR = 0xFFB8BDC5;
    private static final int VALUE_COLOR = 0xFFE3E7EC;
    private static final int ACCENT_COLOR = 0xFF74C7EC;
    private static final int WARNING_COLOR = 0xFFFFC857;
    private static final int ERROR_COLOR = 0xFFFF7070;
    private final Screen parent;
    private EditBox circumferenceField;
    private EditBox widthField;
    private EditBox wallHeightField;
    private boolean requestOceanMonument;
    private RingWallStyle wallStyle;
    private RingSkyProfile.Backdrop skyBackdrop;
    private RingSkyProfile.LightSource sunStyle;
    private RingWorldGenerationSettings generationSettings;
    private Button smallButton;
    private Button mediumButton;
    private Button largeButton;
    private Button savedConfigButton;
    private Button monumentButton;
    private Button wallPresetButton;
    private Button skyBackdropButton;
    private Button sunStyleButton;
    private Button generationButton;
    private Button applyButton;
    private Button seedPreviewButton;
    @Nullable private RingDimensionReport report;
    private java.util.List<String> validationMessages = java.util.List.of();

    public RingWorldCreationScreen(Screen parent) {
        super(Component.literal("Create a RingWorld"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RingWorldConfig config = RingWorldConfig.load();
        String draftCircumference = circumferenceField == null
                ? Integer.toString(config.circumferenceBlocks()) : circumferenceField.getValue();
        String draftWidth = widthField == null
                ? Integer.toString(config.widthBlocks()) : widthField.getValue();
        String draftWallHeight = wallHeightField == null
                ? Integer.toString(config.wallHeightBlocks()) : wallHeightField.getValue();
        boolean draftMonument = circumferenceField == null
                ? config.requestOceanMonument() : requestOceanMonument;
        RingWallStyle draftWallStyle = circumferenceField == null
                ? config.wallStyle() : wallStyle;
        RingSkyProfile.Backdrop draftSkyBackdrop = circumferenceField == null
                ? config.skyProfile().backdrop() : skyBackdrop;
        RingSkyProfile.LightSource draftSunStyle = circumferenceField == null
                ? config.skyProfile().lightSource() : sunStyle;
        RingWorldGenerationSettings draftGenerationSettings = circumferenceField == null
                ? config.generationSettings() : generationSettings;
        Layout layout = layout();
        int presetGap = 4;
        int presetWidth = (layout.contentWidth() - presetGap * 2) / 3;
        smallButton = addRenderableWidget(Button.builder(Component.literal("Small"),
                button -> setPreset(RingWorldCreationUiModel.SMALL))
                .bounds(layout.contentLeft(), layout.presetY(), presetWidth, 20).build());
        mediumButton = addRenderableWidget(Button.builder(Component.literal("Medium"),
                button -> setPreset(RingWorldCreationUiModel.MEDIUM))
                .bounds(layout.contentLeft() + presetWidth + presetGap,
                        layout.presetY(), presetWidth, 20).build());
        largeButton = addRenderableWidget(Button.builder(Component.literal("Large"),
                button -> setPreset(RingWorldCreationUiModel.LARGE))
                .bounds(layout.contentLeft() + (presetWidth + presetGap) * 2,
                        layout.presetY(), presetWidth, 20).build());

        int fieldGap = 6;
        int fieldWidth = (layout.contentWidth() - fieldGap * 2) / 3;
        circumferenceField = numericField(layout.contentLeft(), layout.fieldY(), fieldWidth,
                "Around",
                draftCircumference);
        widthField = numericField(layout.contentLeft() + fieldWidth + fieldGap,
                layout.fieldY(), fieldWidth, "Across",
                draftWidth);
        wallHeightField = numericField(layout.contentLeft() + (fieldWidth + fieldGap) * 2,
                layout.fieldY(), fieldWidth, "Rim height",
                draftWallHeight);
        requestOceanMonument = draftMonument;
        wallStyle = draftWallStyle;
        skyBackdrop = draftSkyBackdrop;
        sunStyle = draftSunStyle;
        generationSettings = draftGenerationSettings;

        int optionGap = 4;
        int optionWidth = (layout.contentWidth() - optionGap * 5) / 6;
        wallPresetButton = addRenderableWidget(Button.builder(wallPresetMessage(),
                button -> RingMinecraftClientAccess.setScreen(minecraft,
                        new RingWallStyleScreen(this, wallStyle, selected -> {
                            wallStyle = selected;
                            RingMinecraftClientAccess.setScreen(minecraft, this);
                        })))
                .bounds(layout.contentLeft(), layout.optionY(), optionWidth, 20).build());
        skyBackdropButton = addRenderableWidget(Button.builder(skyBackdropMessage(), button -> {
            skyBackdrop = skyBackdrop.next();
            button.setMessage(skyBackdropMessage());
        }).bounds(layout.contentLeft() + optionWidth + optionGap,
                layout.optionY(), optionWidth, 20).build());
        sunStyleButton = addRenderableWidget(Button.builder(sunStyleMessage(), button -> {
            sunStyle = sunStyle.next();
            button.setMessage(sunStyleMessage());
        }).bounds(layout.contentLeft() + (optionWidth + optionGap) * 2,
                layout.optionY(), optionWidth, 20).build());
        generationButton = addRenderableWidget(Button.builder(generationMessage(), button ->
                RingMinecraftClientAccess.setScreen(minecraft,
                        new RingWorldGenerationScreen(this, report == null ? null : report.geometry(),
                                generationSettings, selected -> {
                            generationSettings = selected;
                            RingMinecraftClientAccess.setScreen(minecraft, this);
                        })))
                .bounds(layout.contentLeft() + (optionWidth + optionGap) * 3,
                        layout.optionY(), optionWidth, 20).build());
        monumentButton = addRenderableWidget(Button.builder(monumentMessage(),
                button -> {
                    requestOceanMonument = !requestOceanMonument;
                    button.setMessage(monumentMessage());
                })
                .bounds(layout.contentLeft() + (optionWidth + optionGap) * 4,
                        layout.optionY(), optionWidth, 20).build());
        savedConfigButton = addRenderableWidget(Button.builder(Component.literal("Reset"),
                button -> restoreSavedConfig())
                .bounds(layout.contentLeft() + (optionWidth + optionGap) * 5,
                        layout.optionY(), optionWidth, 20).build());

        int actionGap = 6;
        int actionWidth = (layout.contentWidth() - actionGap * 2) / 3;
        applyButton = addRenderableWidget(Button.builder(Component.literal("Use layout"),
                button -> apply())
                .bounds(layout.contentLeft(), layout.actionY(), actionWidth, 20).build());
        seedPreviewButton = addRenderableWidget(Button.builder(Component.literal("Seed preview"),
                button -> openSeedPreview())
                .bounds(layout.contentLeft() + actionWidth + actionGap,
                        layout.actionY(), actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                button -> onClose())
                .bounds(layout.contentLeft() + (actionWidth + actionGap) * 2,
                        layout.actionY(), actionWidth, 20).build());
        updateReport();
    }

    private EditBox numericField(int x, int y, int width, String label, String value) {
        EditBox field = new EditBox(
                font, x, y, width, 20, Component.literal(label));
        field.setMaxLength(9);
        field.setValue(value);
        field.setResponder(ignored -> updateReport());
        return addRenderableWidget(field);
    }

    private void setPreset(int circumference, int width, int wallHeight) {
        circumferenceField.setValue(Integer.toString(circumference));
        widthField.setValue(Integer.toString(width));
        wallHeightField.setValue(Integer.toString(wallHeight));
        updateReport();
    }

    private void setPreset(RingWorldCreationUiModel.Preset preset) {
        setPreset(preset.circumferenceBlocks(), preset.widthBlocks(), preset.wallHeightBlocks());
    }

    private void restoreSavedConfig() {
        RingWorldConfig saved = RingWorldConfig.load();
        setPreset(saved.circumferenceBlocks(), saved.widthBlocks(), saved.wallHeightBlocks());
        requestOceanMonument = saved.requestOceanMonument()
                && report != null && report.isValid()
                && RingWorldCreationUiModel.monumentAvailable(report.geometry());
        wallStyle = saved.wallStyle();
        skyBackdrop = saved.skyProfile().backdrop();
        sunStyle = saved.skyProfile().lightSource();
        generationSettings = saved.generationSettings();
        wallPresetButton.setMessage(wallPresetMessage());
        skyBackdropButton.setMessage(skyBackdropMessage());
        sunStyleButton.setMessage(sunStyleMessage());
        generationButton.setMessage(generationMessage());
        updateReport();
        monumentButton.setMessage(monumentMessage());
    }

    private Component wallPresetMessage() {
        String label = RingWallStyle.Preset.find(wallStyle)
                .map(RingWallStyle.Preset::label).orElse("Custom");
        if (layout().compact()) {
            label = switch (RingWallStyle.Preset.matching(wallStyle)) {
                case WEATHERED_FORTIFICATION -> "Wthr";
                case ANCIENT_MASONRY -> "Anc";
                case NATURAL_ESCARPMENT -> "Rock";
                case RING_ALLOY -> "Alloy";
                case INDUSTRIAL_SUPERSTRUCTURE -> "Ind";
                case OVERGROWN_RUIN -> "Ruin";
                case CLEAN_MONOLITH -> "Mono";
                case NETHER_FORTRESS -> "Neth";
                case OBSIDIAN_BASTION -> "Obsi";
                case TIMBER_RAMPART -> "Wood";
            };
            if (RingWallStyle.Preset.find(wallStyle).isEmpty()) label = "Custom";
        }
        return Component.literal("Rim: " + label);
    }

    private Component skyBackdropMessage() {
        String label = layout().compact() && skyBackdrop == RingSkyProfile.Backdrop.ATMOSPHERE
                ? "Atm" : skyBackdrop.label();
        return Component.literal(layout().compact() ? "Sky:" + label : "Sky: " + label);
    }

    private Component sunStyleMessage() {
        String label = layout().compact() ? switch (sunStyle) {
            case SMALL -> "Sm";
            case LARGE -> "Lg";
            case NONE -> "No";
        } : sunStyle.label();
        return Component.literal(layout().compact() ? "Sun:" + label : "Sun: " + label);
    }

    private RingSkyProfile skyProfile() {
        return new RingSkyProfile(skyBackdrop, sunStyle, RingSkyProfile.FORMAT_VERSION);
    }

    private Component generationMessage() {
        if (layout().compact()) return Component.literal("Gen");
        String label = generationSettings.layout().label();
        if (generationSettings.continuousRiver()) label += "+R";
        if (generationSettings.moreStructures()) label += "+S";
        return Component.literal("Gen: " + label);
    }

    private Component monumentMessage() {
        if (report != null && report.isValid()
                && !RingWorldCreationUiModel.monumentAvailable(report.geometry())) {
            return Component.literal(layout().compact()
                    ? "Mon: N/A" : "Monument: needs 160 width");
        }
        return Component.literal(layout().compact()
                ? "Mon: " + (requestOceanMonument ? "On" : "Off")
                : RingWorldCreationUiModel.monumentChoice(requestOceanMonument));
    }

    private void updateReport() {
        if (applyButton == null || circumferenceField == null) return;
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                circumferenceField.getValue(), widthField.getValue(), wallHeightField.getValue(),
                wallStyle, generationSettings);
        report = validation.report();
        validationMessages = validation.messages();
        applyButton.active = validation.canApply();
        if (seedPreviewButton != null) seedPreviewButton.active = validation.canApply();
        if (monumentButton != null && report != null && report.isValid()) {
            boolean available = RingWorldCreationUiModel.monumentAvailable(report.geometry());
            if (!available) requestOceanMonument = false;
            monumentButton.active = available;
            monumentButton.setMessage(monumentMessage());
        } else if (monumentButton != null) {
            monumentButton.active = true;
            monumentButton.setMessage(monumentMessage());
        }
    }

    private void openSeedPreview() {
        if (report == null || !report.isValid() || !(parent instanceof LayoutButtonOwner owner)) {
            return;
        }
        RingMinecraftClientAccess.setScreen(minecraft,
                new RingSeedPreviewScreen(this, owner, report.geometry(), generationSettings));
    }

    private void apply() {
        if (report == null || !report.isValid()) return;
        RingDimensionReport confirmedReport = report;
        RingMinecraftClientAccess.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) {
                persistLayout(confirmedReport);
            } else {
                RingMinecraftClientAccess.setScreen(minecraft, this);
            }
        }, Component.literal("Use this RingWorld?"),
                Component.literal(RingWorldCreationUiModel.confirmationCopy(
                        confirmedReport, requestOceanMonument, wallStyle, skyProfile(),
                        generationSettings)),
                Component.literal("Use layout"), Component.literal("Back")));
    }

    private void persistLayout(RingDimensionReport confirmedReport) {
        try {
            RingWorldConfig.saveBootstrapLayout(
                    confirmedReport.geometry().widthBlocks(),
                    confirmedReport.geometry().circumferenceBlocks(),
                    confirmedReport.wallHeightBlocks(), wallStyle, skyProfile(),
                    generationSettings, requestOceanMonument);
            if (parent instanceof LayoutButtonOwner owner) {
                owner.ringworld$refreshLayoutButton();
            }
            RingMinecraftClientAccess.setScreen(minecraft, parent);
        } catch (RuntimeException exception) {
            validationMessages = java.util.List.of(exception.getMessage());
            applyButton.active = false;
            RingMinecraftClientAccess.setScreen(minecraft, this);
        }
    }

    @Override
    public void onClose() {
        RingMinecraftClientAccess.setScreen(minecraft, parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Screen.extractRenderStateWithTooltipAndSubtitles already owns the
        // panorama blur. This card adds hierarchy without requesting another
        // background pass.
        Layout layout = layout();
        context.fill(layout.panelLeft(), layout.panelTop(), layout.panelRight(),
                layout.panelBottom(), PANEL_COLOR);
        context.outline(layout.panelLeft(), layout.panelTop(), layout.panelWidth(),
                layout.panelHeight(), PANEL_BORDER_COLOR);
        context.fill(layout.contentLeft() - 4, layout.factsY() - 5,
                layout.contentRight() + 4, layout.factsBottom(), FACTS_COLOR);
        context.outline(layout.contentLeft() - 4, layout.factsY() - 5,
                layout.contentWidth() + 8, layout.factsBottom() - layout.factsY() + 5,
                FACTS_BORDER_COLOR);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        context.centeredText(font, title, width / 2, layout.titleY(), 0xFFFFFFFF);
        if (!layout.compact()) {
            context.centeredText(font, Component.literal("Choose a scale, then tune it."),
                    width / 2, layout.subtitleY(), LABEL_COLOR);
        }
        drawSectionLabel(context, "PRESET", layout.presetLabelY());
        drawSectionLabel(context, "CUSTOM", layout.customLabelY());
        drawFieldLabel(context, "Around", layout.contentLeft(), layout.fieldLabelY());
        int fieldGap = 6;
        int fieldWidth = (layout.contentWidth() - fieldGap * 2) / 3;
        drawFieldLabel(context, "Across", layout.contentLeft() + fieldWidth + fieldGap,
                layout.fieldLabelY());
        drawFieldLabel(context, "Rim", layout.contentLeft() + (fieldWidth + fieldGap) * 2,
                layout.fieldLabelY());
        drawSectionLabel(context, "RING MATHS", layout.factsLabelY());
        drawPresetAccent(context, layout, RingWorldCreationUiModel.SMALL, 0);
        drawPresetAccent(context, layout, RingWorldCreationUiModel.MEDIUM, 1);
        drawPresetAccent(context, layout, RingWorldCreationUiModel.LARGE, 2);

        int y = layout.factsY();
        int lineStep = layout.lineStep();
        if (!validationMessages.isEmpty()) {
            drawValidationMessages(context, layout);
        } else if (report != null) {
            java.util.List<String> metricLines =
                    RingWorldCreationUiModel.Validation.metricLines(report);
            for (int index = 0; index < metricLines.size(); index++) {
                boolean advisory = index == metricLines.size() - 1
                        && (report.hasHighGenerationCost()
                            || !RingWorldCreationUiModel.monumentAvailable(report.geometry()));
                int color = advisory
                        ? WARNING_COLOR : VALUE_COLOR;
                context.text(font, Component.literal(metricLines.get(index)),
                        layout.contentLeft(), y + lineStep * index, color);
            }
        }
    }

    private void drawValidationMessages(GuiGraphicsExtractor context, Layout layout) {
        int y = layout.factsY();
        int capacity = Math.max(1, (layout.factsBottom() - y) / layout.lineStep());
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        for (String message : validationMessages) {
            lines.addAll(font.split(Component.literal("• " + message), layout.contentWidth()));
        }
        int shown = Math.min(capacity, lines.size());
        for (int index = 0; index < shown; index++) {
            context.text(font, lines.get(index), layout.contentLeft(),
                    y + layout.lineStep() * index, ERROR_COLOR);
        }
        if (lines.size() > shown && shown > 0) {
            context.text(font, Component.literal("…"), layout.contentRight() - 8,
                    y + layout.lineStep() * (shown - 1), ERROR_COLOR);
        }
    }

    private void drawSectionLabel(GuiGraphicsExtractor context, String value, int y) {
        context.text(font, Component.literal(value), layout().contentLeft(), y, SECTION_COLOR);
    }

    private void drawFieldLabel(GuiGraphicsExtractor context, String value, int x, int y) {
        context.text(font, Component.literal(value), x, y, LABEL_COLOR);
    }

    private void drawPresetAccent(
            GuiGraphicsExtractor context, Layout layout,
            RingWorldCreationUiModel.Preset preset, int index) {
        if (!matchesPreset(preset)) return;
        int gap = 4;
        int presetWidth = (layout.contentWidth() - gap * 2) / 3;
        int x = layout.contentLeft() + index * (presetWidth + gap);
        context.fill(x + 2, layout.presetY() + 18, x + presetWidth - 2,
                layout.presetY() + 20, ACCENT_COLOR);
    }

    private boolean matchesPreset(RingWorldCreationUiModel.Preset preset) {
        return circumferenceField != null
                && circumferenceField.getValue().equals(Integer.toString(preset.circumferenceBlocks()))
                && widthField.getValue().equals(Integer.toString(preset.widthBlocks()))
                && wallHeightField.getValue().equals(Integer.toString(preset.wallHeightBlocks()));
    }

    private Layout layout() {
        boolean compact = height < COMPACT_HEIGHT;
        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(304, width - 16));
        int targetHeight = compact ? PANEL_COMPACT_HEIGHT : PANEL_REGULAR_HEIGHT;
        int panelHeight = Math.min(targetHeight, Math.max(262, height - 8));
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = Math.max(4, (height - panelHeight) / 2);
        return Layout.create(panelLeft, panelTop, panelWidth, panelHeight, compact);
    }

    private record Layout(
            int panelLeft, int panelTop, int panelWidth, int panelHeight, boolean compact,
            int contentLeft, int contentWidth, int titleY, int subtitleY,
            int presetLabelY, int presetY, int customLabelY, int fieldLabelY, int fieldY,
            int optionY, int factsLabelY, int factsY, int factsBottom, int actionY,
            int lineStep) {
        static Layout create(int left, int top, int width, int height, boolean compact) {
            int contentLeft = left + 8;
            int contentWidth = width - 16;
            if (compact) {
                return new Layout(left, top, width, height, true,
                        contentLeft, contentWidth, top + 7, top + 7,
                        top + 25, top + 36, top + 61, top + 72, top + 82,
                        top + 106, top + 132, top + 145, top + 230,
                        top + height - 26, 10);
            }
            return new Layout(left, top, width, height, false,
                    contentLeft, contentWidth, top + 12, top + 27,
                    top + 49, top + 60, top + 89, top + 101, top + 112,
                    top + 140, top + 169, top + 183, top + 292,
                    top + height - 28, 13);
        }

        int panelRight() { return panelLeft + panelWidth; }
        int panelBottom() { return panelTop + panelHeight; }
        int contentRight() { return contentLeft + contentWidth; }
    }

    /*
     * Package-visible, test-only paths for the opt-in graphical creation UI
     * fixture. They deliberately enter through the actual widgets: the
     * responder recomputes validation and the buttons retain their ordinary
     * callbacks. No production path invokes these methods.
     */
    void ringworld$automationPressSmall() {
        smallButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationPressMedium() {
        mediumButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationPressLarge() {
        largeButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationPressSavedConfig() {
        savedConfigButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationOpenRimEditor() {
        wallPresetButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationOpenSeedPreview() {
        seedPreviewButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationOpenGeneration() {
        generationButton.onPress(AutomationInput.INSTANCE);
    }

    RingWorldGenerationSettings ringworld$automationGenerationSettings() {
        return generationSettings;
    }

    void ringworld$automationSetGenerationSettings(RingWorldGenerationSettings settings) {
        generationSettings = settings;
        generationButton.setMessage(generationMessage());
        updateReport();
    }

    void ringworld$automationCycleSky() {
        skyBackdropButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationCycleSun() {
        sunStyleButton.onPress(AutomationInput.INSTANCE);
    }

    boolean ringworld$automationHasWallStyle(RingWallStyle expected) {
        return wallStyle.equals(expected);
    }

    void ringworld$automationSetLayout(int circumference, int width, int wallHeight) {
        circumferenceField.setValue(Integer.toString(circumference));
        widthField.setValue(Integer.toString(width));
        wallHeightField.setValue(Integer.toString(wallHeight));
    }

    void ringworld$automationToggleMonument() {
        monumentButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationApply() {
        applyButton.onPress(AutomationInput.INSTANCE);
    }

    boolean ringworld$automationHasLayout(int circumference, int width, int wallHeight) {
        return circumferenceField.getValue().equals(Integer.toString(circumference))
                && widthField.getValue().equals(Integer.toString(width))
                && wallHeightField.getValue().equals(Integer.toString(wallHeight));
    }

    boolean ringworld$automationCanApply() {
        return applyButton.active;
    }

    int ringworld$automationValidationMessageCount() {
        return validationMessages.size();
    }

    boolean ringworld$automationMonumentRequested() {
        return requestOceanMonument;
    }

    boolean ringworld$automationMonumentAvailable() {
        return monumentButton.active;
    }

    java.util.List<String> ringworld$automationMetricLines() {
        return report == null ? java.util.List.of()
                : RingWorldCreationUiModel.Validation.metricLines(report);
    }

    /** Shared non-pointer input for invoking a Button's normal onPress path. */
    public enum AutomationInput implements InputWithModifiers {
        INSTANCE;

        @Override public int input() { return 0; }
        @Override public int modifiers() { return 0; }
    }

    /** Parent-screen hook kept UI-local so accepting a layout refreshes its summary. */
    public interface LayoutButtonOwner {
        void ringworld$refreshLayoutButton();
        String ringworld$seedText();
        long ringworld$resolvedSeed();
        void ringworld$setSeedText(String seed);
        net.minecraft.client.gui.screens.worldselection.WorldCreationContext
                ringworld$creationContext();

        /** True after the real Create World footer widget has been initialized. */
        boolean ringworld$layoutButtonReadyForAutomation();

        /** Opens the actual footer button for the menu-only graphical fixture. */
        void ringworld$openLayoutEditorForAutomation();

        /** Reads the actual footer label after an accepted confirmation. */
        Component ringworld$layoutButtonMessageForAutomation();
    }
}
