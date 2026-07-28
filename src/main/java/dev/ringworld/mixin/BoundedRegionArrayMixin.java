package dev.ringworld.mixin;

import dev.ringworld.world.RingRegionContext;
import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets a generation region address canonical holders through a local seam alias. */
@Mixin(StaticCache2D.class)
abstract class BoundedRegionArrayMixin {
    @Shadow @Final private int minX;
    @Shadow @Final private int sizeX;
    @Unique private int ringworld$circumferenceChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$markPeriodicRegion(CallbackInfo ci) {
        ringworld$circumferenceChunks = RingRegionContext.activeCircumferenceChunks();
    }

    @ModifyVariable(method = {"get", "contains"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld$projectPeriodicXIntoRegion(int x) {
        int circumference = ringworld$circumferenceChunks;
        if (circumference <= 0 || (x >= minX && x < minX + sizeX)) return x;
        int projected = x - Math.floorDiv(x - minX, circumference) * circumference;
        if (projected < minX) projected += circumference;
        if (projected >= minX + sizeX) projected -= circumference;
        return projected;
    }
}
