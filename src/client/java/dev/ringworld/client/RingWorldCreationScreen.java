package dev.ringworld.client;

import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldCreationUiModel;
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
    private final Screen parent;
    private EditBox circumferenceField;
    private EditBox widthField;
    private EditBox wallHeightField;
    private boolean requestOceanMonument;
    private Button safeSmallButton;
    private Button productionButton;
    private Button savedConfigButton;
    private Button monumentButton;
    private Button applyButton;
    @Nullable private RingDimensionReport report;
    private java.util.List<String> validationMessages = java.util.List.of();

    public RingWorldCreationScreen(Screen parent) {
        super(Component.literal("RingWorld layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RingWorldConfig config = RingWorldConfig.load();
        int left = this.width / 2 - 100;
        boolean compact = this.height < COMPACT_HEIGHT;
        int firstFieldY = compact ? 24 : 52;
        int fieldStep = compact ? 24 : 34;
        int presetY = compact ? 98 : 150;
        circumferenceField = numericField(left, firstFieldY, "Circumference blocks",
                config.circumferenceBlocks());
        widthField = numericField(left, firstFieldY + fieldStep, "Width blocks",
                config.widthBlocks());
        wallHeightField = numericField(left, firstFieldY + fieldStep * 2,
                "Wall height blocks",
                config.wallHeightBlocks());
        requestOceanMonument = config.requestOceanMonument();

        safeSmallButton = addRenderableWidget(Button.builder(Component.literal("Safe-small test"),
                button -> setPreset(RingWorldCreationUiModel.SAFE_SMALL_TEST))
                .bounds(this.width / 2 - 154, presetY, 150, 20).build());
        productionButton = addRenderableWidget(Button.builder(Component.literal("Production (recommended)"),
                button -> setPreset(RingWorldCreationUiModel.PRODUCTION_RECOMMENDED))
                .bounds(this.width / 2 + 4, presetY, 150, 20).build());
        savedConfigButton = addRenderableWidget(Button.builder(Component.literal("Saved config values"),
                button -> restoreSavedConfig())
                .bounds(this.width / 2 - 100, presetY + 24, 200, 20).build());
        monumentButton = addRenderableWidget(Button.builder(monumentMessage(),
                button -> {
                    requestOceanMonument = !requestOceanMonument;
                    button.setMessage(monumentMessage());
                })
                .bounds(this.width / 2 - 100, presetY + 48, 200, 20).build());

        applyButton = addRenderableWidget(Button.builder(Component.literal("Use for new world"),
                button -> apply())
                .bounds(this.width / 2 - 154, this.height - 34, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                button -> onClose())
                .bounds(this.width / 2 + 4, this.height - 34, 150, 20).build());
        updateReport();
    }

    private EditBox numericField(int x, int y, String label, int value) {
        EditBox field = new EditBox(
                font, x, y, 200, 20, Component.literal(label));
        field.setMaxLength(9);
        field.setValue(Integer.toString(value));
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
        requestOceanMonument = saved.requestOceanMonument();
        monumentButton.setMessage(monumentMessage());
    }

    private Component monumentMessage() {
        return Component.literal(RingWorldCreationUiModel.monumentChoice(requestOceanMonument));
    }

    private void updateReport() {
        if (applyButton == null || circumferenceField == null) return;
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                circumferenceField.getValue(), widthField.getValue(), wallHeightField.getValue());
        report = validation.report();
        validationMessages = validation.messages();
        applyButton.active = validation.canApply();
    }

    private void apply() {
        if (report == null || !report.isValid()) return;
        RingDimensionReport confirmedReport = report;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                persistLayout(confirmedReport);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.literal("Lock RingWorld dimensions?"),
                Component.literal(RingWorldCreationUiModel.confirmationCopy(
                        confirmedReport, requestOceanMonument)),
                Component.literal("Lock and use"), Component.literal("Go back")));
    }

    private void persistLayout(RingDimensionReport confirmedReport) {
        try {
            RingWorldConfig.saveBootstrapLayout(
                    confirmedReport.geometry().widthBlocks(),
                    confirmedReport.geometry().circumferenceBlocks(),
                    confirmedReport.wallHeightBlocks(), requestOceanMonument);
            if (parent instanceof LayoutButtonOwner owner) {
                owner.ringworld$refreshLayoutButton();
            }
            minecraft.setScreen(parent);
        } catch (RuntimeException exception) {
            validationMessages = java.util.List.of(exception.getMessage());
            applyButton.active = false;
            minecraft.setScreen(this);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Screen.extractRenderStateWithTooltipAndSubtitles already extracted
        // this screen's panorama, blur, and darkening layer. Keep this method
        // limited to widgets and foreground text so the frame owns one blur.
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        int center = width / 2;
        boolean compact = this.height < COMPACT_HEIGHT;
        int firstFieldY = compact ? 24 : 52;
        int fieldStep = compact ? 24 : 34;
        context.centeredText(font, title, center, compact ? 6 : 18, 0xFFFFFFFF);
        drawFieldLabel(context,
                compact ? "Circumference" : "Circumference (blocks)",
                firstFieldY, compact);
        drawFieldLabel(context,
                compact ? "Finite width" : "Finite width (blocks)",
                firstFieldY + fieldStep, compact);
        drawFieldLabel(context,
                compact ? "Rim wall height" : "Rim wall height (from Y=-64)",
                firstFieldY + fieldStep * 2, compact);

        int y = compact ? 174 : 232;
        int lineStep = compact ? 10 : 12;
        if (!validationMessages.isEmpty()) {
            int capacity = Math.max(1, (height - 74 - y) / lineStep);
            int shown = Math.min(validationMessages.size(), capacity);
            if (validationMessages.size() > shown) shown--;
            for (int index = 0; index < shown; index++) {
                context.centeredText(font, Component.literal(validationMessages.get(index)), center,
                        y + lineStep * index, 0xFFFF6060);
            }
            if (validationMessages.size() > shown) {
                context.centeredText(font,
                        Component.literal("+" + (validationMessages.size() - shown) + " more issue(s) to fix"),
                        center, y + lineStep * shown, 0xFFFF6060);
            }
        } else if (report != null) {
            java.util.List<String> summaryLines = RingWorldCreationUiModel.Validation.summaryLines(report);
            int capacity = Math.max(1, (height - 74 - y) / lineStep);
            int summaryLimit = compact ? Math.min(3, capacity) : capacity;
            if (!report.warnings().isEmpty()) summaryLimit--;
            int shown = Math.min(summaryLimit, summaryLines.size());
            for (int index = 0; index < shown; index++) {
                drawReportLine(context, y + lineStep * index, summaryLines.get(index));
            }
            if (!report.warnings().isEmpty()) {
                context.centeredText(font, Component.literal(report.warnings().getFirst()), center,
                        y + lineStep * shown, 0xFFFFD060);
            }
        }
        int informationY = height - 74;
        for (int index = 0; index < RingWorldCreationUiModel.MONUMENT_LINES.size(); index++) {
            context.centeredText(font, Component.literal(RingWorldCreationUiModel.MONUMENT_LINES.get(index)),
                    center, informationY + index * 10, 0xFFA0A0A0);
        }
        for (int index = 0; index < RingWorldCreationUiModel.NEXT_NEW_WORLD_LINES.size(); index++) {
            context.centeredText(font, Component.literal(RingWorldCreationUiModel.NEXT_NEW_WORLD_LINES.get(index)),
                    center, informationY + 22 + index * 10, 0xFFFFD060);
        }
    }

    private void drawReportLine(GuiGraphicsExtractor context, int y, String value) {
        context.centeredText(font, Component.literal(value),
                width / 2, y, 0xFFD0D0D0);
    }

    private void drawFieldLabel(
            GuiGraphicsExtractor context, String value, int fieldY, boolean compact) {
        Component label = Component.literal(value);
        int fieldLeft = width / 2 - 100;
        int x = compact ? fieldLeft - font.width(label) - 4 : fieldLeft;
        int y = compact ? fieldY + 6 : fieldY - 12;
        context.text(font, label, x, y, 0xFFA0A0A0);
    }

    /*
     * Package-visible, test-only paths for the opt-in graphical creation UI
     * fixture. They deliberately enter through the actual widgets: the
     * responder recomputes validation and the buttons retain their ordinary
     * callbacks. No production path invokes these methods.
     */
    void ringworld$automationPressSafeSmall() {
        safeSmallButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationPressProduction() {
        productionButton.onPress(AutomationInput.INSTANCE);
    }

    void ringworld$automationPressSavedConfig() {
        savedConfigButton.onPress(AutomationInput.INSTANCE);
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

    /** Shared non-pointer input for invoking a Button's normal onPress path. */
    public enum AutomationInput implements InputWithModifiers {
        INSTANCE;

        @Override public int input() { return 0; }
        @Override public int modifiers() { return 0; }
    }

    /** Parent-screen hook kept UI-local so accepting a layout refreshes its summary. */
    public interface LayoutButtonOwner {
        void ringworld$refreshLayoutButton();

        /** True after the real Create World footer widget has been initialized. */
        boolean ringworld$layoutButtonReadyForAutomation();

        /** Opens the actual footer button for the menu-only graphical fixture. */
        void ringworld$openLayoutEditorForAutomation();

        /** Reads the actual footer label after an accepted confirmation. */
        Component ringworld$layoutButtonMessageForAutomation();
    }
}
