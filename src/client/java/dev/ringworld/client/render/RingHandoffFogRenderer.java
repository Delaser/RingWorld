package dev.ringworld.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * A camera-facing atmospheric volume over the live-chunk/Arch handoff.
 * Unlike the sky proxy's own haze, this is drawn after terrain, so tiny gaps
 * or a one-frame disagreement between chunk streaming and the LOD cannot show
 * through as a hard line.
 */
public final class RingHandoffFogRenderer {
    private static final int ACROSS_SEGMENTS = 64;
    private static final double[] RADIAL_FACTORS = {0.82, 0.90, 0.98, 1.08, 1.20};
    private static final double[] RADIAL_ALPHA = {0.055, 0.085, 0.13, 0.10, 0.055};
    private static final double[] HEIGHTS = {-48.0, 16.0, 48.0, 80.0, 112.0, 160.0, 224.0};
    private static final double[] HEIGHT_ALPHA = {0.0, 0.34, 0.82, 1.0, 0.82, 0.34, 0.0};
    private static final int MAX_VERTICES = ACROSS_SEGMENTS * RADIAL_FACTORS.length
            * (HEIGHTS.length - 1) * 2 * 6;
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("ringworld", "pipeline/handoff_fog"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .build());

    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static RingGeometry bufferedGeometry;
    private static int bufferedViewDistance = -1;
    private static int bufferedCameraYCell = Integer.MIN_VALUE;
    private static int bufferedCameraZCell = Integer.MIN_VALUE;
    private static int bufferedColorKey = -1;
    private static int skyColor = 0x78A7FF;

    private RingHandoffFogRenderer() { }

    public static void updateSkyColor(int color) {
        skyColor = color & 0xFFFFFF;
    }

    public static void render(WorldRenderContext context) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.world == null || client.gameRenderer == null
                || !client.gameRenderer.getCamera().isReady()) return;

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        int viewDistanceBlocks = client.options.getClampedViewDistance() * 16;
        ensureBuffer(geometry, camera, viewDistanceBlocks);
        if (vertexBuffer == null || vertexCount == 0) return;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(context.matrices().peek().getPositionMatrix());
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                modelView, new Vector4f(1.0F), new Vector3f(), new Matrix4f());
        GpuTextureView color = client.getFramebuffer().getColorAttachmentView();
        GpuTextureView depth = client.getFramebuffer().getDepthAttachmentView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RingWorld handoff fog", color, OptionalInt.empty(),
                depth, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
        modelView.popMatrix();
    }

    private static void ensureBuffer(RingGeometry geometry, Vec3d camera,
                                     int viewDistanceBlocks) {
        int cameraYCell = (int)Math.floor(camera.y / 4.0);
        int cameraZCell = (int)Math.floor(camera.z / 4.0);
        int colorKey = ((skyColor >> 16 & 0xFF) >> 4) << 8
                | ((skyColor >> 8 & 0xFF) >> 4) << 4
                | (skyColor & 0xFF) >> 4;
        if (geometry.equals(bufferedGeometry)
                && viewDistanceBlocks == bufferedViewDistance
                && cameraYCell == bufferedCameraYCell
                && cameraZCell == bufferedCameraZCell
                && colorKey == bufferedColorKey
                && vertexBuffer != null) return;

        Vec3d anchoredCamera = new Vec3d(camera.x, cameraYCell * 4.0 + 2.0,
                cameraZCell * 4.0 + 2.0);
        double handoffRadius = Math.min(viewDistanceBlocks,
                geometry.circumferenceBlocks() * 0.45);
        VertexFormat format = VertexFormats.POSITION_COLOR;
        int count = 0;
        try (BufferAllocator allocator = BufferAllocator.fixedSized(
                MAX_VERTICES * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    allocator, VertexFormat.DrawMode.TRIANGLES, format);
            for (int layer = 0; layer < RADIAL_FACTORS.length; layer++) {
                double radius = handoffRadius * RADIAL_FACTORS[layer];
                double minDz = Math.max(geometry.minWidthZ() - anchoredCamera.z,
                        -radius * 0.995);
                double maxDz = Math.min(geometry.maxWidthZ() + 1.0 - anchoredCamera.z,
                        radius * 0.995);
                if (maxDz <= minDz) continue;
                for (int segment = 0; segment < ACROSS_SEGMENTS; segment++) {
                    double dz0 = minDz + (maxDz - minDz) * segment / ACROSS_SEGMENTS;
                    double dz1 = minDz + (maxDz - minDz) * (segment + 1) / ACROSS_SEGMENTS;
                    double dx0 = Math.sqrt(Math.max(0.0, radius * radius - dz0 * dz0));
                    double dx1 = Math.sqrt(Math.max(0.0, radius * radius - dz1 * dz1));
                    for (int direction : new int[]{-1, 1}) {
                        for (int height = 0; height < HEIGHTS.length - 1; height++) {
                            int color0 = fogColor(RADIAL_ALPHA[layer] * HEIGHT_ALPHA[height]);
                            int color1 = fogColor(RADIAL_ALPHA[layer] * HEIGHT_ALPHA[height + 1]);
                            fogVertex(builder, geometry, anchoredCamera,
                                    direction * dx0, HEIGHTS[height], dz0, color0);
                            fogVertex(builder, geometry, anchoredCamera,
                                    direction * dx1, HEIGHTS[height], dz1, color0);
                            fogVertex(builder, geometry, anchoredCamera,
                                    direction * dx1, HEIGHTS[height + 1], dz1, color1);
                            fogVertex(builder, geometry, anchoredCamera,
                                    direction * dx0, HEIGHTS[height], dz0, color0);
                            fogVertex(builder, geometry, anchoredCamera,
                                    direction * dx1, HEIGHTS[height + 1], dz1, color1);
                            fogVertex(builder, geometry, anchoredCamera,
                                    direction * dx0, HEIGHTS[height + 1], dz0, color1);
                            count += 6;
                        }
                    }
                }
            }
            try (BuiltBuffer built = builder.end()) {
                GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                        () -> "RingWorld handoff fog", GpuBuffer.USAGE_VERTEX, built.getBuffer());
                if (vertexBuffer != null) vertexBuffer.close();
                vertexBuffer = replacement;
            }
        }
        vertexCount = count;
        bufferedGeometry = geometry;
        bufferedViewDistance = viewDistanceBlocks;
        bufferedCameraYCell = cameraYCell;
        bufferedCameraZCell = cameraZCell;
        bufferedColorKey = colorKey;
    }

    private static void fogVertex(BufferBuilder builder, RingGeometry geometry, Vec3d camera,
                                  double dx, double y, double dz, int color) {
        Vec3d local = geometry.toCameraLocal(
                new Vec3d(camera.x + dx, y, camera.z + dz), camera);
        builder.vertex((float)local.x, (float)local.y, (float)local.z).color(color);
    }

    private static int fogColor(double alpha) {
        int red = ((skyColor >> 16 & 0xFF) * 3 + 255) / 4;
        int green = ((skyColor >> 8 & 0xFF) * 3 + 255) / 4;
        int blue = ((skyColor & 0xFF) * 3 + 255) / 4;
        int a = (int)Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
        return a << 24 | red << 16 | green << 8 | blue;
    }

    public static void clear() {
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        vertexCount = 0;
        bufferedGeometry = null;
        bufferedViewDistance = -1;
        bufferedCameraYCell = Integer.MIN_VALUE;
        bufferedCameraZCell = Integer.MIN_VALUE;
        bufferedColorKey = -1;
    }
}
