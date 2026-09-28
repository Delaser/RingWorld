package dev.ringworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewSampler;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingPreviewRequestGate;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldGenerationSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;

import org.jetbrains.annotations.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Fast, chunk-free preview of the selected seed wrapped across the whole ring. */
public final class RingSeedPreviewScreen extends Screen {
    private static final int PANEL_COLOR = 0xE0101116;
    private static final int BORDER_COLOR = 0xFF606872;
    private static final int LABEL_COLOR = 0xFFB8BDC5;
    private static final int ERROR_COLOR = 0xFFFF7070;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "ringworld", "dynamic/creation_seed_preview");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RingWorld creation seed preview");
        thread.setDaemon(true);
        return thread;
    });

    private final Screen parent;
    private final @Nullable RingWorldEditorScreen editor;
    private int windowStartBlock;
    private final RingWorldCreationScreen.LayoutButtonOwner owner;
    private final RingGeometry geometry;
    private final RingWorldGenerationSettings generationSettings;
    private final RingPreviewRequestGate<Result> requests = new RingPreviewRequestGate<>();
    private EditBox seedField;
    private Button useSeedButton;
    private Future<?> running;
    private long request;
    private int debounceTicks;
    private DynamicTexture texture;
    private String state = "Waiting";
    private String error = "";
    private long lastPreviewHash = Long.MIN_VALUE;

    public RingSeedPreviewScreen(RingWorldCreationScreen parent,
                                 RingWorldCreationScreen.LayoutButtonOwner owner,
                                 RingGeometry geometry,
                                 RingWorldGenerationSettings generationSettings) {
        super(Component.literal("Ring seed preview"));
        this.parent = parent;
        this.editor = null;
        this.owner = owner;
        this.geometry = geometry;
        this.generationSettings = generationSettings;
    }

    public RingSeedPreviewScreen(RingWorldEditorScreen parent,
                                 RingWorldCreationScreen.LayoutButtonOwner owner,
                                 RingGeometry geometry,
                                 RingWorldGenerationSettings generationSettings) {
        super(Component.literal("Ring seed preview"));
        this.parent = parent;
        this.editor = parent;
        this.owner = owner;
        this.geometry = geometry;
        this.generationSettings = generationSettings;
        this.windowStartBlock = geometry.circumferenceBlocks() / 3;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(620, Math.max(304, width - 16));
        int left = (width - panelWidth) / 2;
        seedField = new EditBox(font, left + 8, editor == null ? 53 : 60,
                panelWidth - (editor == null ? 112 : 148), 20,
                Component.literal("Seed"));
        seedField.setMaxLength(64);
        seedField.setValue(editor == null ? owner.ringworld$seedText() : editor.draftSeed());
        seedField.setResponder(value -> {
            if (editor == null) owner.ringworld$setSeedText(value);
            else editor.setDraftSeed(value);
            schedule();
        });
        addRenderableWidget(seedField);
        addRenderableWidget(Button.builder(Component.literal("Reroll"), button -> {
            seedField.setValue(Long.toString(WorldOptions.randomSeed()));
        }).bounds(left + panelWidth - (editor == null ? 98 : 136),
                editor == null ? 53 : 60, editor == null ? 90 : 62, 20).build());
        if (editor != null) {
            useSeedButton = addRenderableWidget(Button.builder(Component.literal("Use"), button ->
                    editor.useDraftSeed()).bounds(left + panelWidth - 70, 60, 62, 20).build());
        }
        if (editor == null) {
            addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                    .bounds(width / 2 - 100, height - 30, 200, 20).build());
        } else {
            String[] tabs = {"Ring", "Terrain", "Walls", "Sky", "Preview"};
            int tabWidth = Math.max(50, (panelWidth - 16) / 5);
            for (int index = 0; index < tabs.length; index++) {
                String tab = tabs[index];
                addRenderableWidget(Button.builder(Component.literal(tab + (index == 4 ? " *" : "")),
                        button -> { if (indexOfTab(tab) < 4) editor.openTab(tab); })
                        .bounds(left + 8 + index * tabWidth, 28, tabWidth - 2, 20).build());
            }
            int buttons = (panelWidth - 16 - 8) / 3;
            addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                    .bounds(left + 8, height - 30, buttons, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> editor.cancelFromPreview())
                    .bounds(left + 12 + buttons, height - 30, buttons, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Apply settings"), button -> editor.applyFromPreview())
                    .bounds(left + 16 + buttons * 2, height - 30, buttons, 20).build());
            int panY = Math.min(height - 72, 95 + Math.max(48, Math.min(160, height - 38 - 95 - 48)) + 6);
            addRenderableWidget(Button.builder(Component.literal("<"), button -> pan(-1))
                    .bounds(left + panelWidth - 54, panY, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> pan(1))
                    .bounds(left + panelWidth - 32, panY, 20, 20).build());
        }
        if (editor != null) {
            int mapWidth = panelWidth - 16;
            int mapHeight = Math.max(48, Math.min(160, height - 38 - 95 - 48));
            windowStartBlock = Math.min(windowStartBlock,
                    Math.max(0, geometry.circumferenceBlocks() - zoomBlocks(mapWidth, mapHeight)));
        }
        schedule();
    }

    private static int indexOfTab(String name) {
        return java.util.List.of("Ring", "Terrain", "Walls", "Sky", "Preview").indexOf(name);
    }

    private int zoomBlocks(int mapWidth, int mapHeight) {
        if (editor == null) return geometry.circumferenceBlocks();
        int aspectFit = (int)Math.ceil((double)geometry.widthBlocks() * mapWidth
                / Math.max(1, mapHeight));
        return Math.min(geometry.circumferenceBlocks(), Math.max(512, aspectFit));
    }

    private void pan(int direction) {
        int mapWidth = Math.min(620, Math.max(304, width - 16)) - 16;
        int mapHeight = Math.max(48, Math.min(160, height - 38 - 95 - 48));
        int span = zoomBlocks(mapWidth, mapHeight);
        int limit = Math.max(0, geometry.circumferenceBlocks() - span);
        windowStartBlock = Math.max(0, Math.min(limit, windowStartBlock + direction * Math.max(1, span / 2)));
    }

    private void schedule() {
        request = requests.begin();
        debounceTicks = 5;
        error = "";
        state = texture == null ? "Preparing…" : "Updating…";
        if (running != null) running.cancel(true);
    }

    @Override
    public void tick() {
        if (debounceTicks > 0 && --debounceTicks == 0) startPreview();
        Result result = requests.poll();
        if (result != null) {
            if (result.error() != null) {
                error = result.error();
                state = "Preview unavailable";
            } else {
                upload(result.preview());
                state = "Ready in " + result.elapsedMillis() + " ms";
            }
        }
    }

    private void startPreview() {
        long previewRequest = request;
        long seed = editor == null ? owner.ringworld$resolvedSeed() : editor.resolvedDraftSeed();
        PreviewInput input = snapshot(owner.ringworld$creationContext());
        state = "Generating from seed " + seed + "…";
        running = WORKER.submit(() -> {
            long started = System.nanoTime();
            try {
                RingTerrainPreview preview = generate(input, seed);
                if (preview == null) throw new IllegalStateException(
                        "The selected world type does not expose a compatible noise generator.");
                requests.complete(previewRequest, new Result(preview,
                        Math.round((System.nanoTime() - started) / 1_000_000.0), null));
            } catch (java.util.concurrent.CancellationException ignored) {
                // A newer seed/layout owns the next result.
            } catch (RuntimeException exception) {
                RingWorldMod.LOGGER.warn("Could not generate RingWorld creation preview", exception);
                requests.complete(previewRequest, new Result(null, 0L,
                        exception.getMessage() == null ? exception.getClass().getSimpleName()
                                : exception.getMessage()));
            }
        });
    }

    /** Snapshots immutable worldgen inputs on the client thread before the worker starts. */
    private PreviewInput snapshot(WorldCreationContext context) {
        ChunkGenerator generator = context.selectedDimensions().overworld();
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) return null;
        var settingsKey = noise.generatorSettings().unwrapKey()
                .orElse(NoiseGeneratorSettings.OVERWORLD);
        LevelStem overworld = context.selectedDimensions().get(LevelStem.OVERWORLD)
                .orElseThrow(() -> new IllegalStateException("Overworld dimension is unavailable"));
        return new PreviewInput(noise.getBiomeSource(), noise.generatorSettings(), settingsKey,
                context.worldgenLoadContext(), overworld.type().value().minY(),
                overworld.type().value().height());
    }

    /**
     * Builds and configures a new noise generator for this one preview. This
     * must never mutate the generator retained by WorldCreationContext: a
     * cancelled worker is intentionally not joined before world creation.
     */
    private RingTerrainPreview generate(PreviewInput input, long seed) {
        if (input == null) return null;
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                input.biomeSource(), input.settings());
        if (!((Object)generator instanceof RingWorldGeneratorAccess access)) return null;
        RandomState randomState = RandomState.create(
                input.worldgenLoadContext().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, input.settings().value());
        LevelHeightAccessor height = LevelHeightAccessor.create(input.minY(), input.height());
        access.ringworld$setGeometry(geometry);
        access.ringworld$setTerrainNoiseMapping(RingTerrainNoiseMapping.CURRENT);
        access.ringworld$setGenerationSettings(generationSettings, seed);
        return RingTerrainPreviewSampler.generate(
                previewHash(seed), geometry, RingTerrainPreviewStage.CURRENT,
                generator, randomState, height);
    }

    private long previewHash(long seed) {
        long value = seed ^ Integer.toUnsignedLong(geometry.circumferenceBlocks()) << 32;
        value ^= Integer.toUnsignedLong(geometry.widthBlocks());
        value ^= (long)generationSettings.layout().id() << 12;
        if (generationSettings.continuousRiver()) value ^= 0x52495645524C4F4FL;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value;
    }

    private void upload(RingTerrainPreview preview) {
        releaseTexture();
        NativeImage image = new NativeImage(preview.columns(), preview.rows(), false);
        double spacingX = (double)geometry.circumferenceBlocks() / preview.columns();
        double spacingZ = (double)geometry.widthBlocks() / preview.rows();
        for (int row = 0; row < preview.rows(); row++) {
            int lower = Math.max(0, row - 1);
            int upper = Math.min(preview.rows() - 1, row + 1);
            for (int displayColumn = 0; displayColumn < preview.columns(); displayColumn++) {
                int column = preview.centeredSeamSourceColumn(displayColumn);
                int left = Math.floorMod(column - 1, preview.columns());
                int right = Math.floorMod(column + 1, preview.columns());
                int shaded = RingSurfaceLod.shadeSurfaceColor(
                        preview.color(column, row), preview.height(column, row),
                        preview.height(left, row), preview.height(right, row),
                        preview.height(column, lower), preview.height(column, upper),
                        spacingX, spacingZ);
                image.setPixel(displayColumn, row, 0xFF000000 | shaded);
            }
        }
        texture = new DynamicTexture(() -> "RingWorld creation seed preview", image);
        minecraft.getTextureManager().register(TEXTURE, texture);
        texture.upload();
        lastPreviewHash = preview.worldHash();
    }

    private void releaseTexture() {
        if (texture == null || minecraft == null) return;
        minecraft.getTextureManager().release(TEXTURE);
        texture = null;
    }

    @Override
    public void removed() {
        requests.begin();
        if (running != null) running.cancel(true);
        releaseTexture();
        super.removed();
    }

    @Override
    public void onClose() {
        RingMinecraftClientAccess.setScreen(minecraft, parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float deltaTicks) {
        int panelWidth = Math.min(620, Math.max(304, width - 16));
        int left = (width - panelWidth) / 2;
        int top = 12;
        int bottom = height - 38;
        graphics.fill(left, top, left + panelWidth, bottom, PANEL_COLOR);
        graphics.outline(left, top, panelWidth, bottom - top, BORDER_COLOR);
        super.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
        graphics.centeredText(font, title, width / 2, editor == null ? top + 10 : 13,
                0xFFFFFFFF);
        graphics.text(font, Component.literal("Seed"), left + 8,
                editor == null ? 41 : 50, LABEL_COLOR);
        if (editor != null) {
            String applied = editor.selectedSeed().isBlank() ? "(random)" : editor.selectedSeed();
            String label = "Applied seed: " + applied;
            if (font.width(label) > panelWidth - 16)
                label = font.plainSubstrByWidth(label, panelWidth - 32) + "…";
            graphics.text(font, Component.literal(label), left + 8, 83, LABEL_COLOR);
        }

        int mapLeft = left + 8;
        int mapRight = left + panelWidth - 8;
        int mapTop = editor == null ? 88 : 95;
        int mapHeight = Math.max(48, Math.min(160, bottom - mapTop - 48));
        graphics.fill(mapLeft - 1, mapTop - 1, mapRight + 1, mapTop + mapHeight + 1,
                0xFF343A42);
        graphics.fill(mapLeft, mapTop, mapRight, mapTop + mapHeight, 0xFF20242A);
        if (texture != null) {
            int zoomBlocks = zoomBlocks(mapRight - mapLeft, mapHeight);
            double fraction = editor == null ? 1.0 : (double)zoomBlocks / geometry.circumferenceBlocks();
            int shownHeight = editor == null
                    ? Math.max(1, Math.min(mapHeight, (int)Math.round(
                            (mapRight - mapLeft) * (double)geometry.widthBlocks()
                                    / geometry.circumferenceBlocks())))
                    : Math.max(1, Math.min(mapHeight, (int)Math.round(
                            (mapRight - mapLeft) * (double)geometry.widthBlocks() / zoomBlocks)));
            int shownTop = mapTop + (mapHeight - shownHeight) / 2;
            float u0 = editor == null ? 0.0F
                    : (float)windowStartBlock / geometry.circumferenceBlocks();
            graphics.blit(TEXTURE, mapLeft, shownTop, mapRight, shownTop + shownHeight,
                    u0, (float)(u0 + fraction), 0.0F, 1.0F);
            if (editor != null) {
                int overviewY = mapTop + mapHeight + 29;
                graphics.fill(mapLeft, overviewY, mapRight, overviewY + 6, 0xFF405457);
                graphics.blit(TEXTURE, mapLeft, overviewY, mapRight, overviewY + 6,
                        0.0F, 1.0F, 0.0F, 1.0F);
                int selectionX = mapLeft + (int)((mapRight - mapLeft) * (double)windowStartBlock
                        / geometry.circumferenceBlocks());
                int selectionWidth = Math.max(2, (int)Math.round((mapRight - mapLeft) * fraction));
                graphics.outline(selectionX, overviewY - 1, selectionWidth, 8, 0xFFFFE29B);
            }
        }
        String status = editor == null ? state : state + " · "
                + zoomBlocks(mapRight - mapLeft, mapHeight) + " × "
                + geometry.widthBlocks() + " blocks";
        graphics.text(font, Component.literal(status), mapLeft,
                mapTop + mapHeight + 8, error.isEmpty() ? LABEL_COLOR : ERROR_COLOR);
        if (!error.isEmpty()) {
            graphics.centeredText(font, Component.literal(error), width / 2,
                    mapTop + mapHeight + (editor == null ? 22 : 20), ERROR_COLOR);
        } else {
            graphics.centeredText(font, Component.literal(
                            "Approximate terrain · no chunks, structures, caves, or save created"),
                    width / 2, mapTop + mapHeight + (editor == null ? 22 : 20), LABEL_COLOR);
        }
        if (editor == null || height >= 350) {
            graphics.centeredText(font, Component.literal(
                            "Full ring: %,d × %,d blocks · %s".formatted(
                                    geometry.circumferenceBlocks(), geometry.widthBlocks(),
                                    aspectLabel())),
                    width / 2, mapTop + mapHeight + (editor == null ? 34 : 47), LABEL_COLOR);
        }
    }

    private String aspectLabel() {
        int divisor = greatestCommonDivisor(
                geometry.circumferenceBlocks(), geometry.widthBlocks());
        return (geometry.circumferenceBlocks() / divisor) + ":"
                + (geometry.widthBlocks() / divisor);
    }

    private static int greatestCommonDivisor(int left, int right) {
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return Math.max(1, Math.abs(left));
    }

    private record PreviewInput(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings,
                                ResourceKey<NoiseGeneratorSettings> settingsKey,
                                RegistryAccess.Frozen worldgenLoadContext, int minY, int height) { }

    private record Result(RingTerrainPreview preview,
                          long elapsedMillis, String error) { }

    void ringworld$automationSetSeed(String seed) {
        seedField.setValue(seed);
    }

    void ringworld$automationUseSeed() {
        useSeedButton.onPress(RingWorldCreationScreen.AutomationInput.INSTANCE);
    }

    boolean ringworld$automationAppliedSeedIs(String value) {
        return editor != null && editor.selectedSeed().equals(value);
    }

    boolean ringworld$automationVanillaSeedIs(String value) {
        return owner.ringworld$seedText().equals(value);
    }

    boolean ringworld$automationReady() {
        return texture != null && state.startsWith("Ready") && error.isEmpty();
    }

    long ringworld$automationPreviewHash() {
        return lastPreviewHash;
    }

    void ringworld$automationDone() {
        onClose();
    }
}
