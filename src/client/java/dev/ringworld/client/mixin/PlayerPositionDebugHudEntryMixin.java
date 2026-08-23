package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPosition;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainNoiseMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Presents the server's single canonical ring coordinate plane in 1.21.1's monolithic F3 overlay. */
@Mixin(DebugScreenOverlay.class)
abstract class PlayerPositionDebugHudEntryMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void ringworld$renderCanonicalPosition(CallbackInfoReturnable<List<String>> cir) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        Minecraft client = Minecraft.getInstance();
        Entity entity = client.getCameraEntity();
        if (entity == null || client.level == null) return;

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
        ServerLevel serverLevel = client.getSingleplayerServer() == null
                ? null : client.getSingleplayerServer().getLevel(client.level.dimension());
        int forcedChunkCount = serverLevel == null ? 0 : serverLevel.getForcedChunks().size();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        String atlasStatus = atlas == null ? "not received"
                : String.format(Locale.ROOT, "%d/%d cells (%.1f%%), step %d",
                atlas.presentCount(), atlas.cellCount(), atlas.completion() * 100.0, atlas.sampleStep());

        List<String> ringLines = List.of(
                String.format(Locale.ROOT, "Ring XYZ: %.3f / %.5f / %.3f",
                        canonicalX, entity.getY(), entity.getZ()),
                String.format(Locale.ROOT, "Ring Block: %d %d %d",
                        blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                String.format(Locale.ROOT,
                        "Ring Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                        chunkPos.x, SectionPos.blockToSectionCoord(blockPos.getY()), chunkPos.z,
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
                client.level.dimension().location() + " FC: " + forcedChunkCount
        );

        // 1.21.1 emits these fields as part of one mutable list rather than
        // independent debug entries. Replace the four vanilla coordinate
        // rows in-place and append RingWorld's additional diagnostics at the
        // same location so unrelated F3 information stays untouched.
        List<String> lines = new ArrayList<>(cir.getReturnValue());
        int insertion = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith("XYZ: ")) {
                insertion = index;
                lines.set(index, ringLines.get(0));
            } else if (line.startsWith("Block: ")) {
                lines.set(index, ringLines.get(1));
            } else if (line.startsWith("Chunk: ")) {
                lines.set(index, ringLines.get(2));
            } else if (line.startsWith("Facing: ")) {
                lines.set(index, ringLines.get(3));
                insertion = index + 1;
            }
        }
        if (insertion < 0) insertion = lines.size();
        lines.addAll(insertion, ringLines.subList(4, ringLines.size()));
        cir.setReturnValue(lines);
    }
}
