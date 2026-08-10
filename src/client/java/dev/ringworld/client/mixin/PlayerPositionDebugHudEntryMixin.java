package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPosition;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainNoiseMapping;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Presents the server's single canonical ring coordinate plane. */
@Mixin(DebugEntryPosition.class)
abstract class PlayerPositionDebugHudEntryMixin {
    @Inject(method = "display", at = @At("HEAD"), cancellable = true)
    private void ringworld$renderCanonicalPosition(DebugScreenDisplayer lines, @Nullable Level world,
                                                   @Nullable LevelChunk clientChunk,
                                                   @Nullable LevelChunk chunk,
                                                   CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        Minecraft client = Minecraft.getInstance();
        Entity entity = client.getCameraEntity();
        if (entity == null) return;

        RingPosition ringPosition = RingPosition.fromPresentationX(entity.getX(), geometry);
        double canonicalX = ringPosition.canonicalX() == 0.0 ? 0.0 : ringPosition.canonicalX();
        BlockPos rawBlockPos = entity.blockPosition();
        BlockPos blockPos = new BlockPos(geometry.wrapBlockX(rawBlockPos.getX()),
                rawBlockPos.getY(), rawBlockPos.getZ());
        ChunkPos chunkPos = new ChunkPos(
                SectionPos.blockToSectionCoord(blockPos.getX()),
                SectionPos.blockToSectionCoord(blockPos.getZ()));
        Direction direction = entity.getDirection();
        String directionDescription = switch (direction) {
            case NORTH -> "Towards negative Z";
            case SOUTH -> "Towards positive Z";
            case WEST -> "Towards decreasing Ring X";
            case EAST -> "Towards increasing Ring X";
            default -> "Invalid";
        };
        LongSet forcedChunks = world instanceof ServerLevel serverWorld
                ? serverWorld.getForceLoadedChunks() : LongSets.EMPTY_SET;
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        String atlasStatus = atlas == null ? "not received"
                : String.format(Locale.ROOT, "%d/%d cells (%.1f%%), step %d",
                atlas.presentCount(), atlas.cellCount(), atlas.completion() * 100.0, atlas.sampleStep());

        lines.addToGroup(DebugEntryPosition.GROUP, List.of(
                String.format(Locale.ROOT, "Ring XYZ: %.3f / %.5f / %.3f",
                        canonicalX, entity.getY(), entity.getZ()),
                String.format(Locale.ROOT, "Ring Block: %d %d %d",
                        blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                String.format(Locale.ROOT,
                        "Ring Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                        chunkPos.x(), SectionPos.blockToSectionCoord(blockPos.getY()), chunkPos.z(),
                        chunkPos.getRegionLocalX(), chunkPos.getRegionLocalZ(),
                        chunkPos.getRegionX(), chunkPos.getRegionZ()),
                String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)",
                        direction, directionDescription,
                        Mth.wrapDegrees(entity.getYRot()),
                        Mth.wrapDegrees(entity.getXRot())),
                String.format(Locale.ROOT, "Loop: X 0-%d, %d blocks / %d chunks",
                        geometry.circumferenceBlocks() - 1,
                        geometry.circumferenceBlocks(),
                        geometry.circumferenceChunks()),
                String.format(Locale.ROOT, "Worldgen: %s (%d)",
                        RingTerrainNoiseMapping.diagnosticName(ClientRingState.terrainNoiseMapping()),
                        ClientRingState.terrainNoiseMapping()),
                "Ring Atlas: " + atlasStatus,
                client.level.dimension().identifier() + " FC: " + forcedChunks.size()
        ));
        ci.cancel();
    }
}
