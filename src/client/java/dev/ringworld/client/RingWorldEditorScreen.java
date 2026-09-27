package dev.ringworld.client;

import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldCreationUiModel;
import dev.ringworld.world.RingWorldGenerationSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.WorldOptions;

/** One draft for the five new-world pages; Use locks the preview seed until Apply. */
public final class RingWorldEditorScreen extends Screen {
    private static final int PANEL = 0xE0172226;
    private static final int BORDER = 0xFF64776D;
    private static final int LABEL = 0xFFC4D0CA;
    private static final int MUTED = 0xFF9DAEA5;
    private static final int ACCENT = 0xFFA6CC91;
    private static final int ERROR = 0xFFFF9080;
    private static final Page[] PAGES = Page.values();
    private final Screen parent;
    private final RingWorldCreationScreen.LayoutButtonOwner owner;
    private final String originalSeed;
    private final RingWorldConfig original;
    private Page page = Page.RING;
    private String circumference;
    private String widthBlocks;
    private String wallHeight;
    private String thickness;
    private String decay;
    private String seed;
    private String selectedSeed;
    private RingWallStyle.Palette palette;
    private RingWallStyle.Pattern pattern;
    private RingSkyProfile.Backdrop backdrop;
    private RingSkyProfile.LightSource sun;
    private RingWorldGenerationSettings generation;
    private boolean monument;
    private boolean showDetails;
    private String notice = "";
    private EditBox circumferenceField;
    private EditBox widthField;
    private EditBox wallHeightField;
    private EditBox thicknessField;
    private EditBox decayField;
    private Button monumentButton;

    public RingWorldEditorScreen(Screen parent) {
        super(Component.literal("Create a RingWorld"));
        this.parent = Objects.requireNonNull(parent);
        this.owner = (RingWorldCreationScreen.LayoutButtonOwner) parent;
        this.original = RingWorldConfig.load();
        this.originalSeed = owner.ringworld$seedText();
        load(original);
        seed = originalSeed;
        selectedSeed = originalSeed;
    }

    private void load(RingWorldConfig config) {
        circumference = Integer.toString(config.circumferenceBlocks());
        widthBlocks = Integer.toString(config.widthBlocks());
        wallHeight = Integer.toString(config.wallHeightBlocks());
        thickness = Integer.toString(config.wallStyle().thicknessBlocks());
        decay = Integer.toString(config.wallStyle().decayPercent());
        palette = config.wallStyle().palette();
        pattern = Arrays.asList(RingWallStyle.Pattern.selectableValues()).contains(config.wallStyle().pattern())
                ? config.wallStyle().pattern() : RingWallStyle.Pattern.ENGINEERED;
        backdrop = config.skyProfile().backdrop();
        sun = config.skyProfile().lightSource();
        generation = config.generationSettings();
        monument = config.requestOceanMonument();
    }

    @Override
    protected void init() {
        // Draft strings are updated by responders. Rebuilding after resize never
        // reads stale widgets or changes the seed while rebuilding controls.
        circumferenceField = widthField = wallHeightField = null;
        thicknessField = decayField = null;
        monumentButton = null;
        Bounds b = bounds();
        int gap = 3, tabWidth = (b.innerWidth() - gap * 4) / 5;
        for (int i = 0; i < PAGES.length; i++) {
            Page target = PAGES[i];
            addRenderableWidget(Button.builder(Component.literal(target.label + (page == target ? " *" : "")),
                    button -> select(target))
                    .bounds(b.x() + i * (tabWidth + gap), b.top() + 27, tabWidth, 20).build());
        }
        switch (page) {
            case RING -> ringWidgets(b);
            case TERRAIN -> terrainWidgets(b);
            case WALLS -> wallWidgets(b);
            case SKY -> skyWidgets(b);
            case PREVIEW -> previewWidgets(b);
        }
        int actionWidth = Math.min(112, (b.innerWidth() - 8) / 2);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(b.right() - actionWidth * 2 - 5, b.bottom() - 27, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply settings"), button -> apply())
                .bounds(b.right() - actionWidth, b.bottom() - 27, actionWidth, 20).build());
    }

    private void rebuild() { clearWidgets(); init(); }
    void openTab(String name) {
        page = Page.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        notice = "";
        RingMinecraftClientAccess.setScreen(minecraft, this);
    }

    private void select(Page next) {
        if (next == Page.PREVIEW) {
            openPreview();
        } else {
            page = next;
            notice = "";
            rebuild();
        }
    }

