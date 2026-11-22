package dev.ringworld.experiment;

import static org.lwjgl.opengl.GL11.*;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingSkyView;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.experiment.mixin.AtlasBuffersAccess;
import dev.ringworld.world.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.opengl.*;

/** A frozen copy of the completed demo Atlas for visual comparisons with the normal renderer. */
public final class FullRingScene {
    static final boolean ENABLED = Boolean.getBoolean("ringworld.fullSceneTrial");
    private static final Identifier TERRAIN =
            Identifier.fromNamespaceAndPath("ringworldtrial", "map");
    private static final Identifier WALLS =
            Identifier.fromNamespaceAndPath("ringworldtrial", "walls");
    private static final int SEGMENTS = 256, BANDS = 24, ROWS = BANDS + 1;
    private static boolean ready;
    private static Vec3 lastEye;
    private static Vec3[] terrainNormals;
    private static Vec3[] points,
            leftOuter,
            leftBottom,
            rightOuter,
            rightBottom,
            leftInner,
            rightInner;
    private static float[] crestOuter, crestInner;
    private static double[] heights;
    private static long worldHash;
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel>
            capturedLevel = new java.lang.ref.WeakReference<>(null);

    private FullRingScene() {}

    static void submit(
            PoseStack pose,
            SubmitNodeCollector collector,
            RingSkyView view,
            RingTerrainAtlas atlas,
            RingNearbyProjection correction) {
        if (!net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()) return;
        var mc = Minecraft.getInstance();
        var geom = atlas.geometry();
        var eye = view.eye();
        if (!ready || worldHash != atlas.worldHash() || capturedLevel.get() != view.world()) {
            var terrain = RingSurfaceTextureRenderer.waterSurface();
            var walls = AtlasBuffersAccess.walls();
            if (!RingSurfaceTextureRenderer.captureReady() || terrain == null || walls == null)
                return;
            copyAlbedo(mc, TERRAIN, terrain);
            copyAlbedo(mc, WALLS, walls);
            heights = new double[(SEGMENTS + 1) * ROWS];
            var style = ClientRingState.wallStyle();
            double width = geom.widthBlocks() - 2.0 * style.thicknessBlocks();
            for (int x = 0; x <= SEGMENTS; x++)
                for (int z = 0; z < ROWS; z++)
                    heights[x * ROWS + z] =
                            atlas.sample(
                                            x * geom.circumferenceBlocks() / (double) SEGMENTS,
                                            geom.minWidthZ()
                                                    + style.thicknessBlocks()
                                                    + z * width / BANDS)
                                    .height();
            worldHash = atlas.worldHash();
            capturedLevel = new java.lang.ref.WeakReference<>(view.world());
            lastEye = null;
            ready = true;
            System.out.println(
                    "FULL_SCENE completed terrain and four wall strips copied from the stock"
                            + " Atlas");
        }
        int wallHeight = ClientRingState.wallHeightBlocks(),
                bottom = view.world().getMinY(),
                top = bottom + wallHeight;
        int thick = ClientRingState.wallStyle().thicknessBlocks();
        double width = geom.widthBlocks() - 2.0 * thick, z0 = geom.minWidthZ() + thick;
        double viewRange = mc.options.getEffectiveRenderDistance() * 16.0;
        if (!eye.equals(lastEye)) {
            points = new Vec3[heights.length];
            leftOuter = arr();
            leftBottom = arr();
            rightOuter = arr();
            rightBottom = arr();
            leftInner = arr();
            rightInner = arr();
            crestOuter = new float[SEGMENTS + 1];
            crestInner = new float[SEGMENTS + 1];
            for (int k = 0; k <= SEGMENTS; k++) {
                int x = k * geom.circumferenceBlocks() / SEGMENTS;
                double dx = geom.shortestCircumferenceDelta(eye.x, x);
                double dz = Math.max(0.0, Math.max(z0 - eye.z, eye.z - z0 - width));
                double distance = Math.hypot(dx, dz);
                double t = Math.clamp((distance - viewRange * 0.35) / (viewRange * 0.65), 0, 1);
                // Put the overlap below local blocks and converge on surveyed height at the far
                // edge.
                double inset = 12.0 * (1.0 - t * t * (3.0 - 2.0 * t));
                for (int j = 0; j < ROWS; j++)
                    points[k * ROWS + j] =
                            project(
                                    geom,
                                    correction,
                                    eye,
                                    x,
                                    heights[k * ROWS + j] - inset,
                                    z0 + j * width / BANDS);
                double crest =
                        top
                                - RingWallPattern.topCollapseDepth(
                                        ClientRingState.wallStyle(),
                                        x,
                                        0,
                                        geom.circumferenceBlocks(),
                                        ClientRingState.generatorSeed());
                double inner =
                        top
                                - RingWallPattern.topCollapseDepth(
                                        ClientRingState.wallStyle(),
                                        x,
                                        thick - 1,
                                        geom.circumferenceBlocks(),
                                        ClientRingState.generatorSeed());
                crestOuter[k] = (float) ((crest - bottom) / wallHeight);
                crestInner[k] = (float) ((inner - bottom) / wallHeight);
                leftOuter[k] = project(geom, correction, eye, x, crest - inset, geom.minWidthZ());
                leftBottom[k] = project(geom, correction, eye, x, bottom, geom.minWidthZ());
                rightOuter[k] =
                        project(geom, correction, eye, x, crest - inset, geom.maxWidthZ() + 1);
                rightBottom[k] = project(geom, correction, eye, x, bottom, geom.maxWidthZ() + 1);
                leftInner[k] = project(geom, correction, eye, x, inner - inset, z0);
                rightInner[k] = project(geom, correction, eye, x, inner - inset, z0 + width);
            }
            terrainNormals = new Vec3[SEGMENTS * BANDS];
            for (int k = 0; k < SEGMENTS; k++) {
                for (int j = 0; j < BANDS; j++) {
                    int a = k * ROWS + j;
                    terrainNormals[k * BANDS + j] =
                            points[a + 1]
                                    .subtract(points[a])
                                    .cross(points[a + ROWS].subtract(points[a]))
                                    .normalize();
                }
            }
            lastEye = eye;
        }
        double radius = correction == null ? geom.radius() : correction.referenceRadius();
        PatchMaterial.opticalRange =
                (float)
                        Math.hypot(
                                Math.abs(radius + geom.surfaceReferenceY() - eye.y)
                                        + radius
                                        + geom.surfaceReferenceY()
                                        - bottom,
                                Math.abs(eye.z) + geom.widthBlocks() / 2);
        double coverage = Math.min(48.0, viewRange * 0.35);
        collector.submitCustomGeometry(
                pose,
                PatchMaterial.textured(TERRAIN),
                (mat, out) -> {
                    for (int k = 0; k < SEGMENTS; k++) {
                        float u0 = (float) k / SEGMENTS, u1 = (float) (k + 1) / SEGMENTS;
                        double dx =
                                geom.shortestCircumferenceDelta(
                                        eye.x, (k + .5) * geom.circumferenceBlocks() / SEGMENTS);
                        for (int j = 0; j < BANDS; j++) {
                            int a = k * ROWS + j, b = a + 1, d = a + ROWS, c = d + 1;
                            double dz = z0 + (j + .5) * width / BANDS - eye.z,
                                    dy = heights[a] - eye.y;
                            if (dx * dx + dz * dz + dy * dy < coverage * coverage) continue;
                            float v0 = (float) ((thick + j * width / BANDS) / geom.widthBlocks()),
                                    v1 =
                                            (float)
                                                    ((thick + (j + 1) * width / BANDS)
                                                            / geom.widthBlocks());
                            Vec3 n = terrainNormals[k * BANDS + j];
                            vertex(mat, out, points[a], u0, v0, n);
                            vertex(mat, out, points[b], u0, v1, n);
                            vertex(mat, out, points[c], u1, v1, n);
                            vertex(mat, out, points[d], u1, v0, n);
                        }
                    }
                });
        collector.submitCustomGeometry(
                pose,
                PatchMaterial.textured(WALLS),
                (mat, out) -> {
                    for (int k = 0; k < SEGMENTS; k++) {
                        double dx =
                                geom.shortestCircumferenceDelta(
                                        eye.x, (k + .5) * geom.circumferenceBlocks() / SEGMENTS);
                        for (int side = 0; side < 2; side++) {
                            int edge = side == 0 ? 0 : BANDS;
                            double dz = z0 + (side == 0 ? 0 : width) - eye.z, dy = top - eye.y;
                            if (dx * dx + dz * dz + dy * dy < coverage * coverage) continue;
                            Vec3[] outer = side == 0 ? leftOuter : rightOuter,
                                    base = side == 0 ? leftBottom : rightBottom,
                                    inside = side == 0 ? leftInner : rightInner;
                            float u = (float) k / SEGMENTS, v = (float) (k + 1) / SEGMENTS;
                            Vec3 normal = new Vec3(0, 0, side == 0 ? -1 : 1);
                            int strip = side + 2, a = k * ROWS + edge, b = (k + 1) * ROWS + edge;
                            vertex(mat, out, base[k], u, strip / 4f + .0001f, normal);
                            vertex(mat, out, base[k + 1], v, strip / 4f + .0001f, normal);
                            vertex(
                                    mat,
                                    out,
                                    outer[k + 1],
                                    v,
                                    (strip + crestOuter[k + 1] - .001f) / 4f,
                                    normal);
                            vertex(
                                    mat,
                                    out,
                                    outer[k],
                                    u,
                                    (strip + crestOuter[k] - .001f) / 4f,
                                    normal);
                            normal = normal.scale(-1);
                            vertex(
                                    mat,
                                    out,
                                    points[a],
                                    u,
                                    (float) (side + (heights[a] - bottom) / wallHeight) / 4,
                                    normal);
                            vertex(
                                    mat,
                                    out,
                                    points[b],
                                    v,
                                    (float) (side + (heights[b] - bottom) / wallHeight) / 4,
                                    normal);
                            vertex(
                                    mat,
                                    out,
                                    inside[k + 1],
                                    v,
                                    (side + crestInner[k + 1] - .001f) / 4,
                                    normal);
                            vertex(
                                    mat,
                                    out,
                                    inside[k],
                                    u,
                                    (side + crestInner[k] - .001f) / 4,
                                    normal);
                            Vec3 cap =
                                    outer[k + 1]
                                            .subtract(outer[k])
                                            .cross(inside[k].subtract(outer[k]))
                                            .normalize();
                            if (side == 0) cap = cap.scale(-1);
                            vertex(mat, out, outer[k], u, (strip + crestOuter[k] - .001f) / 4, cap);
                            vertex(
                                    mat,
                                    out,
                                    outer[k + 1],
                                    v,
                                    (strip + crestOuter[k + 1] - .001f) / 4,
                                    cap);
                            vertex(
                                    mat,
                                    out,
                                    inside[k + 1],
                                    v,
                                    (strip + crestInner[k + 1] - .012f) / 4,
                                    cap);
                            vertex(
                                    mat,
                                    out,
                                    inside[k],
                                    u,
                                    (strip + crestInner[k] - .012f) / 4,
                                    cap);
                        }
                    }
                });
    }

