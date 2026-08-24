package dev.ringworld.client.render;

import dev.ringworld.world.RingGeometry;
import net.minecraft.world.phys.Vec3;

/**
 * Narrow 1.21.1 LevelRenderer bridge for the Atlas safety floor. It is queried
 * after vanilla has prepared the current frame's drawable section list and
 * before any live terrain layer is drawn.
 */
public interface RingDrawableSectionView {
    boolean ringworld$hasCompiledSectionsInsideProxyHole(
            RingGeometry geometry, Vec3 cameraPosition, int effectiveChunks,
            double baseProxyOpaqueFromBlocks);
}
