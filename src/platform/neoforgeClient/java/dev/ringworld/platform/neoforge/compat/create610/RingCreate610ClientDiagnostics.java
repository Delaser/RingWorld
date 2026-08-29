package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.world.RingGeometry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

/** Fixture-only counters; no gameplay or session state depends on them. */
public final class RingCreate610ClientDiagnostics {
    private static final String PROPERTY = "ringworld.createCompatClient";
    private static final String BEARING_PROPERTY = "ringworld.createCompatBearing";
    private static final String WINDMILL_PROPERTY = "ringworld.createCompatWindmill";
    private static final String KINETIC_VISUAL_PROPERTY = "ringworld.createCompatKineticVisual";
    private static final String LINEAR_PROPERTY = "ringworld.createCompatLinear";
    private static final boolean OFF_CONTRAPTION_LAYER_DIAGNOSTICS =
            Boolean.getBoolean(BEARING_PROPERTY) || Boolean.getBoolean(LINEAR_PROPERTY);
    private static final AtomicInteger ATTACHED_CONTROLLER_READS = new AtomicInteger();
    private static final AtomicInteger DETACHED_CONTROLLER_READS = new AtomicInteger();
    private static final AtomicInteger CURVED_EMBEDDING_TRANSFORMS = new AtomicInteger();
    private static final AtomicInteger DEFERRED_CONTROLLER_REPAIRS = new AtomicInteger();
    private static final AtomicInteger NON_FINITE_EMBEDDING_MATRICES = new AtomicInteger();
    private static final AtomicInteger GANTRY_OFFSET_PROJECTIONS = new AtomicInteger();
    private static final ConcurrentMap<Integer, Integer> VISUAL_IDENTITIES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, AtomicInteger> VISUAL_CREATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, AtomicInteger> VISUAL_DELETES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, AtomicInteger> ENTITY_LEAVES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, AtomicInteger> ENTITY_TRANSFORMS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, List<EntityTransformSample>> ENTITY_TRANSFORM_SAMPLES =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, ConcurrentMap<String, OffLayerSample>>
            OFF_CONTRAPTION_LAYERS = new ConcurrentHashMap<>();
    private static volatile String firstEmbeddingMatrix;
    private static volatile String lastEmbeddingMatrix;
    private static volatile BlockPos previewFirst;
    private static volatile BlockPos previewSecond;
    private static volatile boolean previewCanConnect;
    private static volatile boolean kineticGeometrySuppressed;

    private RingCreate610ClientDiagnostics() { }

    public static void recordControllerRead(BlockEntity owner) {
        if (!enabled()) return;
        (owner.getLevel() == null ? DETACHED_CONTROLLER_READS : ATTACHED_CONTROLLER_READS)
                .incrementAndGet();
    }

    public static void recordCurvedEmbeddingTransform(Matrix4f matrix) {
        if (!enabled()) return;
        int count = CURVED_EMBEDDING_TRANSFORMS.incrementAndGet();
        float[] values = new float[16];
        matrix.get(values);
        for (float value : values) {
            if (!Float.isFinite(value)) {
                NON_FINITE_EMBEDDING_MATRICES.incrementAndGet();
                return;
            }
        }
        if (count == 1) firstEmbeddingMatrix = matrixSample(values);
        if (count == 1 || count % 64 == 0) lastEmbeddingMatrix = matrixSample(values);
    }

