package dev.ringworld.platform.neoforge.compat.create610;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class RingCreate610KineticEmbeddingTransformStateTest {
    @Test
    void createBeforeGeometryRetainsIdentityThenTransitionsToCurved() {
        RingCreate610KineticEmbeddingTransformState state =
                new RingCreate610KineticEmbeddingTransformState();
        Matrix4f poseIdentity = state.pose();
        BlockPos anchor = new BlockPos(2050, 124, 12);
        Vec3 camera = new Vec3(2053.75, 121.75, 9.5);
        Vec3i origin = new Vec3i(2048, 112, 0);

        assertEquals(RingCreate610KineticEmbeddingTransformState.Result.IDENTITY,
                state.update(anchor, camera, origin, null));
        assertSame(poseIdentity, state.pose());
        assertFalse(state.curved());
        assertTrue(identity(state.pose()));

        assertEquals(RingCreate610KineticEmbeddingTransformState.Result.CURVED,
                state.update(anchor, camera, origin, new RingGeometry(128, 2048)));
        assertSame(poseIdentity, state.pose());
        assertTrue(state.curved());
        assertFalse(identity(state.pose()));
    }

    @Test
    void invalidLiveGeometryInputNeverRetainsPreviousCurvature() {
        RingCreate610KineticEmbeddingTransformState state =
                new RingCreate610KineticEmbeddingTransformState();
        RingGeometry geometry = new RingGeometry(128, 2048);
        BlockPos anchor = new BlockPos(2050, 124, 12);
        Vec3i origin = new Vec3i(2048, 112, 0);
        assertEquals(RingCreate610KineticEmbeddingTransformState.Result.CURVED,
                state.update(anchor, new Vec3(2053.75, 121.75, 9.5), origin, geometry));
        assertEquals(RingCreate610KineticEmbeddingTransformState.Result.MALFORMED,
                state.update(anchor, new Vec3(Double.NaN, 121.75, 9.5), origin, geometry));
        assertFalse(state.curved());
        assertTrue(identity(state.pose()));
    }

    private static boolean identity(Matrix4f matrix) {
        float[] actual = matrix.get(new float[16]);
        float[] expected = new Matrix4f().get(new float[16]);
        for (int index = 0; index < actual.length; index++) {
            if (Math.abs(actual[index] - expected[index]) > 1.0e-6F) return false;
        }
        return true;
    }

}
