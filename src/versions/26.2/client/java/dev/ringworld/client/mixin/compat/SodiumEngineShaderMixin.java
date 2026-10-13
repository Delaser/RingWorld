package dev.ringworld.client.mixin.compat;

import com.mojang.blaze3d.shaders.ShaderType;
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
    @Redirect(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;openAsReader()Ljava/io/BufferedReader;"))
    private static java.io.BufferedReader ringworld$material(Resource resource, Identifier name,
            Resource original, ShaderType type, java.util.Map<Identifier, Resource> resources, ImmutableMap.Builder<?, ?> builder) throws java.io.IOException {
        try (var reader = resource.openAsReader()) {
            return new java.io.BufferedReader(new java.io.StringReader(
                    SodiumTerrainShaders.adapt(name, org.apache.commons.io.IOUtils.toString(reader))));
        }
    }
}
