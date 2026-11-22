package dev.ringworld.client.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ringworld.client.compat.SodiumTerrainShaders;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
abstract class SodiumEngineShaderMixin {
    @ModifyExpressionValue(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;readAllAsString()Ljava/lang/String;"))
    private static String ringworld$material(String source, Identifier name) {
        return SodiumTerrainShaders.adapt(name, source);
    }
}
