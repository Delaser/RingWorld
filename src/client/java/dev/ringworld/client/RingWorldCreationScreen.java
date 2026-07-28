package dev.ringworld.client;

import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Pre-creation editor and cost preview for immutable RingWorld layout. */
public final class RingWorldCreationScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget circumferenceField;
    private TextFieldWidget widthField;
    private TextFieldWidget wallHeightField;
    private ButtonWidget applyButton;
    @Nullable private RingDimensionReport report;
    @Nullable private String inputError;

    public RingWorldCreationScreen(Screen parent) {
        super(Text.literal("RingWorld layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RingWorldConfig config = RingWorldConfig.load();
        int left = this.width / 2 - 100;
        circumferenceField = numericField(left, 58, "Circumference blocks",
                config.circumferenceBlocks());
        widthField = numericField(left, 94, "Width blocks", config.widthBlocks());
        wallHeightField = numericField(left, 130, "Wall height blocks",
                config.wallHeightBlocks());

        addDrawableChild(ButtonWidget.builder(Text.literal("Safe small"),
                button -> setPreset(2_048, 416, 160))
                .dimensions(this.width / 2 - 154, 166, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Production"),
                button -> setPreset(
                        RingWorldSettings.DEFAULT_CIRCUMFERENCE,
                        RingWorldSettings.DEFAULT_WIDTH,
                        RingWorldSettings.DEFAULT_WALL_HEIGHT))
                .dimensions(this.width / 2 - 50, 166, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Current"),
                button -> setPreset(
                        config.circumferenceBlocks(),
                        config.widthBlocks(),
                        config.wallHeightBlocks()))
                .dimensions(this.width / 2 + 54, 166, 100, 20).build());

        applyButton = addDrawableChild(ButtonWidget.builder(Text.literal("Use for new world"),
                button -> apply())
                .dimensions(this.width / 2 - 154, this.height - 34, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),
                button -> close())
                .dimensions(this.width / 2 + 4, this.height - 34, 150, 20).build());
        updateReport();
    }

    private TextFieldWidget numericField(int x, int y, String label, int value) {
        TextFieldWidget field = new TextFieldWidget(
                textRenderer, x, y, 200, 20, Text.literal(label));
        field.setMaxLength(9);
        field.setText(Integer.toString(value));
        field.setChangedListener(ignored -> updateReport());
        return addDrawableChild(field);
    }

    private void setPreset(int circumference, int width, int wallHeight) {
        circumferenceField.setText(Integer.toString(circumference));
        widthField.setText(Integer.toString(width));
        wallHeightField.setText(Integer.toString(wallHeight));
        updateReport();
    }

    private void updateReport() {
        if (applyButton == null || circumferenceField == null) return;
        try {
            int circumference = Integer.parseInt(circumferenceField.getText());
            int width = Integer.parseInt(widthField.getText());
            int wallHeight = Integer.parseInt(wallHeightField.getText());
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
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                persistLayout(confirmedReport);
            } else {
                client.setScreen(this);
            }
        }, Text.literal("Lock RingWorld dimensions?"),
                Text.literal("This new Overworld will permanently use "
                        + confirmedReport.geometry().circumferenceBlocks() + "×"
                        + confirmedReport.geometry().widthBlocks()
                        + " blocks with wall height "
                        + confirmedReport.wallHeightBlocks()
                        + ". Existing worlds are not changed."),
                Text.literal("Lock and use"), Text.literal("Go back")));
    }

    private void persistLayout(RingDimensionReport confirmedReport) {
        try {
            RingWorldConfig.saveBootstrapLayout(
                    confirmedReport.geometry().widthBlocks(),
                    confirmedReport.geometry().circumferenceBlocks(),
                    confirmedReport.wallHeightBlocks());
            client.setScreen(parent);
        } catch (RuntimeException exception) {
            inputError = exception.getMessage();
            applyButton.active = false;
            client.setScreen(this);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Screen.renderWithTooltip already rendered this screen's panorama,
        // blur, and darkening layer before dispatching here. A second
        // renderBackground call is illegal on 1.21.11 because GuiRenderState
        // permits only one blur layer per frame.
        super.render(context, mouseX, mouseY, deltaTicks);
        int center = width / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, center, 18, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Circumference (blocks)"),
                center - 100, 46, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal("Finite width (blocks)"),
                center - 100, 82, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.literal("Rim wall height (from Y=-64)"),
                center - 100, 118, 0xA0A0A0);

        int y = 200;
        if (inputError != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(inputError), center, y, 0xFF6060);
        } else if (report != null) {
            RingRenderProfile renderProfile = RingRenderProfile.create(
                    report.geometry(), 28 * 16.0);
            drawReportLine(context, y, "Chunks: %d around × %d across (%s total)"
                    .formatted(report.geometry().circumferenceChunks(),
                            report.geometry().widthChunks(),
                            formatLong(report.canonicalChunkCount())));
            drawReportLine(context, y + 12, String.format(Locale.ROOT,
                    "Radius %.2f; centre Y %.2f; sky width %.2f°",
                    report.geometry().radius(), report.geometry().physicalCenterY(),
                    report.oppositeAngularWidthDegrees()));
            drawReportLine(context, y + 24, String.format(Locale.ROOT,
                    "Wall top Y %d; clouds Y %d; top clearance %.2f",
                    report.wallTopYExclusive(), report.cloudBaseY(),
                    report.radialClearanceAtHighestPlane()));
            drawReportLine(context, y + 36, "Atlas %s cells (~%s MiB); noise lookup ~%s MiB"
                    .formatted(formatLong(report.atlasCellCount()),
                            formatMiB(report.estimatedAtlasBytes()),
                            formatMiB(report.estimatedNoiseCoordinateBytes())));
            drawReportLine(context, y + 48, String.format(Locale.ROOT,
                    "GPU %d×%d; %.2f×%.2f blocks/texel; %s vertices",
                    renderProfile.textureColumns(), renderProfile.textureRows(),
                    renderProfile.textureBlocksPerTexelX(),
                    renderProfile.textureBlocksPerTexelZ(),
                    formatLong(renderProfile.vertexCount())));
            drawReportLine(context, y + 60,
                    "GPU texture/mesh ~%s/%s MiB; build scratch ~%s MiB"
                            .formatted(formatMiB(renderProfile.estimatedGpuTextureBytes()),
                                    formatMiB(renderProfile.estimatedGpuMeshBytes()),
                                    formatMiB(renderProfile.estimatedTextureBuildScratchBytes())));
            if (!report.errors().isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(report.errors().getFirst()), center, y + 78, 0xFF6060);
            } else if (!report.warnings().isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(report.warnings().getFirst()), center, y + 78, 0xFFD060);
            }
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Dimensions become immutable when the Overworld is first loaded."),
                center, height - 50, 0xFFD060);
    }

    private void drawReportLine(DrawContext context, int y, String value) {
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(value),
                width / 2, y, 0xD0D0D0);
    }

    private static String formatLong(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatMiB(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }
}
