package dev.ringworld.client;

import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pre-creation editor and cost preview for immutable RingWorld layout. */
public final class RingWorldCreationScreen extends Screen {
    private static final int COMPACT_HEIGHT = 320;
    private final Screen parent;
    private EditBox circumferenceField;
    private EditBox widthField;
    private EditBox wallHeightField;
    private boolean requestOceanMonument;
    private Button monumentButton;
    private Button applyButton;
    @Nullable private RingDimensionReport report;
    @Nullable private String inputError;

    public RingWorldCreationScreen(Screen parent) {
        super(Component.literal("RingWorld layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RingWorldConfig config = RingWorldConfig.load();
        int left = this.width / 2 - 100;
        boolean compact = this.height < COMPACT_HEIGHT;
        int firstFieldY = compact ? 28 : 58;
        int fieldStep = compact ? 24 : 36;
        int presetY = compact ? 100 : 166;
        circumferenceField = numericField(left, firstFieldY, "Circumference blocks",
                config.circumferenceBlocks());
        widthField = numericField(left, firstFieldY + fieldStep, "Width blocks",
                config.widthBlocks());
        wallHeightField = numericField(left, firstFieldY + fieldStep * 2,
                "Wall height blocks",
                config.wallHeightBlocks());
        requestOceanMonument = config.requestOceanMonument();

        addRenderableWidget(Button.builder(Component.literal("Safe small"),
                button -> setPreset(2_048, 416, 160))
                .bounds(this.width / 2 - 154, presetY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Production"),
                button -> setPreset(
                        RingWorldSettings.DEFAULT_CIRCUMFERENCE,
                        RingWorldSettings.DEFAULT_WIDTH,
                        RingWorldSettings.DEFAULT_WALL_HEIGHT))
                .bounds(this.width / 2 - 50, presetY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Current"),
                button -> setPreset(
                        config.circumferenceBlocks(),
                        config.widthBlocks(),
                        config.wallHeightBlocks()))
                .bounds(this.width / 2 + 54, presetY, 100, 20).build());
        monumentButton = addRenderableWidget(Button.builder(monumentMessage(),
                button -> {
                    requestOceanMonument = !requestOceanMonument;
                    button.setMessage(monumentMessage());
                })
                .bounds(this.width / 2 - 100, presetY + 26, 200, 20).build());

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

    private Component monumentMessage() {
        return Component.literal("Ocean monument guarantee: " + (requestOceanMonument ? "On" : "Off"));
    }

    private void updateReport() {
        if (applyButton == null || circumferenceField == null) return;
        try {
            int circumference = Integer.parseInt(circumferenceField.getValue());
            int width = Integer.parseInt(widthField.getValue());
            int wallHeight = Integer.parseInt(wallHeightField.getValue());
            report = RingDimensionReport.forVanillaOverworld(
                    new RingGeometry(width, circumference), wallHeight);
            inputError = null;
            applyButton.active = report.isValid();
        } catch (IllegalArgumentException exception) {
            report = null;
            inputError = exception.getMessage();
            applyButton.active = false;
        }
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
                Component.literal("This new Overworld will permanently use "
                        + confirmedReport.geometry().circumferenceBlocks() + "×"
                        + confirmedReport.geometry().widthBlocks()
                        + " blocks with wall height "
                        + confirmedReport.wallHeightBlocks()
                        + " and ocean monument guarantee "
                        + (requestOceanMonument ? "enabled" : "disabled")
                        + ". Existing worlds are not changed."),
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
            inputError = exception.getMessage();
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
        int firstFieldY = compact ? 28 : 58;
        int fieldStep = compact ? 24 : 36;
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

        int y = compact ? 154 : 226;
        int lineStep = compact ? 10 : 12;
        if (inputError != null) {
            context.centeredText(font,
                    Component.literal(inputError), center, y, 0xFFFF6060);
        } else if (report != null) {
            RingRenderProfile renderProfile = RingRenderProfile.create(
                    report.geometry(), 28 * 16.0);
            drawReportLine(context, y, "Chunks: %d around × %d across (%s total)"
                    .formatted(report.geometry().circumferenceChunks(),
                            report.geometry().widthChunks(),
                            formatLong(report.canonicalChunkCount())));
            drawReportLine(context, y + lineStep, String.format(Locale.ROOT,
                    "Radius %.2f; centre Y %.2f; sky width %.2f°",
                    report.geometry().radius(), report.geometry().physicalCenterY(),
                    report.oppositeAngularWidthDegrees()));
            drawReportLine(context, y + lineStep * 2, String.format(Locale.ROOT,
                    "Wall top Y %d; clouds Y %d; top clearance %.2f",
                    report.wallTopYExclusive(), report.cloudBaseY(),
                    report.radialClearanceAtHighestPlane()));
            drawReportLine(context, y + lineStep * 3,
                    "Atlas %s cells (~%s MiB); noise lookup ~%s MiB"
                    .formatted(formatLong(report.atlasCellCount()),
                            formatMiB(report.estimatedAtlasBytes()),
                            formatMiB(report.estimatedNoiseCoordinateBytes())));
            drawReportLine(context, y + lineStep * 4, String.format(Locale.ROOT,
                    "GPU %d×%d; %.2f×%.2f blocks/texel; %s vertices",
                    renderProfile.textureColumns(), renderProfile.textureRows(),
                    renderProfile.textureBlocksPerTexelX(),
                    renderProfile.textureBlocksPerTexelZ(),
                    formatLong(renderProfile.vertexCount())));
            drawReportLine(context, y + lineStep * 5,
                    "GPU texture/mesh ~%s/%s MiB; build scratch ~%s MiB"
                            .formatted(formatMiB(renderProfile.estimatedGpuTextureBytes()),
                                    formatMiB(renderProfile.estimatedGpuMeshBytes()),
                                    formatMiB(renderProfile.estimatedTextureBuildScratchBytes())));
            if (!report.errors().isEmpty()) {
                context.centeredText(font,
                        Component.literal(report.errors().getFirst()), center,
                        y + lineStep * 7, 0xFFFF6060);
            } else if (!report.warnings().isEmpty()) {
                context.centeredText(font,
                        Component.literal(report.warnings().getFirst()), center,
                        y + lineStep * 7, 0xFFFFD060);
            }
        }
        context.centeredText(font,
                Component.literal("Dimensions and monument choice become immutable when the Overworld is first loaded."),
                center, height - 50, 0xFFFFD060);
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

    private static String formatLong(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatMiB(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }

    /** Parent-screen hook kept UI-local so accepting a layout refreshes its summary. */
    public interface LayoutButtonOwner {
        void ringworld$refreshLayoutButton();
    }
}
