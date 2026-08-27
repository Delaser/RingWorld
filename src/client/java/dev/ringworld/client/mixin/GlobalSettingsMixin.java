package dev.ringworld.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingCloudBounds;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends Minecraft's shared Globals UBO with a named, synchronized RingWorld
 * layout. All overridden terrain/cloud shaders consume the same values, while
 * non-RingWorld shaders simply ignore the trailing fields.
 */
@Mixin(GlobalSettingsUniform.class)
abstract class GlobalSettingsMixin {
    private static final int RINGWORLD_GLOBALS_SIZE = new Std140SizeCalculator()
            .putIVec3().putVec3().putVec2().putFloat().putFloat().putInt().putInt()
            .putIVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();

    @Shadow @Final private GpuBuffer buffer;

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
            index = 2)
    private long ringworld$extendGlobalsBuffer(long vanillaSize) {
        return RINGWORLD_GLOBALS_SIZE;
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void ringworld$publishLayout(int width, int height, double glintStrength,
                                         long time, DeltaTracker tickCounter,
                                         int menuBackgroundBlurriness, Vec3 cameraPosition,
                                         boolean useRgss, CallbackInfo ci) {
        int cameraX = Mth.floor(cameraPosition.x);
        int cameraY = Mth.floor(cameraPosition.y);
        int cameraZ = Mth.floor(cameraPosition.z);

        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        int active = geometry == null ? 0 : 1;
        int circumference = geometry == null ? 0 : geometry.circumferenceBlocks();
        int ringWidth = geometry == null ? 0 : geometry.widthBlocks();
        int wallHeight = geometry == null ? 0 : ClientRingState.wallHeightBlocks();

        float surfaceReferenceY = geometry == null ? 0.0F : ClientRingState.surfaceReferenceY();
        int worldBottomY = active == 1 && client.level != null
                ? client.level.getMinY()
                : RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y;
        float wallTopY = active == 1 ? worldBottomY + wallHeight : 0.0F;
        float cloudBaseY = active == 1
                ? wallTopY + RingDimensionReport.CLOUD_CLEARANCE_BLOCKS
                : 0.0F;
        float physicalCenterY = geometry == null ? 0.0F : (float)geometry.physicalCenterY();

        float minWidthZ = geometry == null ? 0.0F : geometry.minWidthZ();
        float maxWidthZExclusive = geometry == null ? 0.0F : geometry.maxWidthZ() + 1.0F;
        float halfCircumference = geometry == null ? 0.0F
                : geometry.circumferenceBlocks() * 0.5F;
        float viewDistanceBlocks = active == 1
                ? client.options.getEffectiveRenderDistance() * 16.0F
                : 0.0F;
        RingRenderProfile profile = geometry == null
                ? null
                : RingRenderProfile.create(geometry, viewDistanceBlocks);
        RingCloudBounds cloudBounds = geometry == null ? null
                : RingCloudBounds.betweenInnerRimFaces(
                        geometry, RingGenerationBoundary.RIM_THICKNESS);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, RINGWORLD_GLOBALS_SIZE)
                    .putIVec3(cameraX, cameraY, cameraZ)
                    .putVec3((float)(cameraX - cameraPosition.x),
                            (float)(cameraY - cameraPosition.y),
                            (float)(cameraZ - cameraPosition.z))
                    .putVec2(width, height)
                    .putFloat((float)glintStrength)
                    .putFloat(((float)(time % 24_000L) + tickCounter.getGameTimeDeltaPartialTick(false))
                            / 24_000.0F)
                    .putInt(menuBackgroundBlurriness)
                    .putInt(useRgss ? 1 : 0)
                    // active, circumference, width, saved wall height
                    .putIVec4(active, circumference, ringWidth, wallHeight)
                    // surface reference, wall top, cloud base, physical centre
                    .putVec4(surfaceReferenceY, wallTopY, cloudBaseY, physicalCenterY)
                    // min Z, max Z exclusive, half circumference, view distance
                    .putVec4(minWidthZ, maxWidthZExclusive,
                            halfCircumference, viewDistanceBlocks)
                    // live terrain dither, then visual proxy alpha
                    .putVec4(profile == null ? 0.0F : (float)profile.liveFadeStartBlocks(),
                            profile == null ? 0.0F : (float)profile.liveFadeEndBlocks(),
                            profile == null ? 0.0F : (float)profile.proxyFadeStartBlocks(),
                            profile == null ? 0.0F : (float)profile.proxyFadeEndBlocks())
                    // proxy detail transition and terrain reveal strength
                    .putVec4(profile == null ? 0.0F : (float)profile.detailStartBlocks(),
                            profile == null ? 0.0F : (float)profile.detailEndBlocks(),
                            profile == null ? 0.0F : (float)profile.revealNear(),
                            profile == null ? 0.0F : (float)profile.revealFar())
                    // proxy haze policy and local curved-cloud fade start
                    .putVec4(profile == null ? 0.0F : (float)profile.hazeNear(),
                            profile == null ? 0.0F : (float)profile.hazeFar(),
                            profile == null ? 0.0F : (float)profile.hazeExponent(),
                            profile == null ? 0.0F : (float)profile.cloudFadeStartBlocks())
                    // cloud fade end, visual policy version, inner Z face planes
                    .putVec4(profile == null ? 0.0F : (float)profile.cloudFadeEndBlocks(),
                            profile == null ? 0.0F : profile.visualProfileVersion(),
                            cloudBounds == null ? 0.0F : (float)cloudBounds.minimumZ(),
                            cloudBounds == null ? 0.0F : (float)cloudBounds.maximumZ())
                    .get();
            dev.ringworld.client.render.RingSurfaceGpu.writeBuffer(buffer.slice(), data);
        }
        RenderSystem.setGlobalSettingsUniform(buffer);
        ci.cancel();
    }
}