    private void ringWidgets(Bounds b) {
        int gap = 4, each = (b.innerWidth() - gap * 2) / 3;
        RingWorldCreationUiModel.Preset[] presets = {RingWorldCreationUiModel.SMALL,
                RingWorldCreationUiModel.MEDIUM, RingWorldCreationUiModel.LARGE};
        for (int i = 0; i < presets.length; i++) {
            RingWorldCreationUiModel.Preset preset = presets[i];
            addRenderableWidget(Button.builder(Component.literal(preset.label()
                            + (matches(preset) ? " *" : "")), button -> {
                        circumference = Integer.toString(preset.circumferenceBlocks());
                        widthBlocks = Integer.toString(preset.widthBlocks());
                        wallHeight = Integer.toString(preset.wallHeightBlocks());
                        if (preset.widthBlocks() < 160) monument = false;
                        rebuild();
                    }).bounds(b.x() + i * (each + gap), b.top() + 69, each, 20).build());
        }
        int fieldsY = b.top() + (b.compact() ? 113 : 126);
        circumferenceField = number(b.x(), fieldsY, each, "Circumference", circumference,
                value -> circumference = value);
        widthField = number(b.x() + each + gap, fieldsY, each, "Width", widthBlocks,
                value -> widthBlocks = value);
        wallHeightField = number(b.x() + (each + gap) * 2, fieldsY, each,
                "Wall height", wallHeight, value -> wallHeight = value);
        if (!b.compact()) {
            addRenderableWidget(Button.builder(Component.literal(
                            showDetails ? "Hide size details" : "Size details & estimates"),
                    button -> { showDetails = !showDetails; rebuild(); })
                    .bounds(b.x(), b.bottom() - 56, Math.min(180, b.innerWidth() / 2), 20).build());
            addRenderableWidget(Button.builder(Component.literal("Restore saved settings"),
                    button -> confirmRestore())
                    .bounds(b.right() - Math.min(180, b.innerWidth() / 2), b.bottom() - 56,
                            Math.min(180, b.innerWidth() / 2), 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Details"),
                    button -> { showDetails = !showDetails; rebuild(); })
                    .bounds(b.x(), b.bottom() - 53, 75, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Restore saved"),
                    button -> confirmRestore())
                    .bounds(b.right() - 116, b.bottom() - 53, 116, 20).build());
        }
    }

    private EditBox number(int x, int y, int w, String label, String value,
                           java.util.function.Consumer<String> responder) {
        EditBox field = new EditBox(font, x, y, w, 20, Component.literal(label));
        field.setMaxLength(9);
        field.setValue(value);
        field.setResponder(text -> { responder.accept(text); notice = ""; });
        return addRenderableWidget(field);
    }

    private void terrainWidgets(Bounds b) {
        int y = b.top() + 68;
        addRenderableWidget(Button.builder(Component.literal("More structures: "
                        + onOff(generation.moreStructures())), button -> {
                    generation = generation.withMoreStructures(!generation.moreStructures());
                    rebuild();
                }).bounds(b.x(), y, b.innerWidth(), 20).build());
        y += b.compact() ? 42 : 48;
        boolean available = monumentAvailable();
        monumentButton = addRenderableWidget(Button.builder(Component.literal(
                        available ? "Ocean monument search: " + onOff(monument)
                                : "Ocean monument: needs 160 width"), button -> {
                    monument = !monument;
                    rebuild();
                }).bounds(b.x(), y, b.innerWidth(), 20).build());
        monumentButton.active = available;
        y += b.compact() ? 42 : 48;
        addRenderableWidget(Button.builder(Component.literal("Continuous ring river: "
                        + onOff(generation.continuousRiver())), button -> {
                    generation = generation.withContinuousRiver(!generation.continuousRiver());
                    rebuild();
                }).bounds(b.x(), y, b.innerWidth(), 20).build());
    }

    private void wallWidgets(Bounds b) {
        int left = b.x(), top = b.top();
        if (b.compact()) {
            wallChoices(b, left, top + 69, b.innerWidth());
            wallValues(b, left, top + 206, b.innerWidth());
        } else {
            int leftWidth = b.innerWidth() / 2 - 5;
            int rightX = left + leftWidth + 10;
            wallChoices(b, rightX, top + 65, b.innerWidth() - leftWidth - 10);
            wallValues(b, rightX, top + 163, b.innerWidth() - leftWidth - 10);
        }
    }

