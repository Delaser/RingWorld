package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.compat.SodiumTerrainShaders;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
abstract class SodiumShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void ringworld$material(Identifier name, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SodiumTerrainShaders.adapt(name, cir.getReturnValue()));
    }
}
