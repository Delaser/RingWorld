package dev.ringworld.experiment;

import static org.lwjgl.opengl.GL11.*;

import com.mojang.blaze3d.opengl.GlTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class PatchProof {
    public static boolean armed, done;
    public static int phase, frames;
    static int n;
    static final long[] times = new long[300];
    static long prev;
    static double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

    public static void strafe(Minecraft mc) {
        if (!PERF || !Boolean.getBoolean("patch.strafe")) return;
        var player = mc.player;
        double z = 0.5 + 3 * Math.sin(frames * Math.PI / 75);
        player.setPos(player.getX(), player.getY(), z);
        mc.getConnection()
                .send(
                        new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                                player.getX(),
                                player.getY(),
                                z,
                                player.getYRot(),
                                player.getXRot(),
                                player.onGround(),
                                player.horizontalCollision));
    }

    static volatile boolean planted;
    static final boolean PERF = Boolean.getBoolean("patch.perf");

    public static void frame() {
        if (!armed || done) return;
        var mc = Minecraft.getInstance();
        if (PERF) {
            if (frames == 0) AtlasPatch.visible = phase == 1 || phase == 2;
            long now = System.nanoTime();
            if (frames > 90) {
                times[n++] = now - prev;
                double z = mc.gameRenderer.mainCamera().position().z;
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            prev = now;
            frames++;
            if (n == times.length) {
                long sum = 0;
                for (long t : times) sum += t;
                java.util.Arrays.sort(times);
                System.out.printf(
                        "PATCH_TIMING phase=%d on=%s n=%d mean=%.4f p50=%.4f p95=%.4f over50=%d%n",
                        phase,
                        AtlasPatch.visible,
                        n,
                        sum / 1e6 / n,
                        times[n / 2] / 1e6,
                        times[(int) (n * .95)] / 1e6,
                        java.util.Arrays.stream(times).filter(t -> t > 50000000).count());
                System.out.printf(
                        "PATCH_MOVEMENT phase=%d minZ=%.3f maxZ=%.3f%n", phase, minZ, maxZ);
                minZ = Double.POSITIVE_INFINITY;
                maxZ = Double.NEGATIVE_INFINITY;
                n = 0;
                frames = 0;
                if (++phase == 4) {
                    strafe(mc);
                    done = true;
                }
            }
            return;
        }
        if (phase == 0) AtlasPatch.visible = false;
        if (phase == 1 || phase == 2) AtlasPatch.visible = true;
        if (phase == 3) AtlasPatch.visible = false;
        if (phase == 2 && !planted) return;
        if (++frames < (phase == 2 ? 160 : 60)) return;
        capture(mc, "stage-" + phase);
        if (phase == 1) {
            var server = mc.getSingleplayerServer();
            server.execute(
                    () -> {
                        var level = server.overworld();
                        var p = new BlockPos(2026, Integer.getInteger("patch.treeY", 120), 0);
                        level.setBlockAndUpdate(p.below(), Blocks.DIRT.defaultBlockState());
                        var feature =
                                level.registryAccess()
                                        .lookupOrThrow(Registries.CONFIGURED_FEATURE)
                                        .getOrThrow(TreeFeatures.OAK)
                                        .value();
                        boolean ok =
                                feature.place(
                                        level,
                                        level.getChunkSource().getGenerator(),
                                        RandomSource.create(416),
                                        p);
                        System.out.println(
                                "PATCH_OAK generated="
                                        + ok
                                        + " at="
                                        + p
                                        + " trunk="
                                        + level.getBlockState(p));
                        planted = true;
                    });
        }
        if (++phase == 4) done = true;
        frames = 0;
    }

    static void capture(Minecraft mc, String name) {
        var target = mc.gameRenderer.mainRenderTarget();
        int id = ((GlTexture) target.getColorTexture()).glId();
        snap(name, id);
    }

    public static void snap(String name, int id) {
        int prev = glGetInteger(GL_TEXTURE_BINDING_2D),
                pbo = glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, id);
        int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
                h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        var buf = MemoryUtil.memAlloc(w * h * 4);
        try {
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int j = ((h - 1 - y) * w + x) * 4;
                    im.setRGB(
                            x,
                            y,
                            ((buf.get(j) & 255) << 16)
                                    | ((buf.get(j + 1) & 255) << 8)
                                    | (buf.get(j + 2) & 255));
                }
            File dir = new File("patch-proof");
            dir.mkdirs();
            ImageIO.write(im, "png", new File(dir, name + ".png"));
            System.out.println(
                    "PATCH_FINAL "
                            + name
                            + " eye="
                            + Minecraft.getInstance().gameRenderer.mainCamera().position());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            MemoryUtil.memFree(buf);
            glBindTexture(GL_TEXTURE_2D, prev);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        }
    }
}
