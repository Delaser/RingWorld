package dev.ringworld.client.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingCloudShaderState;
import dev.ringworld.client.render.RingHandoffViewDistance;
import dev.ringworld.world.RingCloudBounds;
import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes mainline's named RingWorld shader contract through 1.21.1's
 * per-program uniforms. Later Minecraft versions provide these values through
 * the shared Globals UBO; keeping the names and value layout identical makes
 * the GLSL compatibility layer mechanical and leaves unrelated shaders alone.
 */
@Mixin(ShaderInstance.class)
abstract class GlobalSettingsMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void ringworld$publishLayout(CallbackInfo ci) {
        ShaderInstance shader = (ShaderInstance)(Object)this;
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();

        int active = geometry == null ? 0 : 1;
        int circumference = geometry == null ? 0 : geometry.circumferenceBlocks();
        int ringWidth = geometry == null ? 0 : geometry.widthBlocks();
        int wallHeight = geometry == null ? 0 : ClientRingState.wallHeightBlocks();
        set(shader, "RingWorldLayout", active, circumference, ringWidth, wallHeight);
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        set(shader, "RingWorldCamera", (float)camera.x, (float)camera.y,
                (float)camera.z, 0.0F);
        set(shader, "RingWorldCloudOffset",
                RingCloudShaderState.cellX(), RingCloudShaderState.cellY(),
                RingCloudShaderState.cellZ(), 0.0F);

        float surfaceReferenceY = geometry == null ? 0.0F : ClientRingState.surfaceReferenceY();
        int worldBottomY = active == 1 && client.level != null
                ? client.level.getMinBuildHeight()
                : RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y;
        float wallTopY = active == 1 ? worldBottomY + wallHeight : 0.0F;
        float cloudBaseY = active == 1
                ? wallTopY + RingDimensionReport.CLOUD_CLEARANCE_BLOCKS : 0.0F;
        float physicalCenterY = geometry == null ? 0.0F : (float)geometry.physicalCenterY();
        set(shader, "RingWorldVertical", surfaceReferenceY, wallTopY,
                cloudBaseY, physicalCenterY);

        // Match mainline's negotiated live-chunk radius. The configured slider
        // can exceed the server/effective distance; using it delays both fade
        // curves until after the authoritative chunks have already ended.
        int effectiveViewDistanceChunks = geometry == null ? 0
                : client.options.getEffectiveRenderDistance();
        float requestedViewDistanceBlocks = effectiveViewDistanceChunks * 16.0F;
        float handoffViewDistanceBlocks = geometry == null ? 0.0F
                : (float)RingHandoffViewDistance.blocks(
                        client, effectiveViewDistanceChunks);
        RingRenderProfile profile = geometry == null ? null
                : RingRenderProfile.create(geometry, handoffViewDistanceBlocks);
        set(shader, "RingWorldRender",
                geometry == null ? 0.0F : (float)geometry.minWidthZ(),
                geometry == null ? 0.0F : (float)geometry.maxWidthZ() + 1.0F,
                geometry == null ? 0.0F : geometry.circumferenceBlocks() * 0.5F,
                requestedViewDistanceBlocks);
        set(shader, "RingWorldHandoff",
                profile == null ? 0.0F : (float)profile.liveFadeStartBlocks(),
                profile == null ? 0.0F : (float)profile.liveFadeEndBlocks(),
                profile == null ? 0.0F : (float)profile.proxyFadeStartBlocks(),
                // The 1.21.1 core-shader path blends this sky-stage draw
                // through the legacy framebuffer. Make it opaque before the
                // first live dither can expose it; otherwise those discarded
                // pixels reveal partial Atlas plus bright sky as a dotted
                // shelf. Mainline's distances still define both spans.
                profile == null ? 0.0F : (float)profile.liveFadeStartBlocks());
        set(shader, "RingWorldDetail",
                profile == null ? 0.0F : (float)profile.detailStartBlocks(),
                profile == null ? 0.0F : (float)profile.detailEndBlocks(),
                profile == null ? 0.0F : (float)profile.revealNear(),
                profile == null ? 0.0F : (float)profile.revealFar());
        set(shader, "RingWorldAtmosphere",
                profile == null ? 0.0F : (float)profile.hazeNear(),
                profile == null ? 0.0F : (float)profile.hazeFar(),
                profile == null ? 0.0F : (float)profile.hazeExponent(),
                profile == null ? 0.0F : (float)profile.cloudFadeStartBlocks());

        RingCloudBounds cloudBounds = geometry == null ? null
                : RingCloudBounds.betweenInnerRimFaces(
                        geometry, RingGenerationBoundary.RIM_THICKNESS);
        set(shader, "RingWorldAtmosphere2",
                profile == null ? 0.0F : (float)profile.cloudFadeEndBlocks(),
                profile == null ? 0.0F : profile.visualProfileVersion(),
                cloudBounds == null ? 0.0F : (float)cloudBounds.minimumZ(),
                cloudBounds == null ? 0.0F : (float)cloudBounds.maximumZ());
    }

    private static void set(ShaderInstance shader, String name,
                            float x, float y, float z, float w) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z, w);
    }

    private static void set(ShaderInstance shader, String name,
                            int x, int y, int z, int w) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z, w);
    }
}