    private static Vec3[] arr() {
        return new Vec3[SEGMENTS + 1];
    }

    private static Vec3 project(
            RingGeometry geom,
            RingNearbyProjection correction,
            Vec3 eye,
            double x,
            double y,
            double z) {
        var point = new Vec3(x, y, z);
        return correction == null
                ? geom.toCameraLocal(point, eye)
                : correction.position(point, eye);
    }

    private static void vertex(
            PoseStack.Pose mat, VertexConsumer out, Vec3 p, float u, float v, Vec3 n) {
        out.addVertex(mat, (float) p.x, (float) p.y, (float) p.z)
                .setColor(-1)
                .setUv(u, v)
                .setLight(0xf00000)
                .setNormal(mat, (float) n.x, (float) n.y, (float) n.z);
    }

    private static void copyAlbedo(Minecraft mc, Identifier name, GpuTextureView source) {
        var texture = (GlTexture) source.texture();
        int old = glGetInteger(GL_TEXTURE_BINDING_2D),
                oldPbo = glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        var image = new NativeImage(texture.getWidth(0), texture.getHeight(0), false);
        boolean owned = false;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            glBindTexture(GL_TEXTURE_2D, texture.glId());
            var rgba = image.getPixelBytes();
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            for (int i = 3; i < rgba.limit(); i += 4) rgba.put(i, (byte) 255);
            mc.getTextureManager().register(name, new DynamicTexture(name::toString, image));
            owned = true;
        } finally {
            if (!owned) image.close();
            glBindTexture(GL_TEXTURE_2D, old);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
    }

    public static boolean replacesSky(RingSkyView view) {
        var atlas = ClientRingState.terrainAtlas();
        return ENABLED
                && ready
                && view.primary()
                && view.fluid() != net.minecraft.world.level.material.FogType.WATER
                && capturedLevel.get() == view.world()
                && atlas != null
                && atlas.isComplete()
                && atlas.worldHash() == worldHash
                && atlas.geometry().equals(view.geometry())
                && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
    }
}
