package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610KineticEmbeddingAccess;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610KineticEmbeddingOwner;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientCoordinates;
import dev.ringworld.world.RingGeometry;
import java.util.Map;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Gives exact Create world kinetic visuals an identity-owned child environment. */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.Storage", remap = false)
abstract class StorageKineticEmbeddingMixin implements RingCreate610KineticEmbeddingAccess {
    private static final String ADD =
            "add(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Ljava/lang/Object;F)V";
    private static final String RECREATE =
            "lambda$recreateAll$4(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;F"
                    + "Ljava/lang/Object;Ldev/engine_room/flywheel/api/visual/Visual;)"
                    + "Ldev/engine_room/flywheel/api/visual/Visual;";

    @Shadow @Final private Map<Object, Visual> visuals;
    @Unique private final RingCreate610KineticEmbeddingOwner ringworld$kineticEmbeddings =
            new RingCreate610KineticEmbeddingOwner();

    @WrapMethod(method = ADD, require = 1, expect = 1, allow = 1)
    private void ringworld$wrapAdd(
            VisualizationContext context, Object object, float partialTick,
            Operation<Void> original) {
        if (!ringworld$eligible(object) || visuals.get(object) != null) {
            original.call(context, object, partialTick);
            return;
        }

        RingCreate610KineticEmbeddingOwner.Pending child =
                ringworld$kineticEmbeddings.createIdentityEmbedding(context);
        try {
            original.call(child.context(), object, partialTick);
        } catch (RuntimeException | Error failure) {
            ringworld$kineticEmbeddings.discardProvisionalSuppressing(child, failure);
            throw failure;
        }
        Visual visual = visuals.get(object);
        if (visual == null) ringworld$kineticEmbeddings.discardProvisional(child);
        else ringworld$kineticEmbeddings.register(object, visual, child);
    }

    @WrapMethod(method = RECREATE, require = 1, expect = 1, allow = 1)
    private Visual ringworld$wrapRecreate(
            VisualizationContext context, float partialTick, Object object, Visual oldVisual,
            Operation<Visual> original) {
        if (!ringworld$eligible(object)) {
            Throwable primary = null;
            try {
                return original.call(context, partialTick, object, oldVisual);
            } catch (RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                if (primary == null) ringworld$kineticEmbeddings.release(object);
                else ringworld$kineticEmbeddings.releaseSuppressing(object, primary);
            }
        }

        RingCreate610KineticEmbeddingOwner.Pending child =
                ringworld$kineticEmbeddings.createIdentityEmbedding(context);
        Visual replacement;
        try {
            replacement = original.call(child.context(), partialTick, object, oldVisual);
        } catch (RuntimeException | Error failure) {
            ringworld$kineticEmbeddings.discardProvisionalSuppressing(child, failure);
            ringworld$kineticEmbeddings.releaseSuppressing(object, failure);
            throw failure;
        }
        // Native old-visual deletion and replacement setup have both completed here.
        if (replacement == null) ringworld$discardAndRelease(child, object);
        else ringworld$kineticEmbeddings.register(object, replacement, child);
        return replacement;
    }

    @WrapMethod(method = "remove(Ljava/lang/Object;)V", require = 1, expect = 1, allow = 1)
    private void ringworld$wrapRemove(Object object, Operation<Void> original) {
        Throwable primary = null;
        try {
            original.call(object);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary == null) ringworld$kineticEmbeddings.release(object);
            else ringworld$kineticEmbeddings.releaseSuppressing(object, primary);
        }
    }

    @WrapMethod(method = "invalidate()V", require = 1, expect = 1, allow = 1)
    private void ringworld$wrapInvalidate(Operation<Void> original) {
        Throwable primary = null;
        try {
            original.call();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary == null) ringworld$kineticEmbeddings.releaseAll();
            else ringworld$kineticEmbeddings.releaseAllSuppressing(primary);
        }
    }

    @Override
    public void ringworld$updateKineticEmbeddings(
            dev.engine_room.flywheel.api.backend.RenderContext context,
            Vec3i renderOrigin, RingGeometry geometry) {
        ringworld$kineticEmbeddings.update(visuals, context, renderOrigin, geometry);
    }

    @Override
    public RingCreate610KineticEmbeddingOwner.Snapshot ringworld$kineticEmbeddingSnapshot(
            BlockEntity blockEntity) {
        return ringworld$kineticEmbeddings.snapshot(blockEntity);
    }

    @Unique
    private boolean ringworld$eligible(Object object) {
        return (Object) this instanceof BlockEntityStorage
                && object instanceof KineticBlockEntity kinetic
                && RingCreate610ClientCoordinates.isOwningClientLevel(kinetic);
    }

    @Unique
    private void ringworld$discardAndRelease(
            RingCreate610KineticEmbeddingOwner.Pending child, Object owner) {
        try {
            ringworld$kineticEmbeddings.discardProvisional(child);
        } catch (RuntimeException | Error failure) {
            ringworld$kineticEmbeddings.releaseSuppressing(owner, failure);
            throw failure;
        }
        ringworld$kineticEmbeddings.release(owner);
    }
}
