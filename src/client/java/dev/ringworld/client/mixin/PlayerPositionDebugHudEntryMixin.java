package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPosition;
import dev.ringworld.world.RingTerrainAtlas;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.debug.DebugHudLines;
import net.minecraft.client.gui.hud.debug.PlayerPositionDebugHudEntry;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Presents the server's single canonical ring coordinate plane. */
@Mixin(PlayerPositionDebugHudEntry.class)
abstract class PlayerPositionDebugHudEntryMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ringworld$renderCanonicalPosition(DebugHudLines lines, @Nullable World world,
                                                   @Nullable WorldChunk clientChunk,
                                                   @Nullable WorldChunk chunk,
                                                   CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = client.getCameraEntity();
        if (entity == null) return;

        RingPosition ringPosition = RingPosition.fromPresentationX(entity.getX(), geometry);
        double canonicalX = ringPosition.canonicalX() == 0.0 ? 0.0 : ringPosition.canonicalX();
        BlockPos rawBlockPos = entity.getBlockPos();
        BlockPos blockPos = new BlockPos(geometry.wrapBlockX(rawBlockPos.getX()),
                rawBlockPos.getY(), rawBlockPos.getZ());
        ChunkPos chunkPos = new ChunkPos(blockPos);
        Direction direction = entity.getHorizontalFacing();
        String directionDescription = switch (direction) {
            case NORTH -> "Towards negative Z";
            case SOUTH -> "Towards positive Z";
            case WEST -> "Towards decreasing Ring X";
            case EAST -> "Towards increasing Ring X";
            default -> "Invalid";
        };
        LongSet forcedChunks = world instanceof ServerWorld serverWorld
                ? serverWorld.getForcedChunks() : LongSets.EMPTY_SET;
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        String atlasStatus = atlas == null ? "not received"
                : String.format(Locale.ROOT, "%d/%d cells (%.1f%%), step %d",
                atlas.presentCount(), atlas.cellCount(), atlas.completion() * 100.0, atlas.sampleStep());

        lines.addLinesToSection(PlayerPositionDebugHudEntry.SECTION_ID, List.of(
                String.format(Locale.ROOT, "Ring XYZ: %.3f / %.5f / %.3f",
                        canonicalX, entity.getY(), entity.getZ()),
                String.format(Locale.ROOT, "Ring Block: %d %d %d",
                        blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                String.format(Locale.ROOT,
                        "Ring Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                        chunkPos.x, ChunkSectionPos.getSectionCoord(blockPos.getY()), chunkPos.z,
                        chunkPos.getRegionRelativeX(), chunkPos.getRegionRelativeZ(),
                        chunkPos.getRegionX(), chunkPos.getRegionZ()),
                String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)",
                        direction, directionDescription,
                        MathHelper.wrapDegrees(entity.getYaw()),
                        MathHelper.wrapDegrees(entity.getPitch())),
                String.format(Locale.ROOT, "Loop: X 0-%d, %d blocks / %d chunks",
                        geometry.circumferenceBlocks() - 1,
                        geometry.circumferenceBlocks(),
                        geometry.circumferenceBlocks() / 16),
                "Ring Atlas: " + atlasStatus,
                client.world.getRegistryKey().getValue() + " FC: " + forcedChunks.size()
        ));
        ci.cancel();
    }
}
