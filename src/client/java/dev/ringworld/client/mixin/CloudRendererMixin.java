package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingCloudBounds;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep CPU cloud faces/offsets on the same dynamic deck as the curved shader. */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Unique private boolean ringworld$curvedFaces;
    @Shadow public abstract void markForRebuild();
    @Shadow private void encodeFace(ByteBuffer faces, int x, int z, Direction direction, int flags) {
        throw new AssertionError();
    }

    @Inject(method = {
            "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"
    }, at = @At("HEAD"))
    private void ringworld$invalidateFacePolicy(CallbackInfo ci) {
        boolean curved = ClientRingState.geometry() != null;
        if (curved != ringworld$curvedFaces) markForRebuild();
        ringworld$curvedFaces = curved;
    }

    @ModifyVariable(method = {
            "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ringworld$dynamicCloudHeight(float vanillaHeight) {
        Minecraft client = Minecraft.getInstance();
        if (ClientRingState.geometry() == null || client.level == null) return vanillaHeight;
        return RingCloudBounds.baseHeight(client.level.getMinY(), ClientRingState.wallHeightBlocks());
    }

    @Inject(method = "buildExtrudedCell", at = @At("HEAD"))
    private void ringworld$keepCurvedCaps(@Coerce Object relativeCameraPos, ByteBuffer faces,
                                         int x, int z, long cell, CallbackInfo ci) {
        if (!ringworld$curvedFaces) return;
        // This private enum has identical names on all supported source ABIs.
        // A curved distant cell can be above the eye while its near deck is below.
        // Add only the cap vanilla omits; retain its other faces and inside flags.
        String side = ((Enum<?>)relativeCameraPos).name();
        if (side.equals("BELOW_CLOUDS")) encodeFace(faces, x, z, Direction.UP, 0);
        else if (side.equals("ABOVE_CLOUDS")) encodeFace(faces, x, z, Direction.DOWN, 0);
    }
}
