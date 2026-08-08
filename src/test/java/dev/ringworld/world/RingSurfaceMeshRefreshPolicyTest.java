package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSurfaceMeshRefreshPolicyTest {
    @Test
    void partialAtlasReusesReferenceHeightMeshAcrossTextureRevisions() {
        assertFalse(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                true, true, false, false, 9L, -1L));
    }

    @Test
    void completeAtlasRebuildsHeightMeshOnlyWhenHeightsChange() {
        assertFalse(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                true, true, true, true, 9L, 9L));
        assertFalse(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                true, true, true, true, 9L, 9L));
        assertTrue(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                true, true, true, true, 10L, 9L));
    }

    @Test
    void layoutAndCompletionTransitionsAlwaysRebuild() {
        assertTrue(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                false, true, true, true, 3L, 3L));
        assertTrue(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                true, false, true, true, 3L, 3L));
        assertTrue(RingSurfaceMeshRefreshPolicy.shouldRebuild(
                true, true, true, false, 3L, -1L));
    }
}
