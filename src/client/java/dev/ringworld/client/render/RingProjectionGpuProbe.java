package dev.ringworld.client.render;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNearbyProjection;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** Opt-in numerical driver check. Uses a private program and restores all touched GL bindings. */
public final class RingProjectionGpuProbe {
    private static boolean complete;
    private RingProjectionGpuProbe() { }

    public static void tick(Minecraft client) {
        if (complete || !Boolean.getBoolean("ringworld.projectionGpuProbe") || client.getOverlay() != null) return;
        complete = true;
        try {
            String source;
            try (var stream = client.getResourceManager().getResourceOrThrow(
                    ResourceLocation.withDefaultNamespace("shaders/include/ringworld_projection.glsl")).open()) {
                source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            verify(source);
        } catch (Exception failure) {
            RingWorldMod.LOGGER.error("[projection-gpu-probe] FAIL", failure);
            throw new IllegalStateException("GPU projection contract failed", failure);
        }
    }

    private static void verify(String projection) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousFeedback = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        int previousFeedbackZero = GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0);
        boolean discarded = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        int shader = 0, program = 0, vao = 0, input = 0, output = 0;
        try {
            shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(shader, """
                    #version 150
                    uniform ivec4 RingWorldLayout;
                    uniform vec4 RingWorldVertical;
                    uniform vec4 RingWorldDistortion;
                    uniform vec3 TestCamera;
                    in vec3 Position;
                    out vec3 Projected;
                    """ + projection + """
                    void main() {
                        Projected = ring_project_position(Position, TestCamera);
                        gl_Position = vec4(Projected, 1.0);
                    }
                    """);
            GL20.glCompileShader(shader);
            require(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0, GL20.glGetShaderInfoLog(shader));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, shader);
            GL20.glBindAttribLocation(program, 0, "Position");
            GL30.glTransformFeedbackVaryings(program, new CharSequence[]{"Projected"}, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            int layout = GL20.glGetUniformLocation(program, "RingWorldLayout");
            int vertical = GL20.glGetUniformLocation(program, "RingWorldVertical");
            int distortion = GL20.glGetUniformLocation(program, "RingWorldDistortion");
            int cameraUniform = GL20.glGetUniformLocation(program, "TestCamera");
            require(layout >= 0 && vertical >= 0 && distortion >= 0 && cameraUniform >= 0, "missing probe uniforms");
            vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            input = GL15.glGenBuffers(); output = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, input);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, output);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            int samples = 0;
            double maximumError = 0;
            for (int circumference : new int[]{1024, 2048, 16384, 32768}) {
                var geometry = new RingGeometry(128, circumference);
                for (int chunks : new int[]{1, 3, 8}) for (double cameraY : new double[]{-64, 64, 319, 512}) {
                    Vec3 camera = new Vec3(circumference - 0.25, cameraY, 5.5);
                    var mapping = RingNearbyProjection.create(geometry, cameraY, 320, chunks);
                    var points = new ArrayList<Vec3>();
                    double core = mapping.coreBlocks();
                    for (double delta : new double[]{-circumference * 1.5, -circumference / 2.0,
                            -core * 4, -core, -1.25, 0, 1.25, core, core * 4,
                            circumference / 2.0, circumference * 1.5}) {
                        for (int y : new int[]{-64, 64, 319}) for (int z : new int[]{-64, 0, 64})
                            points.add(new Vec3((float)(camera.x + delta), y, z));
                    }
                    FloatBuffer positions = MemoryUtil.memAllocFloat(points.size() * 3);
                    FloatBuffer projected = MemoryUtil.memAllocFloat(points.size() * 3);
                    try {
                        for (Vec3 point : points) positions.put((float)point.x).put((float)point.y).put((float)point.z);
                        positions.flip();
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, input);
                        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positions, GL15.GL_STATIC_DRAW);
                        GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, output);
                        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, (long)points.size() * 12, GL15.GL_STREAM_READ);
                        GL20.glUniform4i(layout, 1, circumference, 128, 160);
                        GL20.glUniform4f(vertical, 64, 96, 104, (float)geometry.physicalCenterY());
                        GL20.glUniform3f(cameraUniform, (float)camera.x, (float)camera.y, (float)camera.z);
                        for (boolean enabled : new boolean[]{false, true}) {
                            GL20.glUniform4f(distortion, enabled ? 1 : 0, (float)core,
                                    (float)mapping.blendEnd(), (float)mapping.referenceRadius());
                            GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                            GL11.glDrawArrays(GL11.GL_POINTS, 0, points.size());
                            GL30.glEndTransformFeedback();
                            projected.clear();
                            GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0L, projected);
                            for (int index = 0; index < points.size(); index++) {
                                Vec3 expected = enabled ? mapping.position(points.get(index), camera)
                                        : geometry.toCameraLocal(points.get(index), camera);
                                double error = Math.max(Math.abs(projected.get(index * 3) - expected.x),
                                        Math.max(Math.abs(projected.get(index * 3 + 1) - expected.y),
                                                Math.abs(projected.get(index * 3 + 2) - expected.z)));
                                require(Double.isFinite(error) && error <= 0.02,
                                        "projection mismatch: C=" + circumference + " chunks=" + chunks
                                                + " enabled=" + enabled + " cameraY=" + cameraY + " error=" + error);
                                maximumError = Math.max(maximumError, error);
                                samples++;
                            }
                        }
                    } finally {
                        MemoryUtil.memFree(projected); MemoryUtil.memFree(positions);
                    }
                }
            }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in projection probe");
            RingWorldMod.LOGGER.info("[projection-gpu-probe] PASS samples={} maxErrorBlocks={} vendor={} renderer={} version={}",
                    samples, maximumError, GL11.glGetString(GL11.GL_VENDOR),
                    GL11.glGetString(GL11.GL_RENDERER), GL11.glGetString(GL11.GL_VERSION));
        } finally {
            if (!discarded) GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArray);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, previousFeedbackZero);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, previousFeedback);
            if (output != 0) GL15.glDeleteBuffers(output);
            if (input != 0) GL15.glDeleteBuffers(input);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            if (program != 0) GL20.glDeleteProgram(program);
            if (shader != 0) GL20.glDeleteShader(shader);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
