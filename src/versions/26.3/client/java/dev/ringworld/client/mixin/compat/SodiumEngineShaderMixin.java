package dev.ringworld.client.mixin.compat;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.google.common.collect.ImmutableMap;
import dev.ringworld.client.compat.SodiumTerrainShaders;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShaderManager.class)
abstract class SodiumEngineShaderMixin {
    @Redirect(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;readAllAsString()Ljava/lang/String;"))
    private static String ringworld$material(Resource resource, Identifier name,
            Resource original, ShaderType type, ImmutableMap.Builder<?, ?> builder) throws java.io.IOException {
        return SodiumTerrainShaders.adapt(name, resource.readAllAsString());
    }
}
