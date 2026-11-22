package dev.ringworld.experiment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingDistortionTuning;
import dev.ringworld.client.render.RingSkyView;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNearbyProjection;
import dev.ringworld.world.RingTerrainAtlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

/** One 160 × 36 strip of the 2,048 × 128 trial ring. No changes to the live Atlas or save. */
public final class AtlasPatch {
    private static final Identifier WHITE = Identifier.withDefaultNamespace("block/white_concrete");
    private static final int COLUMNS = 11;
    private static final int ROWS = 4;
    private static final boolean MARK = Boolean.getBoolean("ringworld.patchMarker");
    static boolean visible = true;
    private static WeakReference<RingTerrainAtlas> lastAtlas = new WeakReference<>(null);
    private static long lastRevision;
    private static Vec3 lastEye;
    private static RingNearbyProjection lastCorrection;
    private static Surface surface;

    private AtlasPatch() {}

    public static void submit(
            PoseStack poses, LevelRenderState frame, SubmitNodeCollector collector) {
        if (NativeAtlasTarget.ENABLED || !visible) return;
        var view = ((RingSkyView.Holder) frame.skyRenderState).ringworld$view();
        if (view == null
                || !view.primary()
                || view.fluid() == FogType.WATER
                || view.world() == null) return;
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (atlas == null || !atlas.isComplete() || !atlas.geometry().equals(view.geometry()))
            return;
        RingGeometry geometry = atlas.geometry();
        if (geometry.circumferenceBlocks() != 2048 || geometry.widthBlocks() != 128) return;
        Vec3 eye = view.eye();
        var correction =
                RingDistortionTuning.enabled()
                        ? RingNearbyProjection.create(
                                geometry,
                                eye.y,
                                view.world().getMaxY() + 1,
                                RingDistortionTuning.chunks())
                        : null;
        if (FullRingScene.ENABLED) {
            FullRingScene.submit(poses, collector, view, atlas, correction);
            return;
        }
        if (lastAtlas.get() != atlas
                || lastRevision != atlas.revision()
                || !eye.equals(lastEye)
                || !java.util.Objects.equals(correction, lastCorrection)) {
            surface = Surface.sample(atlas, eye, correction);
            lastEye = eye;
            lastCorrection = correction;
            lastRevision = atlas.revision();
            if (lastAtlas.get() != atlas) lastAtlas = new WeakReference<>(atlas);
        }
        if (surface == null) return;
        var sprite =
                Minecraft.getInstance()
                        .getAtlasManager()
                        .getAtlasOrThrow(AtlasIds.BLOCKS)
                        .getSprite(WHITE);
        float u = (sprite.getU0() + sprite.getU1()) * 0.5F;
        float v = (sprite.getV0() + sprite.getV1()) * 0.5F;
        double radius = correction == null ? geometry.radius() : correction.referenceRadius();
        PatchMaterial.opticalRange = (float) Math.hypot(2 * radius, geometry.widthBlocks());
        Surface extracted = surface;
        collector.submitCustomGeometry(
                poses, PatchMaterial.material(), (pose, out) -> extracted.draw(pose, out, u, v));
    }

    private record Surface(Vec3[] points, int[] colors, Vec3[] normals) {
        static Surface sample(RingTerrainAtlas atlas, Vec3 eye, RingNearbyProjection correction) {
            var geometry = atlas.geometry();
            Vec3[] points = new Vec3[COLUMNS * ROWS];
            int[] colors = new int[points.length];
            Vec3[] normals = new Vec3[(COLUMNS - 1) * (ROWS - 1)];
            for (int col = 0; col < COLUMNS; col++) {
                int x = geometry.wrapBlockX(-368 + col * 16);
                for (int row = 0; row < ROWS; row++) {
                    int z = -18 + 12 * row;
                    var sample = atlas.sample(x, z);
                    if (!sample.present()) return null;
                    var point = new Vec3(x, sample.height(), z);
                    int i = col * ROWS + row;
                    points[i] =
                            correction == null
                                    ? geometry.toCameraLocal(point, eye)
                                    : correction.position(point, eye);
                    colors[i] = MARK ? 0xffff05ed : 0xff000000 | sample.color();
                    if (col > 0 && row > 0) {
                        int a = i - ROWS - 1;
                        normals[(col - 1) * (ROWS - 1) + row - 1] =
                                points[a + 1]
                                        .subtract(points[a])
                                        .cross(points[a + ROWS].subtract(points[a]))
                                        .normalize();
                    }
                }
            }
            return new Surface(points, colors, normals);
        }

        void draw(PoseStack.Pose pose, VertexConsumer out, float u, float v) {
            for (int col = 0; col < COLUMNS - 1; col++) {
                for (int row = 0; row < ROWS - 1; row++) {
                    int a = col * ROWS + row;
                    Vec3 normal = normals[col * (ROWS - 1) + row];
                    vertex(pose, out, a, u, v, normal);
                    vertex(pose, out, a + 1, u, v, normal);
                    vertex(pose, out, a + ROWS + 1, u, v, normal);
                    vertex(pose, out, a + ROWS, u, v, normal);
                }
            }
        }

        void vertex(PoseStack.Pose pose, VertexConsumer out, int i, float u, float v, Vec3 n) {
            Vec3 p = points[i];
            out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                    .setColor(colors[i])
                    .setUv(u, v)
                    .setLight(0xf000f0)
                    .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        }
    }
}
