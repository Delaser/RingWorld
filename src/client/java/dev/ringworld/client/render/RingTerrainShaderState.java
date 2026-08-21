package dev.ringworld.client.render;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

public final class RingTerrainShaderState {
    private RingTerrainShaderState() {}

    public static void update(Camera camera) {
        ShaderInstance shader = GameRenderer.getRendertypeSolidShader();
        if (shader == null) return;

        RingGeometry geometry = ClientRingState.geometry();

        if (geometry == null) {
            shader.safeGetUniform("RingWorldActive").set(0);
            return;
        }


        updateShader(GameRenderer.getRendertypeSolidShader(), camera, geometry);
        updateShader(GameRenderer.getRendertypeCutoutShader(), camera, geometry);
        updateShader(GameRenderer.getRendertypeCutoutMippedShader(), camera, geometry);
        updateShader(GameRenderer.getRendertypeTranslucentShader(), camera, geometry);

    }

    private static void updateShader(ShaderInstance shader, Camera camera, RingGeometry geometry) {
        if (shader == null) return;

        if (geometry == null) {
            shader.safeGetUniform("RingWorldActive").set(0);
            return;
        }

        Vec3 pos = camera.getPosition();

        shader.safeGetUniform("RingWorldActive").set(1);
        shader.safeGetUniform("RingWorldCircumference").set((float)geometry.circumferenceBlocks());
        shader.safeGetUniform("RingWorldSurfaceY").set((float)ClientRingState.surfaceReferenceY());
        shader.safeGetUniform("RingWorldCameraPos").set((float)pos.x, (float)pos.y, (float)pos.z);
    }

}