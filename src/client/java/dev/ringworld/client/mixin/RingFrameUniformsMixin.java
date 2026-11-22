package dev.ringworld.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingAtlasLightTuning;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingCloudBounds;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.client.render.RingRenderUniforms;
import dev.ringworld.client.render.RingUniformCache;
import dev.ringworld.client.render.RingSkyView;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes RingWorld-owned uniforms without replacing Minecraft's Globals. */
@Mixin(GameRenderer.class)
abstract class RingFrameUniformsMixin implements RingRenderUniforms.SkyViewSource {
    @Unique private static final int RINGWORLD_GLOBALS_SIZE = dev.ringworld.client.render.RingShaderContract.UNIFORM_BYTES;

    @Unique private static final Identifier RINGWORLD_BLOCK_ATLAS = net.minecraft.data.AtlasIds.BLOCKS;
    @Unique private static final Identifier RINGWORLD_WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    @Unique private static final Identifier RINGWORLD_WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");

    @Unique
    private static void ringworld$waterBounds(Std140Builder builder, Minecraft client, Identifier texture, boolean active) {
        var sprite = active ? client.getAtlasManager().getAtlasOrThrow(RINGWORLD_BLOCK_ATLAS).getSprite(texture) : null;
        if (sprite == null || !sprite.contents().name().equals(texture)) {
            builder.putVec4(-1.0F, -1.0F, -1.0F, -1.0F);
        } else {
            builder.putVec4(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        }
    }

    @Unique private GpuBuffer ringworld$buffer;
    @Unique private GpuBuffer ringworld$skyBuffer;
    @Unique private final RingUniformCache ringworld$mainContents = new RingUniformCache(RINGWORLD_GLOBALS_SIZE);
    @Unique private RingUniformCache ringworld$skyContents;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ringworld$allocateOwnUniforms(CallbackInfo ci) {
        ringworld$buffer = RenderSystem.getDevice().createBuffer(
                () -> "RingWorld render uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                RINGWORLD_GLOBALS_SIZE);
        dev.ringworld.client.render.RingRenderUniforms.publish(ringworld$buffer);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void ringworld$closeOwnUniforms(CallbackInfo ci) {
        dev.ringworld.client.render.RingRenderUniforms.clear(ringworld$buffer);
        if (ringworld$buffer != null) ringworld$buffer.close();
        if (ringworld$skyBuffer != null) ringworld$skyBuffer.close();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ringworld$publishLayout(CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        var camera = RingMinecraftClientAccess.camera(client);
        float teleportCover = geometry == null || client.getCameraEntity() == null ? 0.0F
                : RingSurfaceTextureRenderer.teleportCoverage(client, geometry, camera);
        ringworld$writeView(ringworld$buffer, ringworld$mainContents, client, client.level, geometry, camera.position(), teleportCover);
    }

    @Override
    public GpuBuffer ringworld$skyViewBuffer(ClientLevel world, RingSkyView view) {
        if (ringworld$skyBuffer == null) {
            ringworld$skyContents = new RingUniformCache(RINGWORLD_GLOBALS_SIZE);
            ringworld$skyBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "RingWorld alternate sky view", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    RINGWORLD_GLOBALS_SIZE);
        }
        // The main camera owns the cold-teleport tracker. Other views use the normal handoff.
        ringworld$writeView(ringworld$skyBuffer, ringworld$skyContents, Minecraft.getInstance(), world, view.geometry(), view.eye(), 0);
        return ringworld$skyBuffer;
    }

    @Unique
    private static void ringworld$writeView(GpuBuffer buffer, RingUniformCache contents,
                                            Minecraft client, ClientLevel world,
                                            RingGeometry geometry, Vec3 eye, float teleportCover) {
        int active = geometry == null ? 0 : 1;
        int circumference = geometry == null ? 0 : geometry.circumferenceBlocks();
        int ringWidth = geometry == null ? 0 : geometry.widthBlocks();
        int wallHeight = geometry == null ? 0 : ClientRingState.wallHeightBlocks();

        float surfaceReferenceY = geometry == null ? 0.0F : ClientRingState.surfaceReferenceY();
        int worldBottomY = active == 1 && world != null
                ? world.getMinY()
                : RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y;
        float wallTopY = active == 1 ? worldBottomY + wallHeight : 0.0F;
        float cloudBaseY = active == 1
                ? RingCloudBounds.baseHeight(worldBottomY, wallHeight)
                : 0.0F;
        float physicalCenterY = geometry == null ? 0.0F : (float)geometry.physicalCenterY();

        float minWidthZ = geometry == null ? 0.0F : geometry.minWidthZ();
        float maxWidthZExclusive = geometry == null ? 0.0F : geometry.maxWidthZ() + 1.0F;
        float viewDistanceBlocks = active == 1
                ? client.options.getEffectiveRenderDistance() * 16.0F
                : 0.0F;
        RingRenderProfile profile = geometry == null
                ? null
                : dev.ringworld.client.RingClientLodTuning.profile(geometry, viewDistanceBlocks);
        RingCloudBounds cloudBounds = geometry == null ? null
                : RingCloudBounds.betweenInnerRimFaces(
                        geometry, ClientRingState.wallStyle().thicknessBlocks());
        var atlasLight = RingAtlasLightTuning.profile();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, RINGWORLD_GLOBALS_SIZE)
                    // active, circumference, width, saved wall height
                    .putIVec4(active, circumference, ringWidth, wallHeight)
                    // surface reference, wall top, cloud base, physical centre
                    .putVec4(surfaceReferenceY, wallTopY, cloudBaseY, physicalCenterY)
                    // min Z, max Z exclusive, temporary Atlas cover, view distance
                    .putVec4(minWidthZ, maxWidthZExclusive,
                            teleportCover, viewDistanceBlocks)
                    // live terrain dither, then visual proxy alpha
                    .putVec4(profile == null ? 0.0F : (float)profile.liveFadeStartBlocks(),
                            profile == null ? 0.0F : (float)profile.liveFadeEndBlocks(),
                            profile == null ? 0.0F : (float)profile.proxyFadeStartBlocks(),
                            profile == null ? 0.0F : (float)profile.proxyFadeEndBlocks())
                    // proxy detail transition and terrain reveal strength
                    .putVec4(profile == null ? 0.0F : (float)profile.detailStartBlocks(),
                            profile == null ? 0.0F : (float)profile.detailEndBlocks(),
                            profile == null ? 0.0F : 1.0F,
                            profile == null ? 0.0F : (float)profile.revealFar())
                    // proxy haze policy and local curved-cloud fade start
                    .putVec4(profile == null ? 0.0F : (float)profile.hazeNear(),
                            profile == null ? 0.0F : (float)profile.hazeFar(),
                            profile == null ? 0.0F : (float)profile.hazeExponent(),
                            profile == null ? 0.0F : (float)profile.cloudFadeStartBlocks())
                    // cloud fade end, current rain strength, inner Z face planes
                    .putVec4(profile == null ? 0.0F : (float)profile.cloudFadeEndBlocks(),
                            profile == null || world == null ? 0.0F : world.getRainLevel(
                                    client.getDeltaTracker().getGameTimeDeltaPartialTick(false)),
                            cloudBounds == null ? 0.0F : (float)cloudBounds.minimumZ(),
                            cloudBounds == null ? 0.0F : (float)cloudBounds.maximumZ())
                    // process-local Atlas-light debug profile; never persisted
                    .putVec4(atlasLight.shaderMode(), atlasLight.falloffExponent(),
                            atlasLight.peakStrength(), ClientRingState.skyProfile().backdrop().id());
            ringworld$waterBounds(builder, client, RINGWORLD_WATER_STILL, active == 1 && world != null);
            ringworld$waterBounds(builder, client, RINGWORLD_WATER_FLOW, active == 1 && world != null);
            // Uploaded correction parameters are independent of camera Y.
            // The shared shader reads the actual camera from vanilla Globals.
            var distortion = geometry == null ? null
                    : dev.ringworld.client.RingDistortionTuning.projection(geometry, Vec3.ZERO);
            builder.putVec4(geometry != null && dev.ringworld.client.RingDistortionTuning.enabled() ? 1.0F : 0.0F,
                    distortion == null ? 0.0F : (float)distortion.coreBlocks(),
                    distortion == null ? 0.0F : (float)distortion.blendEnd(),
                    distortion == null ? 0.0F : (float)distortion.referenceRadius());
            builder.putVec4(geometry == null ? 0F : (float)geometry.wrapX(eye.x), (float)eye.z,
                    dev.ringworld.client.render.RingSurfaceTextureRenderer.waterSurface() == null ? 0F : 1F, 0F);
            var data = builder.get();
            if (!contents.matches(data)) {
                dev.ringworld.client.render.RingSurfaceGpu.writeBuffer(buffer.slice(), data.duplicate());
                contents.record(data);
            }
        }
    }
}
