package dev.ringworld.platform.neoforge.compat.create610.mixin;

import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientDiagnostics;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610KineticEmbeddingAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Updates exact Create KBE child embeddings after frame planning and before GPU work. */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
abstract class VisualizationManagerKineticEmbeddingMixin {
    private static final String RENDER =
            "render(Ldev/engine_room/flywheel/api/backend/RenderContext;)V";

    @Shadow @Final private LevelAccessor level;
    @Shadow @Final private VisualManagerImpl<BlockEntity, BlockEntityStorage> blockEntities;
    @Shadow public abstract Vec3i renderOrigin();

    @Inject(
            method = RENDER,
            at = @At(value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/api/backend/Engine;"
                            + "render(Ldev/engine_room/flywheel/api/backend/RenderContext;)V"),
            require = 1,
            expect = 1,
            allow = 1)
    private void ringworld$updateKineticEmbeddings(RenderContext context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!(level instanceof Level clientLevel)
                || level != client.level || !clientLevel.isClientSide
                || clientLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        ((RingCreate610KineticEmbeddingAccess) (Object) blockEntities.getStorage())
                .ringworld$updateKineticEmbeddings(
                        context, renderOrigin(),
                        RingCreate610ClientDiagnostics.kineticEmbeddingGeometry(
                                ClientRingState.geometry()));
    }
}
