package dev.ringworld.client.render;

import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Frustum adapter matching the cylindrical terrain vertex transform.
 *
 * <p>The vanilla octree is built in flat chunk coordinates. Its branch boxes
 * can cover a very large angle and cannot safely bound a curved subtree, so
 * branches are traversed conservatively. Individual 16-block section leaves
 * retain precise culling using their transformed cylindrical envelope. This
 * fixes upward-looking chunk pop without drawing every section at distance.</p>
 */
public final class CurvedRingFrustum extends Frustum {
    private static final int INTERSECTING = -1;

    private final RingGeometry geometry;
    private final Vec3 cameraPosition;

    public CurvedRingFrustum(Frustum original, RingGeometry geometry, Vec3 cameraPosition) {
        super(original);
        this.geometry = geometry;
        this.cameraPosition = cameraPosition;
    }

//    @Override
//    public int cubeInFrustum(BoundingBox box) {
//        // Octree branches are only an acceleration structure. Always
//        // descending them prevents a flat parent box from discarding curved
//        // children which rise into the camera's view.
//        return INTERSECTING;
//    }

    @Override
    public boolean isVisible(AABB canonicalBox) {
        AABB local = geometry.toCameraLocalBounds(canonicalBox, cameraPosition);
        // Frustum stores an absolute camera origin and subtracts it internally.
        // Rebase the already camera-local curved envelope to that origin.
        AABB rebased = local.move(cameraPosition);
        return super.isVisible(rebased);
    }
}
