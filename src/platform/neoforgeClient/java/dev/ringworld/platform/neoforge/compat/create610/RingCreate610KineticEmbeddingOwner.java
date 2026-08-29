package dev.ringworld.platform.neoforge.compat.create610;

import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Per-Flywheel-storage owner of standalone Create kinetic child embeddings. */
public final class RingCreate610KineticEmbeddingOwner {
    private final RingCreate610OwnedStateTable<Object, State> states =
            new RingCreate610OwnedStateTable<>(State::deleteEmbedding);
    private long identityUpdates;
    private long curvedUpdates;
    private long malformedUpdates;
    private boolean malformedWarningEmitted;

    public Pending createIdentityEmbedding(VisualizationContext parent) {
        State state = states.createInitialized(
                () -> new State(parent.createEmbedding(parent.renderOrigin())),
                State::setInitialIdentity);
        return new Pending(state);
    }

    public void discardProvisional(Pending pending) {
        if (pending != null) states.discard(pending.state);
    }

    public void discardProvisionalSuppressing(Pending pending, Throwable primary) {
        if (pending != null) states.discardSuppressing(pending.state, primary);
    }

    public void register(Object owner, Visual visual, Pending pending) {
        pending.state.visual = visual;
        states.register(owner, pending.state);
        if (Boolean.getBoolean("ringworld.createCompatKineticVisual")
                && owner instanceof BlockEntity blockEntity) {
            RingCreate610OwnedStateTable.Counters counters = states.counters();
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] owner-register block={} visual={} embedding={} "
                            + "owned={} created={} deleted={} failedDeletes={}",
                    blockEntity.getBlockPos(), System.identityHashCode(visual),
                    System.identityHashCode(pending.state.embedding), counters.owned(),
                    counters.created(), counters.deleted(), counters.failedDeletes());
        }
    }

    public void release(Object owner) {
        states.release(owner);
    }

    public void releaseSuppressing(Object owner, Throwable primary) {
        states.releaseSuppressing(owner, primary);
    }

    public void releaseAll() {
        try {
            states.releaseAll();
        } finally {
            logReleaseAll();
        }
    }

    public void releaseAllSuppressing(Throwable primary) {
        try {
            states.releaseAllSuppressing(primary);
        } finally {
            logReleaseAll();
        }
    }

    public void update(
            Map<Object, Visual> nativeVisuals, RenderContext context,
            Vec3i renderOrigin, RingGeometry geometry) {
        for (Map.Entry<Object, State> entry : states.entriesSnapshot()) {
            Object owner = entry.getKey();
            State state = entry.getValue();
            if (nativeVisuals.get(owner) != state.visual
                    || !(owner instanceof BlockEntity blockEntity)
                    || blockEntity.isRemoved()
                    || !RingCreate610ClientCoordinates.isOwningClientLevel(blockEntity)) {
                // Ownership is removed before delete, even when delete fails.
                states.release(owner);
                continue;
            }
            apply(state, blockEntity.getBlockPos(), context.camera().getPosition(),
                    renderOrigin, geometry);
        }
    }

    void updateForTest(
            Object owner, BlockPos anchor, Vec3 camera,
            Vec3i renderOrigin, RingGeometry geometry) {
        State state = states.get(owner);
        if (state == null) throw new IllegalStateException("missing owned embedding");
        apply(state, anchor, camera, renderOrigin, geometry);
    }

    private void apply(
            State state, BlockPos anchor, Vec3 camera,
            Vec3i renderOrigin, RingGeometry geometry) {
        if (geometry == null) {
            setIdentity(state);
            return;
        }
        RingCreate610KineticEmbeddingTransformState.Result result =
                state.transform.update(anchor, camera, renderOrigin, geometry);
        state.embedding.transforms(state.transform.pose(), state.transform.normal());
        if (result == RingCreate610KineticEmbeddingTransformState.Result.IDENTITY) {
            identityUpdates++;
            return;
        }
        if (result == RingCreate610KineticEmbeddingTransformState.Result.MALFORMED) {
            identityUpdates++;
            malformedUpdates++;
            if (!malformedWarningEmitted) {
                malformedWarningEmitted = true;
                RingWorldMod.LOGGER.warn(
                        "Create kinetic visual received malformed curved embedding inputs; "
                                + "using the native identity transform for this frame");
            }
            return;
        }
        curvedUpdates++;
    }

    private void setIdentity(State state) {
        state.transform.identity();
        state.embedding.transforms(state.transform.pose(), state.transform.normal());
        identityUpdates++;
    }

    private void logReleaseAll() {
        RingCreate610OwnedStateTable.Counters counters = states.counters();
        if (counters.created() > 0
                && Boolean.getBoolean("ringworld.createCompatKineticVisual")) {
            boolean balanced = counters.owned() == 0 && counters.failedDeletes() == 0
                    && counters.created() == counters.deleted();
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] storage-invalidate owned={} created={} deleted={} "
                            + "failedDeletes={} balanced={}",
                    counters.owned(), counters.created(), counters.deleted(),
                    counters.failedDeletes(), balanced);
        }
    }

    public Snapshot snapshot(Object owner) {
        State state = states.get(owner);
        RingCreate610OwnedStateTable.Counters counters = states.counters();
        return new Snapshot(counters.owned(), counters.created(), counters.deleted(),
                counters.failedDeletes(), identityUpdates, curvedUpdates, malformedUpdates,
                state == null ? -1 : System.identityHashCode(state.visual),
                state == null ? -1 : System.identityHashCode(state.embedding),
                state != null && state.transform.curved(),
                state == null ? new Matrix4f() : new Matrix4f(state.transform.pose()));
    }

    public static final class Pending {
        private final State state;

        private Pending(State state) {
            this.state = state;
        }

        public VisualizationContext context() {
            return state.embedding;
        }
    }

    public record Snapshot(
            int ownedCount, long created, long deleted, long failedDeletes,
            long identityUpdates, long curvedUpdates, long malformedUpdates,
            int visualIdentity, int embeddingIdentity, boolean curved,
            Matrix4f pose) {
        public boolean finitePose() {
            float[] values = pose.get(new float[16]);
            for (float value : values) if (!Float.isFinite(value)) return false;
            return true;
        }
    }

    private static final class State {
        private Visual visual;
        private final VisualEmbedding embedding;
        private final RingCreate610KineticEmbeddingTransformState transform =
                new RingCreate610KineticEmbeddingTransformState();

        private State(VisualEmbedding embedding) {
            this.embedding = embedding;
        }

        private void setInitialIdentity() {
            embedding.transforms(new Matrix4f(), new Matrix3f());
        }

        private void deleteEmbedding() {
            embedding.delete();
        }
    }
}