    public static void recordCurvedEmbeddingTransform(int entityId, float angle, Matrix4f matrix) {
        recordCurvedEmbeddingTransform(matrix);
        if (!enabled()) return;
        int count = ENTITY_TRANSFORMS.computeIfAbsent(
                entityId, ignored -> new AtomicInteger()).incrementAndGet();
        if (count != 1 && count % 8 != 0) return;
        float[] values = new float[16];
        matrix.get(values);
        for (float value : values) if (!Float.isFinite(value)) return;
        List<EntityTransformSample> samples = ENTITY_TRANSFORM_SAMPLES.computeIfAbsent(
                entityId, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()));
        synchronized (samples) {
            if (samples.size() == 64) samples.removeFirst();
            samples.add(new EntityTransformSample(count, angle, matrixSample(values)));
        }
    }

    public static List<EntityTransformSample> entityTransformSamples(int entityId) {
        List<EntityTransformSample> samples = ENTITY_TRANSFORM_SAMPLES.get(entityId);
        if (samples == null) return List.of();
        synchronized (samples) { return List.copyOf(samples); }
    }

    /** Records the exact backend-OFF SBB sink only while a graphical fixture is active. */
    public static void recordOffContraptionLayer(
            int entityId, String sourceLayer, String mappedLayer, String shaderKind,
            boolean chunkTerrainLayer, Matrix4f modelViewProjection) {
        if (!OFF_CONTRAPTION_LAYER_DIAGNOSTICS) return;
        ShaderInstance shader = switch (shaderKind) {
            case "entity-solid" -> GameRenderer.getRendertypeEntitySolidShader();
            case "entity-cutout" -> GameRenderer.getRendertypeEntityCutoutShader();
            case "entity-translucent-cull" ->
                    GameRenderer.getRendertypeEntityTranslucentCullShader();
            default -> null;
        };
        float[] values = new float[16];
        modelViewProjection.get(values);
        for (float value : values) if (!Float.isFinite(value)) return;
        OFF_CONTRAPTION_LAYERS.computeIfAbsent(
                        entityId, ignored -> new ConcurrentHashMap<>())
                .put(sourceLayer, new OffLayerSample(
                        sourceLayer, mappedLayer, shader == null ? "missing" : shader.getName(),
                        shader != null && shader.getUniform("RingWorldLayout") != null,
                        chunkTerrainLayer, matrixSample(values)));
    }

    /** Avoids constructing detailed OFF-render evidence outside its isolated fixtures. */
    public static boolean offContraptionLayerDiagnosticsEnabled() {
        return OFF_CONTRAPTION_LAYER_DIAGNOSTICS;
    }

    public static List<OffLayerSample> offContraptionLayerSamples(int entityId) {
        var samples = OFF_CONTRAPTION_LAYERS.get(entityId);
        if (samples == null) return List.of();
        return samples.values().stream()
                .sorted(java.util.Comparator.comparing(OffLayerSample::sourceLayer))
                .toList();
    }

    public static void recordVisualCreate(int entityId, Object visual) {
        if (!enabled()) return;
        VISUAL_IDENTITIES.put(entityId, System.identityHashCode(visual));
        VISUAL_CREATES.computeIfAbsent(entityId, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public static void recordVisualDelete(int entityId, Object visual) {
        if (!enabled()) return;
        VISUAL_DELETES.computeIfAbsent(entityId, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public static void recordEntityLeave(int entityId) {
        if (!enabled()) return;
        ENTITY_LEAVES.computeIfAbsent(entityId, ignored -> new AtomicInteger()).incrementAndGet();
    }

    /** Records only fixture-enabled seam projections; ordinary clients remain silent. */
    public static void recordGantryOffsetProjection(
            int entityId, double axisCoord, double originalOffset, double projectedOffset) {
        if (!Boolean.getBoolean(LINEAR_PROPERTY)
                || Double.doubleToLongBits(originalOffset)
                == Double.doubleToLongBits(projectedOffset)
                || Math.abs(originalOffset - projectedOffset) < 1.0
                || GANTRY_OFFSET_PROJECTIONS.incrementAndGet() > 8) {
            return;
        }
        dev.ringworld.RingWorldMod.LOGGER.info(
                "[create-linear] gantry-offset-projection entity={} axis={} rawDiff={} projectedDiff={}",
                entityId, axisCoord, originalOffset, projectedOffset);
    }

    public static int visualIdentity(int entityId) {
        return VISUAL_IDENTITIES.getOrDefault(entityId, -1);
    }

    public static int visualCreateCount(int entityId) {
        AtomicInteger count = VISUAL_CREATES.get(entityId);
        return count == null ? 0 : count.get();
    }

    public static int visualDeleteCount(int entityId) {
        AtomicInteger count = VISUAL_DELETES.get(entityId);
        return count == null ? 0 : count.get();
    }

    public static int entityLeaveCount(int entityId) {
        AtomicInteger count = ENTITY_LEAVES.get(entityId);
        return count == null ? 0 : count.get();
    }

    public static void recordDeferredControllerRepair() {
        if (enabled()) DEFERRED_CONTROLLER_REPAIRS.incrementAndGet();
    }

    public static void recordPreviewFirst(BlockPos first) {
        if (enabled()) previewFirst = first;
    }

    public static void recordPreviewCanConnect(
            BlockPos first, BlockPos second, boolean canConnect) {
        if (!enabled()) return;
        previewFirst = first;
        previewSecond = second;
        previewCanConnect = canConnect;
    }

    public static Snapshot snapshot() {
        return new Snapshot(ATTACHED_CONTROLLER_READS.get(), DETACHED_CONTROLLER_READS.get(),
                CURVED_EMBEDDING_TRANSFORMS.get(),
                DEFERRED_CONTROLLER_REPAIRS.get(),
                NON_FINITE_EMBEDDING_MATRICES.get(),
                firstEmbeddingMatrix, lastEmbeddingMatrix,
                previewFirst, previewSecond, previewCanConnect);
    }

    public static void reset() {
        ATTACHED_CONTROLLER_READS.set(0);
        DETACHED_CONTROLLER_READS.set(0);
        CURVED_EMBEDDING_TRANSFORMS.set(0);
        DEFERRED_CONTROLLER_REPAIRS.set(0);
        NON_FINITE_EMBEDDING_MATRICES.set(0);
        GANTRY_OFFSET_PROJECTIONS.set(0);
        VISUAL_IDENTITIES.clear();
        VISUAL_CREATES.clear();
        VISUAL_DELETES.clear();
        ENTITY_LEAVES.clear();
        ENTITY_TRANSFORMS.clear();
        ENTITY_TRANSFORM_SAMPLES.clear();
        OFF_CONTRAPTION_LAYERS.clear();
        firstEmbeddingMatrix = null;
        lastEmbeddingMatrix = null;
        previewFirst = null;
        previewSecond = null;
        previewCanConnect = false;
        kineticGeometrySuppressed = false;
    }

    /** Exact-fixture hook; production and Create-absent runs always return the input. */
    public static RingGeometry kineticEmbeddingGeometry(RingGeometry geometry) {
        return Boolean.getBoolean(KINETIC_VISUAL_PROPERTY) && kineticGeometrySuppressed
                ? null : geometry;
    }

    public static void suppressKineticEmbeddingGeometryForFixture(boolean suppressed) {
        if (Boolean.getBoolean(KINETIC_VISUAL_PROPERTY)) {
            kineticGeometrySuppressed = suppressed;
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean(PROPERTY) || Boolean.getBoolean(BEARING_PROPERTY)
                || Boolean.getBoolean(WINDMILL_PROPERTY)
                || Boolean.getBoolean(LINEAR_PROPERTY);
    }

    private static String matrixSample(float[] values) {
        return String.format(Locale.ROOT,
                "[%.5f,%.5f,%.5f,%.5f|%.5f,%.5f,%.5f,%.5f|%.5f,%.5f,%.5f,%.5f|%.5f,%.5f,%.5f,%.5f]",
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]);
    }

    public record Snapshot(int attachedControllerReads, int detachedControllerReads,
                           int curvedEmbeddingTransforms,
                           int deferredControllerRepairs,
                           int nonFiniteEmbeddingMatrices,
                           String firstEmbeddingMatrix, String lastEmbeddingMatrix,
                           BlockPos previewFirst, BlockPos previewSecond,
                           boolean previewCanConnect) { }

    public record EntityTransformSample(int transformIndex, float angle, String matrix) { }

    public record OffLayerSample(
            String sourceLayer, String mappedLayer, String shaderName,
            boolean ringWorldLayoutUniform, boolean chunkTerrainLayer,
            String modelViewProjection) { }
}
