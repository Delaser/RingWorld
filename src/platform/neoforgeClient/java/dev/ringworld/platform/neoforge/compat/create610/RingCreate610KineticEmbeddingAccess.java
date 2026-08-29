package dev.ringworld.platform.neoforge.compat.create610;

import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Internal identity-owned access implemented by Flywheel's exact storage mixin. */
public interface RingCreate610KineticEmbeddingAccess {
    void ringworld$updateKineticEmbeddings(
            RenderContext context, Vec3i renderOrigin, RingGeometry geometry);

    RingCreate610KineticEmbeddingOwner.Snapshot ringworld$kineticEmbeddingSnapshot(
            BlockEntity blockEntity);
}
