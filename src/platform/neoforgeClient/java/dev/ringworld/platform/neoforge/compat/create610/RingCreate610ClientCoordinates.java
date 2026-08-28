package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingBlockCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client-only transient presentation coordinates for the exact Create adapter. */
public final class RingCreate610ClientCoordinates {
    private RingCreate610ClientCoordinates() { }

    public static BlockPos nearestPreviewPosition(Level level, BlockPos canonical,
                                                   Player reference) {
        RingGeometry geometry = owningOverworldGeometry(level);
        return geometry == null || canonical == null || reference == null
                ? canonical
                : RingBlockCoordinates.nearestImageBlockPos(
                        canonical, reference.getX(), geometry);
    }

    public static BlockPos nearestController(BlockEntity owner, BlockPos canonical) {
        RingGeometry geometry = owningOverworldGeometry(owner == null ? null : owner.getLevel());
        return geometry == null || owner == null || canonical == null
                ? canonical
                : RingBlockCoordinates.nearestImageBlockPos(
                        canonical, owner.getBlockPos().getX(), geometry);
    }

    /** Returns null only when the owning client geometry is not available yet. */
    public static BlockPos nearestControllerOrNull(BlockEntity owner, BlockPos canonical) {
        RingGeometry geometry = owningOverworldGeometry(owner == null ? null : owner.getLevel());
        return geometry == null || owner == null || canonical == null
                ? null
                : RingBlockCoordinates.nearestImageBlockPos(
                        canonical, owner.getBlockPos().getX(), geometry);
    }

    public static BlockPos canonicalController(BlockEntity owner, BlockPos presentation) {
        RingGeometry geometry = owningOverworldGeometry(owner == null ? null : owner.getLevel());
        return geometry == null || presentation == null
                ? presentation
                : RingBlockCoordinates.canonicalBlockPos(presentation, geometry);
    }

    public static boolean isOwningClientLevel(BlockEntity owner) {
        if (owner == null) return false;
        Level level = owner.getLevel();
        Minecraft client = Minecraft.getInstance();
        return level != null && level.isClientSide
                && level == client.level && level.dimension() == Level.OVERWORLD;
    }

    private static RingGeometry owningOverworldGeometry(Level level) {
        Minecraft client = Minecraft.getInstance();
        if (level == null || level != client.level || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return ClientRingState.geometry();
    }
}
