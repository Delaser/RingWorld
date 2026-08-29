package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.RingWorldMod;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610FlywheelTransform;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientDiagnostics;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Curves only Flywheel's exact Create contraption embedding. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.render.ContraptionVisual",
        remap = false)
abstract class ContraptionVisualMixin {
    @Unique private int ringworld$diagnosticEntityId = -1;
    @Unique private AbstractContraptionEntity ringworld$diagnosticEntity;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void ringworld$traceVisualCreate(
            VisualizationContext context, AbstractContraptionEntity entity, float partialTick,
            CallbackInfo ci) {
        ringworld$diagnosticEntityId = entity.getId();
        ringworld$diagnosticEntity = entity;
        RingCreate610ClientDiagnostics.recordVisualCreate(entity.getId(), this);
        if (Boolean.getBoolean("ringworld.createCompatBearing")) {
            RingWorldMod.LOGGER.info(
                    "[create-bearing] ContraptionVisual create entity={}/{} type={} object={} visual={}",
                    entity.getId(), entity.getUUID(), entity.getClass().getName(),
                    System.identityHashCode(entity), System.identityHashCode(this));
        }
    }

    @Inject(method = "_delete", at = @At("HEAD"), require = 1)
    private void ringworld$traceVisualDelete(CallbackInfo ci) {
        RingCreate610ClientDiagnostics.recordVisualDelete(ringworld$diagnosticEntityId, this);
        if (Boolean.getBoolean("ringworld.createCompatBearing")) {
            RingWorldMod.LOGGER.info(
                    "[create-bearing] ContraptionVisual delete entityId={} visual={}",
                    ringworld$diagnosticEntityId, System.identityHashCode(this));
        }
    }

    @Redirect(method = "setEmbeddingMatrices(F)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"),
            require = 1, allow = 1)
    private void ringworld$curveEmbeddingTranslation(
            PoseStack matrices, double x, double y, double z,
            @Local(name = "origin") Vec3i origin) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        if (geometry == null || client.level == null
                || client.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            matrices.translate(x, y, z);
            return;
        }

        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        RingCreate610FlywheelTransform.applyCurvedEmbedding(
                matrices, new Vec3(x, y, z), camera, origin, geometry);
        float angle = ringworld$diagnosticEntity instanceof ControlledContraptionEntity controlled
                ? controlled.getAngle(1.0F) : Float.NaN;
        RingCreate610ClientDiagnostics.recordCurvedEmbeddingTransform(
                ringworld$diagnosticEntityId, angle, matrices.last().pose());
    }
}
