package dev.ringworld.client.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ringworld.client.compat.SodiumTerrainShaders;
import net.minecraft.client.renderer.ShaderManager;
import java.io.BufferedReader;
import java.io.IOException;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
abstract class SodiumEngineShaderMixin {
    @ModifyExpressionValue(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;openAsReader()Ljava/io/BufferedReader;"))
    private static BufferedReader ringworld$material(BufferedReader reader, Identifier name) throws IOException {
        return SodiumTerrainShaders.adaptReader(name, reader);
    }
}
