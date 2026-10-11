package dev.ringworld.mixin;

import dev.ringworld.server.RingLightSectionStorage;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap",
        "net.minecraft.world.level.lighting.BlockLightSectionStorage$BlockDataLayerStorageMap"})
abstract class LightSectionSnapshotMixin {
    @Inject(method = "copy", at = @At("RETURN"))
    private void ringworld$copyTable(CallbackInfoReturnable<DataLayerStorageMap<?>> cir) {
        var source = (RingLightSectionStorage) this;
        var target = (RingLightSectionStorage) cir.getReturnValue();
        // The covariant bridge also calls copy; do not copy its already initialized result twice.
        if (source.ringworld$table() != null && target.ringworld$table() == null) {
            target.ringworld$table(source.ringworld$table().copy());
        }
    }
}