    private void wallChoices(Bounds b, int x, int y, int w) {
        int materialX = b.compact() ? x + Math.min(160, (w - 10) / 2) + 8 : x;
        int materialWidth = b.compact() ? w - (materialX - x) : (w - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal((b.compact() ? "" : "Material: ") + palette.label()),
                button -> RingMinecraftClientAccess.setScreen(minecraft,
                        new RingWallChoiceScreen(this, RingWallChoiceScreen.Kind.MATERIAL,
                                selected -> palette = selected, selected -> { })))
                .bounds(materialX, y, materialWidth, 20).build());
        String preset = RingWallStyle.Preset.find(validWallStyle())
                .map(RingWallStyle.Preset::label).orElse("Custom");
        addRenderableWidget(Button.builder(Component.literal("Preset: " + preset), button ->
                RingMinecraftClientAccess.setScreen(minecraft,
                        new RingWallChoiceScreen(this, RingWallChoiceScreen.Kind.PRESET,
                                selected -> { }, selected -> {
                                    RingWallStyle style = selected.style();
                                    thickness = Integer.toString(style.thicknessBlocks());
                                    decay = Integer.toString(style.decayPercent());
                                    palette = style.palette();
                                    pattern = style.pattern();
                                })))
                .bounds(b.compact() ? materialX : materialX + materialWidth + 4,
                        b.compact() ? y + 25 : y,
                        materialWidth, 20).build());
        RingWallStyle.Pattern[] options = RingWallStyle.Pattern.selectableValues();
        int patternX = b.compact() ? b.x() : x;
        int patternWidth = b.compact() ? b.innerWidth() : w;
        int each = (patternWidth - 8) / 3;
        for (int i = 0; i < options.length; i++) {
            RingWallStyle.Pattern choice = options[i];
            String label = choice == RingWallStyle.Pattern.ENGINEERED && b.compact()
                    ? "Structure" : choice.label();
            addRenderableWidget(Button.builder(Component.literal(label + (pattern == choice ? " *" : "")),
                    button -> { pattern = choice; rebuild(); })
                    .bounds(patternX + i * (each + 4),
                            b.compact() ? b.top() + 171 : y + 25, each, 20).build());
        }
    }

    private void wallValues(Bounds b, int x, int y, int w) {
        int half = (w - 5) / 2;
        thicknessField = number(x, y, half, "Thickness", thickness, value -> thickness = value);
        decayField = number(x + half + 5, y, half, "Decay", decay, value -> decay = value);
    }

    private void skyWidgets(Bounds b) {
        int each = (b.innerWidth() - 8) / 3;
        for (int i = 0; i < RingSkyProfile.Backdrop.values().length; i++) {
            RingSkyProfile.Backdrop choice = RingSkyProfile.Backdrop.values()[i];
            addRenderableWidget(Button.builder(Component.literal(choice.label()
                            + (backdrop == choice ? " *" : "")), button -> {
                        backdrop = choice;
                        rebuild();
                    }).bounds(b.x() + i * (each + 4), b.top() + 76, each, 20).build());
        }
        for (int i = 0; i < RingSkyProfile.LightSource.values().length; i++) {
            RingSkyProfile.LightSource choice = RingSkyProfile.LightSource.values()[i];
            addRenderableWidget(Button.builder(Component.literal(choice.label()
                            + (sun == choice ? " *" : "")), button -> {
                        sun = choice;
                        rebuild();
                    }).bounds(b.x() + i * (each + 4), b.top() + 143, each, 20).build());
        }
    }

    private void previewWidgets(Bounds b) {
        addRenderableWidget(Button.builder(Component.literal("Open seed terrain preview"),
                button -> openPreview())
                .bounds(b.x(), b.top() + 85, b.innerWidth(), 20).build());
    }

    private void openPreview() {
        RingWorldCreationUiModel.Validation validation = validation();
        if (!validation.canApply()) {
            page = Page.RING;
            notice = validation.messages().isEmpty() ? "Correct the ring size first." : validation.messages().getFirst();
            rebuild();
            return;
        }
        page = Page.PREVIEW;
        RingMinecraftClientAccess.setScreen(minecraft,
                new RingSeedPreviewScreen(this, owner, validation.report().geometry(), generation));
    }

    String draftSeed() { return seed; }
    void setDraftSeed(String value) {
        seed = value;
    }
    void useDraftSeed() { selectedSeed = seed; }
    String selectedSeed() { return selectedSeed; }
    long resolvedDraftSeed() {
        return WorldOptions.parseSeed(seed).orElseGet(owner::ringworld$resolvedSeed);
    }
    Screen creationParent() { return parent; }
    void applyFromPreview() { apply(); }
    void cancelFromPreview() { onClose(); }

    private boolean matches(RingWorldCreationUiModel.Preset p) {
        return circumference.equals(Integer.toString(p.circumferenceBlocks()))
                && widthBlocks.equals(Integer.toString(p.widthBlocks()))
                && wallHeight.equals(Integer.toString(p.wallHeightBlocks()));
    }
    private static String onOff(boolean value) { return value ? "On" : "Off"; }
    private RingWallStyle validWallStyle() {
        try { return RingWallStyle.custom(Integer.parseInt(thickness.trim()), palette, pattern,
                Integer.parseInt(decay.trim())); }
        catch (IllegalArgumentException exception) {
            return RingWallStyle.DEFAULT;
        }
    }
    private String wallError() {
        try { RingWallStyle.custom(Integer.parseInt(thickness.trim()), palette, pattern,
                Integer.parseInt(decay.trim())); return ""; }
        catch (NumberFormatException exception) { return "Thickness and decay must be whole numbers."; }
        catch (IllegalArgumentException exception) { return exception.getMessage(); }
    }
    private RingWorldCreationUiModel.Validation validation() {
        return RingWorldCreationUiModel.validate(circumference, widthBlocks, wallHeight,
                validWallStyle(), generation);
    }
    private boolean monumentAvailable() {
        RingWorldCreationUiModel.Validation v = validation();
        return v.canApply() && RingWorldCreationUiModel.monumentAvailable(v.report().geometry());
    }
    private boolean dirty() {
        return !circumference.equals(Integer.toString(original.circumferenceBlocks()))
                || !widthBlocks.equals(Integer.toString(original.widthBlocks()))
                || !wallHeight.equals(Integer.toString(original.wallHeightBlocks()))
                || !validWallStyle().equals(original.wallStyle())
                || !thickness.equals(Integer.toString(original.wallStyle().thicknessBlocks()))
                || !decay.equals(Integer.toString(original.wallStyle().decayPercent()))
                || backdrop != original.skyProfile().backdrop()
                || sun != original.skyProfile().lightSource()
                || !generation.equals(original.generationSettings())
                || monument != original.requestOceanMonument()
                || !seed.equals(originalSeed)
                || !selectedSeed.equals(originalSeed);
    }

    private void apply() {
        String wallError = wallError();
        if (!wallError.isEmpty()) { showProblem(Page.WALLS, wallError); return; }
        RingWorldCreationUiModel.Validation v = validation();
        if (!v.canApply()) {
            showProblem(Page.RING, v.messages().isEmpty()
                    ? "Correct the ring size first." : v.messages().getFirst());
            return;
        }
        if (!monumentAvailable()) monument = false;
        try {
            RingDimensionReport report = v.report();
            RingWorldConfig.saveBootstrapLayout(report.geometry().widthBlocks(),
                    report.geometry().circumferenceBlocks(), report.wallHeightBlocks(),
                    validWallStyle(), new RingSkyProfile(backdrop, sun, RingSkyProfile.FORMAT_VERSION),
                    generation, monument);
            owner.ringworld$setSeedText(selectedSeed);
            owner.ringworld$refreshLayoutButton();
            RingMinecraftClientAccess.setScreen(minecraft, parent);
        } catch (RuntimeException exception) {
            showProblem(page, exception.getMessage() == null
                    ? "Could not save RingWorld settings." : exception.getMessage());
        }
    }

    private void showProblem(Page target, String message) {
        page = target;
        notice = message;
        if (RingMinecraftClientAccess.screen(minecraft) == this) rebuild();
        else RingMinecraftClientAccess.setScreen(minecraft, this);
    }

    void ringworld$automationTab(String name) { select(Page.valueOf(name)); }
    void ringworld$automationApply() { apply(); }
    boolean ringworld$automationDraftSeedIs(String value) { return seed.equals(value); }
    boolean ringworld$automationSelectedSeedIs(String value) { return selectedSeed.equals(value); }
    boolean ringworld$automationParentSeedIs(String value) {
        return owner.ringworld$seedText().equals(value);
    }

    private void confirmRestore() {
        RingMinecraftClientAccess.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) {
                load(RingWorldConfig.load());
                seed = originalSeed;
                selectedSeed = originalSeed;
                notice = "";
            }
            RingMinecraftClientAccess.setScreen(minecraft, this);
        }, Component.literal("Restore saved settings?"),
                Component.literal("Replace this draft with the saved RingWorld settings?"),
                Component.literal("Restore"), Component.literal("Back")));
    }

    @Override
    public void onClose() {
        if (!dirty()) { RingMinecraftClientAccess.setScreen(minecraft, parent); return; }
        RingMinecraftClientAccess.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            RingMinecraftClientAccess.setScreen(minecraft, confirmed ? parent : this);
        }, Component.literal("Discard changes?"),
                Component.literal("Discard changes to RingWorld settings and the seed?"),
                Component.literal("Discard"), Component.literal("Keep editing")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        Bounds b = bounds();
        graphics.fill(b.left(), b.top(), b.left() + b.panelWidth(), b.bottom(), PANEL);
        graphics.outline(b.left(), b.top(), b.panelWidth(), b.bottom() - b.top(), BORDER);
        super.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
        graphics.centeredText(font, title, width / 2, b.top() + 8, 0xFFFFFFFF);
        if (!b.compact()) graphics.text(font,
                Component.literal("Settings lock when the world is created."),
                b.x(), b.bottom() - 38, MUTED);
        switch (page) {
            case RING -> drawRing(graphics, b);
            case TERRAIN -> drawTerrain(graphics, b);
            case WALLS -> drawWalls(graphics, b);
            case SKY -> drawSky(graphics, b);
            case PREVIEW -> {
                graphics.centeredText(font, Component.literal("Seed and terrain"), width / 2,
                        b.top() + 64, LABEL);
                graphics.centeredText(font, Component.literal("Preview uses the selected ring size."),
                        width / 2, b.top() + 114, MUTED);
            }
        }
        if (!notice.isBlank()) drawLines(graphics, notice, b.x(), b.bottom() - 68,
                b.innerWidth(), ERROR, 2);
    }

    private void drawRing(GuiGraphicsExtractor g, Bounds b) {
        g.text(font, Component.literal("Choose your ring size"), b.x(), b.top() + 57, LABEL);
        RingWorldCreationUiModel.Preset p = matches(RingWorldCreationUiModel.SMALL)
                ? RingWorldCreationUiModel.SMALL : matches(RingWorldCreationUiModel.LARGE)
                ? RingWorldCreationUiModel.LARGE : matches(RingWorldCreationUiModel.MEDIUM)
                ? RingWorldCreationUiModel.MEDIUM : null;
        if (!b.compact()) {
            g.text(font, Component.literal("Small 2,048 x 128     Medium 16,384 x 256     Large 32,768 x 512"),
                    b.x(), b.top() + 96, MUTED);
        }
        int y = b.top() + (b.compact() ? 101 : 114);
        int each = (b.innerWidth() - 8) / 3;
        g.text(font, Component.literal("Circumference"), b.x(), y, LABEL);
        g.text(font, Component.literal("Width"), b.x() + each + 4, y, LABEL);
        g.text(font, Component.literal("Wall height"), b.x() + (each + 4) * 2, y, LABEL);
        RingWorldCreationUiModel.Validation v = validation();
        String summary = !v.messages().isEmpty() ? v.messages().getFirst()
                : !RingWorldCreationUiModel.monumentAvailable(v.report().geometry())
                ? "Small is experimental: portal may need mining."
                : p == RingWorldCreationUiModel.LARGE ? "Large may take longer to generate."
                : p == RingWorldCreationUiModel.MEDIUM ? "Medium is the recommended starting size."
                : "Custom size. Review details before creating.";
        int summaryY = b.top() + (b.compact() ? 145 : 165);
        drawLines(g, summary, b.x(), summaryY, b.innerWidth(),
                v.messages().isEmpty() ? ACCENT : ERROR, 2);
        if (showDetails && v.canApply()) {
            List<String> lines = RingWorldCreationUiModel.Validation.metricLines(v.report());
            int start = b.top() + (b.compact() ? 169 : 190);
            int cap = b.compact() ? 2 : Math.min(6, (b.bottom() - 74 - start) / 12);
            for (int i = 0; i < Math.max(0, cap); i++)
                g.text(font, Component.literal(lines.get(i)), b.x(), start + i * 12, MUTED);
        }
    }

    private void drawTerrain(GuiGraphicsExtractor g, Bounds b) {
        int y = b.top() + 57;
        g.text(font, Component.literal("Terrain and structures"), b.x(), y, LABEL);
        g.text(font, Component.literal("Moderately increases eligible landmarks."),
                b.x(), y + 33, MUTED);
        int step = b.compact() ? 42 : 48;
        g.text(font, Component.literal(monumentAvailable()
                        ? "One search on first load; placement is not guaranteed."
                        : "Requires a ring at least 160 blocks wide."),
                b.x(), y + step + 33, MUTED);
        g.text(font, Component.literal("A seeded, terrain-integrated water loop."),
                b.x(), y + step * 2 + 33, MUTED);
    }

    private void drawWalls(GuiGraphicsExtractor g, Bounds b) {
        g.text(font, Component.literal("Selected wall sample"), b.x(), b.top() + 56, LABEL);
        int x = b.x(), y = b.top() + 69;
        int imageWidth = b.compact() ? Math.min(160, (b.innerWidth() - 10) / 2)
                : b.innerWidth() / 2 - 5;
        int imageHeight = b.compact() ? Math.round(imageWidth * 355.0F / 640.0F)
                : Math.min(141, b.bottom() - y - 72);
        Identifier sample = Identifier.fromNamespaceAndPath("ringworld",
                "textures/gui/wall_samples/wall-" + palette.name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + pattern.name().toLowerCase(java.util.Locale.ROOT) + ".png");
        if (minecraft.getResourceManager().getResource(sample).isPresent()) {
            g.blit(sample, x, y, x + imageWidth, y + imageHeight,
                    0.0F, 1.0F, 0.0F, 1.0F);
        } else {
            g.text(font, Component.literal("Sample unavailable"), x, y + 12, ERROR);
        }
        if (b.compact()) {
            int right = x + imageWidth + 8;
            drawLines(g, wallError().isEmpty() ? "Sample: 7 thick, 0% decay"
                            : wallError(), right, b.top() + 121,
                    b.right() - right, wallError().isEmpty() ? MUTED : ERROR, 2);
            int half = (b.innerWidth() - 5) / 2;
            g.text(font, Component.literal("Thickness 1-32"), x, b.top() + 194, LABEL);
            g.text(font, Component.literal("Decay 0-100%"), x + half + 5,
                    b.top() + 194, LABEL);
        } else {
            g.text(font, Component.literal("Sample: 7 thick, 0% decay"),
                    x, y + imageHeight + 4, MUTED);
            int right = x + imageWidth + 10;
            g.text(font, Component.literal("Thickness 1-32       Decay 0-100%"),
                    right, b.top() + 150, LABEL);
        }
    }

    private void drawSky(GuiGraphicsExtractor g, Bounds b) {
        g.text(font, Component.literal("Backdrop"), b.x(), b.top() + 61, LABEL);
        int each = (b.innerWidth() - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int x = b.x() + i * (each + 4);
            int color = switch (i) {
                case 0 -> 0xFF7EA9BD;
                case 1 -> 0xFF1B2A45;
                default -> 0xFF080D16;
            };
            g.fill(x + 2, b.top() + 101, x + each - 2, b.top() + 116, color);
            g.fill(x + 2, b.top() + 116, x + each - 2, b.top() + 119,
                    0xFF647E53);
            if (i == 0) g.fill(x + each / 2, b.top() + 105,
                    x + each / 2 + 5, b.top() + 110, 0xFFFFEFC5);
            if (i == 1) g.fill(x + each / 2, b.top() + 105,
                    x + each / 2 + 2, b.top() + 107, 0xFFE7ECFC);
        }
        g.text(font, Component.literal("Sun"), b.x(), b.top() + 128, LABEL);
        drawLines(g, "Shared sky appearance. Time and gameplay are unchanged.",
                b.x(), b.top() + 184, b.innerWidth(), MUTED, 2);
    }

    private void drawLines(GuiGraphicsExtractor g, String text, int x, int y,
                           int w, int color, int max) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), w);
        for (int i = 0; i < Math.min(max, lines.size()); i++)
            g.text(font, lines.get(i), x, y + 11 * i, color);
    }

    private Bounds bounds() {
        int panelWidth = Math.min(560, Math.max(304, width - 12));
        int left = (width - panelWidth) / 2;
        int top = Math.max(4, (height - Math.min(348, height - 8)) / 2);
        return new Bounds(left, top, panelWidth, height - top - 4, height < 350);
    }

    private record Bounds(int left, int top, int panelWidth, int bottom, boolean compact) {
        int x() { return left + 9; }
        int right() { return left + panelWidth - 9; }
        int innerWidth() { return panelWidth - 18; }
    }

    private enum Page {
        RING("Ring"), TERRAIN("Terrain"), WALLS("Walls"), SKY("Sky"), PREVIEW("Preview");
        final String label;
        Page(String label) { this.label = label; }
    }
}
