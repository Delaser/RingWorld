package dev.ringworld.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingHandoffFogRenderer;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingVisibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.MoonPhase;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Fixed sun, shadow-panel array, and the atmospheric continuation of the ring. */
@Mixin(SkyRendering.class)
abstract class SkyRenderingMixin {
    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(float alpha, MatrixStack matrices);

    @Unique private static final int RINGWORLD_SEGMENTS_PER_PANEL = 8;
    @Unique private static final int RINGWORLD_SHADOW_VERTICES =
            RingSkyCycle.SHADOW_PANEL_COUNT * RINGWORLD_SEGMENTS_PER_PANEL * 6;
    @Unique private static final RenderPipeline RINGWORLD_SHADOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("ringworld", "pipeline/shadow_panel"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .build());
    @Unique private static final int RINGWORLD_ARCH_SEGMENTS = 384;
    @Unique private static final int RINGWORLD_ARCH_WIDTH_BANDS = 48;
    @Unique private static final int RINGWORLD_ARCH_MAX_VERTICES =
            RINGWORLD_ARCH_SEGMENTS * RINGWORLD_ARCH_WIDTH_BANDS * 6;
    @Unique private static final RenderPipeline RINGWORLD_ARCH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("ringworld", "pipeline/distant_arch"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .build());
    @Unique private GpuBuffer ringworld$shadowPanelVertexBuffer;
    @Unique private GpuBuffer ringworld$archVertexBuffer;
    @Unique private RingGeometry ringworld$archGeometry;
    @Unique private int ringworld$archViewDistanceChunks = -1;
    @Unique private int ringworld$archSkyColorKey = -1;
    @Unique private int ringworld$archEdgeProfileSignature;
    @Unique private int ringworld$archAtlasRevision = -1;
    @Unique private int ringworld$archVertexCount;
    @Unique private long ringworld$edgeProfileEpoch = Long.MIN_VALUE;
    @Unique private int ringworld$edgeProfileCameraChunkX = Integer.MIN_VALUE;
    @Unique private int ringworld$edgeProfileCameraChunkZ = Integer.MIN_VALUE;
    @Unique private int ringworld$edgeProfileViewDistance = -1;
    @Unique private RingworldArchProfile ringworld$edgeProfile;
    @Unique private float ringworld$archCameraY;
    @Unique private float ringworld$archCameraZ;
    @Unique private double ringworld$archCameraX;
    @Unique private float ringworld$starTiltRadians;
    @Unique private boolean ringworld$renderingCenteredSun;
    @Unique private float ringworld$archBrightness = 1.0F;
    @Unique private RingSkyCycle.ShadowPanel ringworld$shadowPanel = RingSkyCycle.shadowPanel(6_000.0);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$createShadowPanel(TextureManager textureManager, AtlasManager atlasManager,
                                             CallbackInfo ci) {
        VertexFormat format = VertexFormats.POSITION_COLOR;
        try (BufferAllocator allocator = BufferAllocator.fixedSized(
                RINGWORLD_SHADOW_VERTICES * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, format);
            int color = 0xFF05070A;
            float halfArc = (float)Math.toRadians(RingSkyCycle.PANEL_ANGULAR_HALF_LENGTH_DEGREES);
            float spacing = (float)Math.toRadians(RingSkyCycle.PANEL_SPACING_DEGREES);
            for (int panel = 0; panel < RingSkyCycle.SHADOW_PANEL_COUNT; panel++) {
                float center = panel * spacing;
                for (int segment = 0; segment < RINGWORLD_SEGMENTS_PER_PANEL; segment++) {
                    float start = center - halfArc
                            + 2.0F * halfArc * segment / RINGWORLD_SEGMENTS_PER_PANEL;
                    float end = center - halfArc
                            + 2.0F * halfArc * (segment + 1) / RINGWORLD_SEGMENTS_PER_PANEL;
                    ringworld$shadowVertex(builder, start, RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH, color);
                    ringworld$shadowVertex(builder, end, RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH, color);
                    ringworld$shadowVertex(builder, end, -RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH, color);
                    ringworld$shadowVertex(builder, start, RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH, color);
                    ringworld$shadowVertex(builder, end, -RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH, color);
                    ringworld$shadowVertex(builder, start, -RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH, color);
                }
            }
            try (BuiltBuffer built = builder.end()) {
                ringworld$shadowPanelVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "RingWorld shadow panel", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
    }

    @Unique
    private static void ringworld$shadowVertex(BufferBuilder builder, float alongAngle,
                                                float crossOffset, int color) {
        float radius = RingSkyCycle.PANEL_ORBIT_RADIUS;
        float x = crossOffset;
        float y = -radius * (float)Math.cos(alongAngle);
        float z = radius * (float)Math.sin(alongAngle);
        builder.vertex(x, y, z).color(color);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void ringworld$closeSkyGeometry(CallbackInfo ci) {
        if (ringworld$shadowPanelVertexBuffer != null) ringworld$shadowPanelVertexBuffer.close();
        if (ringworld$archVertexBuffer != null) ringworld$archVertexBuffer.close();
        RingSurfaceTextureRenderer.clear();
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void ringworld$updateFixedSky(ClientWorld world, float tickProgress, Camera camera,
                                          SkyRenderState state, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        state.sunAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.moonAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.starAngle = 0.0F;
        state.sunriseAndSunsetColor = 0;
        ringworld$shadowPanel = RingSkyCycle.shadowPanel(world.getTimeOfDay() + tickProgress);
        ringworld$archCameraY = (float)camera.getCameraPos().y;
        ringworld$archCameraZ = (float)camera.getCameraPos().z;
        ringworld$archCameraX = camera.getCameraPos().x;
        // Project the one physical star at the origin into this camera's
        // tangent frame. On the band midline it is at local zenith; moving
        // across the finite width shifts it toward the true ring centre.
        Vec3d starDirection = geometry.directionToRingCenter(camera.getCameraPos());
        ringworld$starTiltRadians = (float)Math.atan2(starDirection.z, starDirection.y);
        // The far surface remains readable at night, as the classic Arch is
        // illuminated by enormous distant regions rather than behaving like
        // a nearby unlit block face. Rain still attenuates it in the render call.
        ringworld$archBrightness = 1.0F - 0.35F * state.starBrightness;
        RingHandoffFogRenderer.updateSkyColor(state.skyColor);
    }

    @Unique
    private void ringworld$ensureArchGeometry(RingGeometry geometry, int viewDistanceChunks,
                                              int skyColor, RingworldArchProfile edgeProfile) {
        // Eight levels per channel keep the edge matched through weather and
        // twilight without rebuilding a 55k-vertex buffer every frame.
        int skyColorKey = ((skyColor >> 16 & 0xFF) >> 5) << 6
                | ((skyColor >> 8 & 0xFF) >> 5) << 3
                | (skyColor & 0xFF) >> 5;
        if (geometry.equals(ringworld$archGeometry)
                && viewDistanceChunks == ringworld$archViewDistanceChunks
                && skyColorKey == ringworld$archSkyColorKey
                && edgeProfile.signature == ringworld$archEdgeProfileSignature
                && ClientRingState.terrainAtlasRevision() == ringworld$archAtlasRevision
                && ringworld$archVertexBuffer != null) return;

        double renderDistanceBlocks = viewDistanceChunks * 16.0;
        double skyScale = RingVisibility.skyScale(geometry);
        double radius = geometry.radius();
        double minZ = geometry.minWidthZ();
        double maxZ = geometry.maxWidthZ() + 1.0;
        double cameraZ = ringworld$archCameraZ;
        double cameraX = ringworld$archCameraX;
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        VertexFormat format = VertexFormats.POSITION_COLOR;

        try (BufferAllocator allocator = BufferAllocator.fixedSized(
                RINGWORLD_ARCH_MAX_VERTICES * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, format);
            for (int segment = 0; segment < RINGWORLD_ARCH_SEGMENTS; segment++) {
                double along0 = (double)segment / RINGWORLD_ARCH_SEGMENTS;
                double along1 = (double)(segment + 1) / RINGWORLD_ARCH_SEGMENTS;
                double angle0 = Math.PI * 2.0 * along0;
                double angle1 = Math.PI * 2.0 * along1;

                for (int band = 0; band < RINGWORLD_ARCH_WIDTH_BANDS; band++) {
                    double across0 = (double)band / RINGWORLD_ARCH_WIDTH_BANDS;
                    double across1 = (double)(band + 1) / RINGWORLD_ARCH_WIDTH_BANDS;
                    double z0 = minZ + (maxZ - minZ) * across0;
                    double z1 = minZ + (maxZ - minZ) * across1;
                    double canonicalX0 = cameraX + along0 * geometry.circumferenceBlocks();
                    double canonicalX1 = cameraX + along1 * geometry.circumferenceBlocks();
                    RingTerrainAtlas.SurfaceSample atlas00 = atlas == null
                            ? RingTerrainAtlas.SurfaceSample.MISSING : atlas.sample(canonicalX0, z0);
                    RingTerrainAtlas.SurfaceSample atlas01 = atlas == null
                            ? RingTerrainAtlas.SurfaceSample.MISSING : atlas.sample(canonicalX0, z1);
                    RingTerrainAtlas.SurfaceSample atlas10 = atlas == null
                            ? RingTerrainAtlas.SurfaceSample.MISSING : atlas.sample(canonicalX1, z0);
                    RingTerrainAtlas.SurfaceSample atlas11 = atlas == null
                            ? RingTerrainAtlas.SurfaceSample.MISSING : atlas.sample(canonicalX1, z1);
                    double dz0 = z0 - cameraZ;
                    double dz1 = z1 - cameraZ;
                    double alpha00 = RingVisibility.proxyAlpha(
                            geometry, angle0, dz0, renderDistanceBlocks);
                    double alpha01 = RingVisibility.proxyAlpha(
                            geometry, angle0, dz1, renderDistanceBlocks);
                    double alpha10 = RingVisibility.proxyAlpha(
                            geometry, angle1, dz0, renderDistanceBlocks);
                    double alpha11 = RingVisibility.proxyAlpha(
                            geometry, angle1, dz1, renderDistanceBlocks);
                    double detail00 = RingVisibility.proxyTerrainDetail(
                            geometry, angle0, dz0, renderDistanceBlocks);
                    double detail01 = RingVisibility.proxyTerrainDetail(
                            geometry, angle0, dz1, renderDistanceBlocks);
                    double detail10 = RingVisibility.proxyTerrainDetail(
                            geometry, angle1, dz0, renderDistanceBlocks);
                    double detail11 = RingVisibility.proxyTerrainDetail(
                            geometry, angle1, dz1, renderDistanceBlocks);
                    double fog00 = RingVisibility.handoffFog(
                            geometry, angle0, dz0, renderDistanceBlocks);
                    double fog01 = RingVisibility.handoffFog(
                            geometry, angle0, dz1, renderDistanceBlocks);
                    double fog10 = RingVisibility.handoffFog(
                            geometry, angle1, dz0, renderDistanceBlocks);
                    double fog11 = RingVisibility.handoffFog(
                            geometry, angle1, dz1, renderDistanceBlocks);
                    double height00 = ringworld$archHeight(edgeProfile, angle0, band, detail00, atlas00);
                    double height01 = ringworld$archHeight(edgeProfile, angle0, band + 1, detail01, atlas01);
                    double height10 = ringworld$archHeight(edgeProfile, angle1, band, detail10, atlas10);
                    double height11 = ringworld$archHeight(edgeProfile, angle1, band + 1, detail11, atlas11);
                    int color00 = ringworld$archColor(angle0, across0, alpha00, detail00,
                            fog00, skyColor,
                            ringworld$edgeColor(edgeProfile, angle0, band), atlas00);
                    int color01 = ringworld$archColor(angle0, across1, alpha01, detail01,
                            fog01, skyColor,
                            ringworld$edgeColor(edgeProfile, angle0, band + 1), atlas01);
                    int color10 = ringworld$archColor(angle1, across0, alpha10, detail10,
                            fog10, skyColor,
                            ringworld$edgeColor(edgeProfile, angle1, band), atlas10);
                    int color11 = ringworld$archColor(angle1, across1, alpha11, detail11,
                            fog11, skyColor,
                            ringworld$edgeColor(edgeProfile, angle1, band + 1), atlas11);

                    ringworld$archVertex(builder, radius, skyScale, angle0, z0, height00, color00);
                    ringworld$archVertex(builder, radius, skyScale, angle1, z0, height10, color10);
                    ringworld$archVertex(builder, radius, skyScale, angle1, z1, height11, color11);
                    ringworld$archVertex(builder, radius, skyScale, angle0, z0, height00, color00);
                    ringworld$archVertex(builder, radius, skyScale, angle1, z1, height11, color11);
                    ringworld$archVertex(builder, radius, skyScale, angle0, z1, height01, color01);
                }
            }
            try (BuiltBuffer built = builder.end()) {
                GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                        () -> "RingWorld distant Arch", GpuBuffer.USAGE_VERTEX, built.getBuffer());
                if (ringworld$archVertexBuffer != null) ringworld$archVertexBuffer.close();
                ringworld$archVertexBuffer = replacement;
                ringworld$archVertexCount = RINGWORLD_ARCH_MAX_VERTICES;
                ringworld$archGeometry = geometry;
                ringworld$archViewDistanceChunks = viewDistanceChunks;
                ringworld$archSkyColorKey = skyColorKey;
                ringworld$archEdgeProfileSignature = edgeProfile.signature;
                ringworld$archAtlasRevision = ClientRingState.terrainAtlasRevision();
            }
        }
    }

    @Unique
    private static void ringworld$archVertex(BufferBuilder builder, double radius, double skyScale,
                                             double angle, double z, double surfaceHeight, int color) {
        double surfaceRadius = radius + RingGeometry.SURFACE_Y - surfaceHeight;
        float x = (float)(surfaceRadius * Math.sin(angle) * skyScale);
        float y = (float)((radius - surfaceRadius * Math.cos(angle)) * skyScale);
        double apparentZ = z * RingVisibility.distantWidthScale(angle);
        builder.vertex(x, y, (float)(apparentZ * skyScale)).color(color);
    }

    /** Real atlas colour with the old procedural surface retained only as a missing-data fallback. */
    @Unique
    private static int ringworld$archColor(double angle, double across, double alpha,
                                           double terrainDetail, double handoffFog,
                                           int skyColor, int edgeColor,
                                           RingTerrainAtlas.SurfaceSample atlasSample) {
        double continental = Math.sin(angle * 3.0 + across * 4.2)
                + 0.55 * Math.sin(angle * 7.0 - across * 8.0);
        double cloud = Math.sin(angle * 17.0 + across * 13.0)
                + 0.45 * Math.sin(angle * 29.0 - across * 9.0);
        int fallbackRed;
        int fallbackGreen;
        int fallbackBlue;
        if (continental < -0.35) {
            fallbackRed = 66;
            fallbackGreen = 116;
            fallbackBlue = 143;
        } else if (continental > 0.95) {
            fallbackRed = 137;
            fallbackGreen = 126;
            fallbackBlue = 82;
        } else {
            fallbackRed = 91;
            fallbackGreen = 132;
            fallbackBlue = 82;
        }
        // Atmospheric distance desaturates terrain; broad pale patches imply
        // cloud systems without pretending to reproduce unloaded blocks.
        double distantHaze = 0.28 + Math.max(0.0, cloud - 0.75) * 0.24;
        distantHaze = Math.min(0.68, distantHaze);
        boolean sampledEdge = edgeColor >= 0;
        int distantColor = atlasSample.present() ? atlasSample.color()
                : (fallbackRed << 16) | (fallbackGreen << 8) | fallbackBlue;
        int distantRed = distantColor >> 16 & 0xFF;
        int distantGreen = distantColor >> 8 & 0xFF;
        int distantBlue = distantColor & 0xFF;
        int edgeRed = sampledEdge ? edgeColor >> 16 & 0xFF : distantRed;
        int edgeGreen = sampledEdge ? edgeColor >> 8 & 0xFF : distantGreen;
        int edgeBlue = sampledEdge ? edgeColor & 0xFF : distantBlue;
        int red = (int)Math.round(edgeRed + (distantRed - edgeRed) * terrainDetail);
        int green = (int)Math.round(edgeGreen + (distantGreen - edgeGreen) * terrainDetail);
        int blue = (int)Math.round(edgeBlue + (distantBlue - edgeBlue) * terrainDetail);
        double edgeHaze = sampledEdge ? 0.35 : 0.72;
        // Atlas data represents actual land and water. It receives ordinary
        // atmospheric desaturation, while procedural fallback stays hazier so
        // incomplete pregeneration is not mistaken for authoritative detail.
        double atlasDistanceHaze = 0.32 + 0.12 * Math.pow(Math.sin(angle * 0.5), 2.0);
        double atlasHaze = atlasSample.present() ? atlasDistanceHaze : distantHaze;
        double haze = edgeHaze + (atlasHaze - edgeHaze) * terrainDetail;
        // At the actual chunk radius, become the same atmospheric colour as
        // the fogged final meshes. Proxy land then reappears gradually beyond
        // the join rather than meeting live blocks along a visible cut line.
        haze = Math.max(haze, handoffFog);
        int skyRed = skyColor >> 16 & 0xFF;
        int skyGreen = skyColor >> 8 & 0xFF;
        int skyBlue = skyColor & 0xFF;
        red = (int)Math.round(red + (skyRed - red) * haze);
        green = (int)Math.round(green + (skyGreen - green) * haze);
        blue = (int)Math.round(blue + (skyBlue - blue) * haze);
        int a = (int)Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
        return (a << 24) | (red << 16) | (green << 8) | blue;
    }

    @Unique
    private RingworldArchProfile ringworld$sampleEdgeProfile(ClientWorld world, RingGeometry geometry,
                                                             double cameraX, double cameraZ,
                                                             int viewDistanceChunks) {
        long epoch = world.getTime() / 20L;
        // A four-block cell keeps the sampled continuation visually anchored
        // while limiting the expensive mesh/profile refresh to about once a
        // second at ordinary walking speed.
        int cameraChunkX = (int)Math.floor(cameraX / 4.0);
        int cameraChunkZ = (int)Math.floor(cameraZ / 4.0);
        if (ringworld$edgeProfile != null && epoch == ringworld$edgeProfileEpoch
                && cameraChunkX == ringworld$edgeProfileCameraChunkX
                && cameraChunkZ == ringworld$edgeProfileCameraChunkZ
                && viewDistanceChunks == ringworld$edgeProfileViewDistance) {
            return ringworld$edgeProfile;
        }

        int points = RINGWORLD_ARCH_WIDTH_BANDS + 1;
        double[] positiveHeights = new double[points];
        double[] negativeHeights = new double[points];
        int[] positiveColors = new int[points];
        int[] negativeColors = new int[points];
        double anchorX = cameraChunkX * 4.0 + 2.0;
        double renderDistance = viewDistanceChunks * 16.0;
        int signature = 1;
        for (int point = 0; point < points; point++) {
            double across = (double)point / RINGWORLD_ARCH_WIDTH_BANDS;
            double z = geometry.minWidthZ() + geometry.widthBlocks() * across;
            double dz = z - cameraZ;
            double availableAlong = Math.sqrt(Math.max(0.0,
                    renderDistance * renderDistance - dz * dz));
            // Sample close to the loaded edge so the proxy inherits the last
            // real terrain silhouette instead of an unrelated inner contour.
            double sampleDistance = availableAlong * 0.90;
            RingworldSurfaceSample positive = sampleDistance >= 32.0
                    ? ringworld$sampleSurface(world, anchorX + sampleDistance, z)
                    : RingworldSurfaceSample.MISSING;
            RingworldSurfaceSample negative = sampleDistance >= 32.0
                    ? ringworld$sampleSurface(world, anchorX - sampleDistance, z)
                    : RingworldSurfaceSample.MISSING;
            positiveHeights[point] = positive.height;
            negativeHeights[point] = negative.height;
            positiveColors[point] = positive.color;
            negativeColors[point] = negative.color;
        }
        ringworld$smoothEdgeProfile(positiveHeights, positiveColors);
        ringworld$smoothEdgeProfile(negativeHeights, negativeColors);
        for (int point = 0; point < points; point++) {
            signature = 31 * signature + Double.hashCode(positiveHeights[point]);
            signature = 31 * signature + positiveColors[point];
            signature = 31 * signature + Double.hashCode(negativeHeights[point]);
            signature = 31 * signature + negativeColors[point];
        }
        signature = 31 * signature + cameraChunkX;
        signature = 31 * signature + cameraChunkZ;
        signature = 31 * signature + viewDistanceChunks;
        ringworld$edgeProfile = new RingworldArchProfile(
                positiveHeights, negativeHeights, positiveColors, negativeColors, signature);
        ringworld$edgeProfileEpoch = epoch;
        ringworld$edgeProfileCameraChunkX = cameraChunkX;
        ringworld$edgeProfileCameraChunkZ = cameraChunkZ;
        ringworld$edgeProfileViewDistance = viewDistanceChunks;
        return ringworld$edgeProfile;
    }

    @Unique
    private static void ringworld$smoothEdgeProfile(double[] heights, int[] colors) {
        // Two small blur passes turn block/map samples into the atmospheric
        // low-frequency information appropriate for a distant sky proxy.
        for (int pass = 0; pass < 2; pass++) {
            double[] sourceHeights = heights.clone();
            int[] sourceColors = colors.clone();
            for (int point = 0; point < heights.length; point++) {
                int first = Math.max(0, point - 1);
                int last = Math.min(heights.length - 1, point + 1);
                double heightTotal = 0.0;
                int heightWeight = 0;
                int red = 0;
                int green = 0;
                int blue = 0;
                int colorWeight = 0;
                for (int sample = first; sample <= last; sample++) {
                    int weight = sample == point ? 2 : 1;
                    heightTotal += sourceHeights[sample] * weight;
                    heightWeight += weight;
                    int color = sourceColors[sample];
                    if (color >= 0) {
                        red += (color >> 16 & 0xFF) * weight;
                        green += (color >> 8 & 0xFF) * weight;
                        blue += (color & 0xFF) * weight;
                        colorWeight += weight;
                    }
                }
                heights[point] = heightTotal / heightWeight;
                colors[point] = colorWeight == 0 ? -1
                        : (red / colorWeight << 16)
                        | (green / colorWeight << 8)
                        | blue / colorWeight;
            }
        }
    }

    @Unique
    private static RingworldSurfaceSample ringworld$sampleSurface(ClientWorld world, double x, double z) {
        int blockX = (int)Math.floor(x);
        int blockZ = (int)Math.floor(z);
        if (world.getChunkManager().getChunk(blockX >> 4, blockZ >> 4,
                ChunkStatus.FULL, false) == null) return RingworldSurfaceSample.MISSING;
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ);
        BlockPos surface = new BlockPos(blockX, topY - 1, blockZ);
        int color = world.getBlockState(surface).getMapColor(world, surface).color;
        return new RingworldSurfaceSample(topY, color);
    }

    @Unique
    private static double ringworld$archHeight(RingworldArchProfile profile, double angle,
                                               int point, double terrainDetail,
                                               RingTerrainAtlas.SurfaceSample atlasSample) {
        double sampled = angle <= Math.PI
                ? profile.positiveHeights[point] : profile.negativeHeights[point];
        double distant = atlasSample.present() ? atlasSample.height() : RingGeometry.SURFACE_Y;
        return sampled + (distant - sampled) * terrainDetail;
    }

    @Unique
    private static int ringworld$edgeColor(RingworldArchProfile profile, double angle, int point) {
        return angle <= Math.PI ? profile.positiveColors[point] : profile.negativeColors[point];
    }

    @Unique
    private record RingworldSurfaceSample(double height, int color) {
        private static final RingworldSurfaceSample MISSING =
                new RingworldSurfaceSample(RingGeometry.SURFACE_Y, -1);
    }

    @Unique
    private record RingworldArchProfile(double[] positiveHeights, double[] negativeHeights,
                                        int[] positiveColors, int[] negativeColors, int signature) { }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideMoon(MoonPhase phase, float alpha, MatrixStack matrices, CallbackInfo ci) {
        if (ClientRingState.geometry() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(float alpha, MatrixStack matrices, CallbackInfo ci) {
        if (ClientRingState.geometry() != null && !ringworld$renderingCenteredSun) ci.cancel();
    }

    @Inject(method = "renderCelestialBodies", at = @At("TAIL"))
    private void ringworld$renderRingAndShadowPanel(MatrixStack matrices, float sunAngle, float moonAngle,
                                                    float starAngle, MoonPhase moonPhase, float alpha,
                                                    float starBrightness, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        RingSurfaceTextureRenderer.render(matrices, geometry,
                new Vec3d(ringworld$archCameraX, ringworld$archCameraY, ringworld$archCameraZ),
                ringworld$archBrightness, alpha);

        // Vanilla drew stars after its first sun. The Arch must cover the
        // stars but sit behind the central star, so redraw the fixed sun once
        // after the proxy and place the nearer shadow array over that.
        matrices.push();
        ringworld$applyStarFrame(matrices);
        ringworld$renderingCenteredSun = true;
        try {
            ringworld$invokeRenderSun(alpha, matrices);
        } finally {
            ringworld$renderingCenteredSun = false;
        }
        matrices.pop();

        if (!ringworld$shadowPanel.visible() || ringworld$shadowPanelVertexBuffer == null) return;

        matrices.push();
        ringworld$applyStarFrame(matrices);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(matrices.peek().getPositionMatrix());
        // The static array is centered around the sun, not the camera. This
        // translate-then-rotate transform advances all twenty small panels as
        // one physical inner ring, while each panel curves away from the view.
        modelView.translate(0.0F, RingSkyCycle.SUN_RENDER_DISTANCE, 0.0F);
        modelView.rotateX((float)Math.toRadians(ringworld$shadowPanel.offset()));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                modelView, new Vector4f(1.0F), new Vector3f(), new Matrix4f());
        MinecraftClient client = MinecraftClient.getInstance();
        GpuTextureView color = client.getFramebuffer().getColorAttachmentView();
        GpuTextureView depth = client.getFramebuffer().getDepthAttachmentView();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RingWorld shadow panel", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(RINGWORLD_SHADOW_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, ringworld$shadowPanelVertexBuffer);
            pass.draw(0, RINGWORLD_SHADOW_VERTICES);
        }

        modelView.popMatrix();
        matrices.pop();
    }

    /**
     * Maps model +Y to the physical star and keeps model +X across the ring's
     * width. Sun and shadow array therefore share one ring-centred frame.
     */
    @Unique
    private void ringworld$applyStarFrame(MatrixStack matrices) {
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(ringworld$starTiltRadians));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
    }

    @Unique
    private void ringworld$renderDistantArch(MatrixStack matrices, RingGeometry geometry, float rainAlpha) {
        if (ringworld$archVertexBuffer == null || ringworld$archVertexCount == 0) return;
        float skyScale = (float)RingVisibility.skyScale(geometry);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(matrices.peek().getPositionMatrix());
        modelView.translate(0.0F,
                (float)((RingGeometry.SURFACE_Y - ringworld$archCameraY) * skyScale),
                -ringworld$archCameraZ * skyScale);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                modelView,
                new Vector4f(ringworld$archBrightness, ringworld$archBrightness,
                        ringworld$archBrightness, rainAlpha),
                new Vector3f(), new Matrix4f());
        MinecraftClient client = MinecraftClient.getInstance();
        GpuTextureView color = client.getFramebuffer().getColorAttachmentView();
        GpuTextureView depth = client.getFramebuffer().getDepthAttachmentView();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RingWorld distant Arch", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(RINGWORLD_ARCH_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, ringworld$archVertexBuffer);
            pass.draw(0, ringworld$archVertexCount);
        }

        modelView.popMatrix();
    }
}
