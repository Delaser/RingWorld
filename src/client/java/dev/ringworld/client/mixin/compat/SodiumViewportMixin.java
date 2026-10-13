package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingRenderView;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Converts canonical visibility bounds through the same immutable CPU view as vanilla. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.viewport.Viewport", remap = false)
abstract class SodiumViewportMixin {
    @Unique private RingRenderView ringworld$view;
    @Unique private FrustumIntersection ringworld$planes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ringworld$capture(@Coerce Object frustum, Vector3d position, CallbackInfo ci) {
        var geometry = ClientRingState.geometry();
        if (geometry != null && frustum instanceof SodiumSimpleFrustumAccessor accessor) {
            ringworld$view = RingRenderView.capture(geometry, new Vec3(position.x, position.y, position.z));
            ringworld$planes = accessor.ringworld$planes();
        }
    }

    @Unique private boolean ringworld$visible(double x, double y, double z, double size) {
        AABB box = ringworld$view.bounds(new AABB(x-size, y-size, z-size, x+size, y+size, z+size));
        return ringworld$planes.testAab((float)box.minX, (float)box.minY, (float)box.minZ,
                (float)box.maxX, (float)box.maxY, (float)box.maxZ);
    }

    @Inject(method = "isBoxVisible", at = @At("HEAD"), cancellable = true)
    private void ringworld$section(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$view != null) cir.setReturnValue(ringworld$visible(x,y,z,9.125));
    }
    @Inject(method = "isBoxVisibleLooser", at = @At("HEAD"), cancellable = true)
    private void ringworld$loose(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$view != null) cir.setReturnValue(ringworld$visible(x,y,z,10.125));
    }
    @Inject(method = "isBoxVisibleDirect", at = @At("HEAD"), cancellable = true)
    private void ringworld$box(float x, float y, float z, float size, CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$view != null) {
            var camera = ringworld$view.camera();
            cir.setReturnValue(ringworld$visible(x+camera.x,y+camera.y,z+camera.z,size));
        }
    }
    @Inject(method = "getBoxIntersectionDirect", at = @At("HEAD"), cancellable = true)
    private void ringworld$intersection(float x, float y, float z, float size, CallbackInfoReturnable<Integer> cir) {
        if (ringworld$view != null) {
            var camera = ringworld$view.camera();
            // A curved parent cannot mark its flat children wholly inside.
            cir.setReturnValue(ringworld$visible(x+camera.x,y+camera.y,z+camera.z,size)
                    ? FrustumIntersection.INTERSECT : FrustumIntersection.OUTSIDE);
        }
    }
}
