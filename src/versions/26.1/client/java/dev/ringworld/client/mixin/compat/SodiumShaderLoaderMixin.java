package dev.ringworld.client.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ringworld.client.compat.SodiumTerrainShaders;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
abstract class SodiumShaderLoaderMixin {
    @ModifyReturnValue(method = "getShaderSource", at = @At("RETURN"))
    private static String ringworld$material(String source, Identifier name) {
        return SodiumTerrainShaders.adapt(name, source);
    }
}
