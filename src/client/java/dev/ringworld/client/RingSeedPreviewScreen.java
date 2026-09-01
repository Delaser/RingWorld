package dev.ringworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPreviewRequestGate;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewSampler;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWorldGeneratorAccess;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Fast, chunk-free preview of the pending seed wrapped across the whole ring. */
public final class RingSeedPreviewScreen extends Screen {
    private static final int PANEL_COLOR = 0xE0101116;
    private static final int BORDER_COLOR = 0xFF606872;
    private static final int LABEL_COLOR = 0xFFB8BDC5;
    private static final int ERROR_COLOR = 0xFFFF7070;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ringworld", "dynamic/creation_seed_preview");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RingWorld creation seed preview");
        thread.setDaemon(true);
        return thread;
    });

    private final RingWorldCreationScreen parent;
    private final RingWorldCreationScreen.LayoutButtonOwner owner;
    private final RingGeometry geometry;
    private final RingPreviewRequestGate<Result> requests = new RingPreviewRequestGate<>();
    private EditBox seedField;
    private Future<?> running;
    private long request;
    private int debounceTicks;
    private DynamicTexture texture;
    private String state = "Waiting";
    private String error = "";
    private long lastPreviewHash = Long.MIN_VALUE;
    private int automationCancellations;

    public RingSeedPreviewScreen(RingWorldCreationScreen parent,
                                 RingWorldCreationScreen.LayoutButtonOwner owner,
                                 RingGeometry geometry) {
        super(Component.literal("Ring seed preview"));
        this.parent = parent;
        this.owner = owner;
        this.geometry = geometry;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(620, Math.max(304, width - 16));
        int left = (width - panelWidth) / 2;
        seedField = new EditBox(font, left + 8, 53, panelWidth - 112, 20,
                Component.literal("Seed"));
        seedField.setMaxLength(64);
        seedField.setValue(owner.ringworld$seedText());
        seedField.setResponder(value -> {
            owner.ringworld$setSeedText(value);
            schedule();
        });
        addRenderableWidget(seedField);
        addRenderableWidget(Button.builder(Component.literal("Reroll"), button ->
                seedField.setValue(Long.toString(WorldOptions.randomSeed())))
                .bounds(left + panelWidth - 98, 53, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
        schedule();
    }

    private void schedule() {
        request = requests.begin();
        debounceTicks = 5;
        error = "";
        state = "Preparing…";
        if (running != null && !running.isDone()) {
            running.cancel(true);
            automationCancellations++;
        }
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
        long seed = owner.ringworld$resolvedSeed();
        // WorldCreationContext is consulted only on the client thread. The
        // worker receives immutable registry holders and primitive dimensions.
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

    /** Snapshots immutable worldgen inputs before the detached worker starts. */
    private static PreviewInput snapshot(WorldCreationContext context) {
        ChunkGenerator selected = context.selectedDimensions().overworld();
        if (!(selected instanceof NoiseBasedChunkGenerator noise)) return null;
        ResourceKey<NoiseGeneratorSettings> settingsKey = noise.generatorSettings().unwrapKey()
                .orElse(NoiseGeneratorSettings.OVERWORLD);
        LevelStem overworld = context.selectedDimensions().get(LevelStem.OVERWORLD)
                .orElseThrow(() -> new IllegalStateException("Overworld dimension is unavailable"));
        return new PreviewInput(noise.getBiomeSource(), noise.generatorSettings(), settingsKey,
                context.worldgenLoadContext(), overworld.type().value().minY(),
                overworld.type().value().height());
    }

    /**
     * Constructs a fresh generator and RandomState. The generator retained by
     * WorldCreationContext is never configured or passed to the worker.
     */
    private RingTerrainPreview generate(PreviewInput input, long seed) {
        if (input == null) return null;
        ChunkGenerator detached = new NoiseBasedChunkGenerator(
                input.biomeSource(), input.settings());
        if (!(detached instanceof RingWorldGeneratorAccess access)) return null;
        RandomState randomState = RandomState.create(
                input.worldgenLoadContext().asGetterLookup(), input.settingsKey(), seed);
        LevelHeightAccessor height = LevelHeightAccessor.create(input.minY(), input.height());
        access.ringworld$setGeometry(geometry);
        access.ringworld$setTerrainNoiseMapping(RingTerrainNoiseMapping.CURRENT);
        RingTerrainPreviewSampler.Source source = RingTerrainPreviewSampler.generatorSource(
                detached, randomState, height);
        return source == null ? null : RingTerrainPreviewSampler.generate(
                previewHash(seed), geometry, RingTerrainPreviewStage.CURRENT, source);
    }

    private long previewHash(long seed) {
        long value = seed ^ Integer.toUnsignedLong(geometry.circumferenceBlocks()) << 32;
        value ^= Integer.toUnsignedLong(geometry.widthBlocks());
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
        try {
            for (int row = 0; row < preview.rows(); row++) {
                int lower = Math.max(0, row - 1);
                int upper = Math.min(preview.rows() - 1, row + 1);
                for (int displayColumn = 0;
                     displayColumn < preview.columns(); displayColumn++) {
                    int column = preview.centeredSeamSourceColumn(displayColumn);
                    int left = Math.floorMod(column - 1, preview.columns());
                    int right = Math.floorMod(column + 1, preview.columns());
                    int shaded = RingSurfaceLod.shadeSurfaceColor(
                            preview.color(column, row), preview.height(column, row),
                            preview.height(left, row), preview.height(right, row),
                            preview.height(column, lower), preview.height(column, upper),
                            spacingX, spacingZ);
                    image.setPixelRGBA(displayColumn, row,
                            FastColor.ABGR32.fromArgb32(0xFF000000 | shaded));
                }
            }
            texture = new DynamicTexture(image);
            minecraft.getTextureManager().register(TEXTURE, texture);
            texture.upload();
            lastPreviewHash = preview.worldHash();
        } catch (RuntimeException | Error exception) {
            if (texture == null) image.close();
            throw exception;
        }
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
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(graphics, mouseX, mouseY, deltaTicks);
        int panelWidth = Math.min(620, Math.max(304, width - 16));
        int left = (width - panelWidth) / 2;
        int top = 12;
        int bottom = height - 38;
        graphics.fill(left, top, left + panelWidth, bottom, PANEL_COLOR);
        graphics.renderOutline(left, top, panelWidth, bottom - top, BORDER_COLOR);
        super.render(graphics, mouseX, mouseY, deltaTicks);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("Seed"), left + 8, 41, LABEL_COLOR);

        int mapLeft = left + 8;
        int mapRight = left + panelWidth - 8;
        int mapTop = 88;
        int mapHeight = Math.max(48, Math.min(160, bottom - mapTop - 48));
        graphics.fill(mapLeft - 1, mapTop - 1, mapRight + 1, mapTop + mapHeight + 1,
                0xFF343A42);
        graphics.fill(mapLeft, mapTop, mapRight, mapTop + mapHeight, 0xFF20242A);
        if (texture != null) {
            int textureWidth = mapRight - mapLeft;
            int textureHeight = Math.max(1, Math.min(mapHeight, (int)Math.round(
                    textureWidth * (double)geometry.widthBlocks()
                            / geometry.circumferenceBlocks())));
            int textureTop = mapTop + (mapHeight - textureHeight) / 2;
            graphics.blit(TEXTURE, mapLeft, textureTop, 0, 0.0F, 0.0F,
                    textureWidth, textureHeight, previewColumns(), previewRows());
        }
        graphics.drawCenteredString(font, Component.literal(state), width / 2,
                mapTop + mapHeight + 10, error.isEmpty() ? LABEL_COLOR : ERROR_COLOR);
        if (!error.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal(error), width / 2,
                    mapTop + mapHeight + 22, ERROR_COLOR);
        } else {
            graphics.drawCenteredString(font, Component.literal(
                            "Approximate terrain · no chunks, structures, caves, or save created"),
                    width / 2, mapTop + mapHeight + 22, LABEL_COLOR);
        }
        graphics.drawCenteredString(font, Component.literal(
                        "Full ring: %,d × %,d blocks · %s".formatted(
                                geometry.circumferenceBlocks(), geometry.widthBlocks(),
                                aspectLabel())),
                width / 2, mapTop + mapHeight + 34, LABEL_COLOR);
    }

    private int previewColumns() {
        return Math.min(geometry.circumferenceBlocks(),
                RingTerrainPreviewStage.CURRENT.colorColumns());
    }

    private int previewRows() {
        return Math.min(geometry.widthBlocks(), RingTerrainPreviewStage.CURRENT.colorRows());
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
                                RegistryAccess.Frozen worldgenLoadContext,
                                int minY, int height) { }

    private record Result(RingTerrainPreview preview, long elapsedMillis, String error) { }

    void ringworld$automationSetSeed(String seed) {
        seedField.setValue(seed);
    }

    boolean ringworld$automationReady() {
        return texture != null && state.startsWith("Ready") && error.isEmpty();
    }

    long ringworld$automationPreviewHash() {
        return lastPreviewHash;
    }

    boolean ringworld$automationGenerating() {
        return running != null && !running.isDone() && state.startsWith("Generating");
    }

    int ringworld$automationCancellationCount() {
        return automationCancellations;
    }

    String ringworld$automationAspectLabel() {
        return aspectLabel();
    }

    boolean ringworld$automationReleased() {
        return texture == null
                && (running == null || running.isCancelled() || running.isDone());
    }

    void ringworld$automationDone() {
        onClose();
    }
}
