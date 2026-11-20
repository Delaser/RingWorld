package local.rwprobe.mixin;
import dev.ringworld.world.RingLayerTrial;
import dev.ringworld.world.RingTerrainAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=RingLayerTrial.class, remap=false)
abstract class LayerChoice {
 @Inject(method="matching",at=@At("HEAD"),cancellable=true)
 private static void choose(RingTerrainAtlas atlas,int segments,int bands,double z0,double z1,CallbackInfoReturnable<Object> ci) {
  if(!local.rwprobe.Study.layers) ci.setReturnValue(null);
 }
}
