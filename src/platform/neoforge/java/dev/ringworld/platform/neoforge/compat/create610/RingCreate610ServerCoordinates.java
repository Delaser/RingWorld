package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.server.RingBlockEntityLoadContext;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingBlockCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server ownership and transient relationship coordinates for the adapter. */
public final class RingCreate610ServerCoordinates {
    private RingCreate610ServerCoordinates() { }

    public static BlockPos canonicalLevelPosition(Level level, BlockPos position) {
        RingGeometry geometry = attachedOverworldGeometry(level);
        return geometry == null
                ? position
                : RingBlockCoordinates.canonicalBlockPos(position, geometry);
    }

    public static BlockPos nearestRelationshipPosition(Level level, BlockPos target,
                                                       double referenceChartX) {
        RingGeometry geometry = attachedOverworldGeometry(level);
        return geometry == null
                ? target
                : RingBlockCoordinates.nearestImageBlockPos(target, referenceChartX, geometry);
    }

    /**
     * Repairs a server-owned controller coordinate. During deserialization the
     * thread-local load geometry is authoritative before a level is attached;
     * otherwise only immutable settings from an attached Overworld are used.
     * With neither source available, repair is deliberately deferred.
     */
    public static BlockPos canonicalController(BlockEntity owner, BlockPos controller) {
        RingGeometry geometry = RingBlockEntityLoadContext.activeGeometryOrNull();
        if (geometry == null) geometry = attachedOverworldGeometry(owner.getLevel());
        return geometry == null
                ? controller
                : RingBlockCoordinates.canonicalBlockPos(controller, geometry);
    }

    private static RingGeometry attachedOverworldGeometry(Level level) {
        if (!(level instanceof ServerLevel serverLevel)
                || serverLevel.dimension() != Level.OVERWORLD) {
            return null;
        }
        return RingWorldServer.geometryFor(serverLevel);
    }
}
