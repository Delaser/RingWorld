package dev.ringworld.client.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.chunk.RingClientChunkMapAccess;
import dev.ringworld.client.chunk.RingClientChunkMaps;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Converts canonical chunk packets back into the nearest logical copy around
 * the client. The server can therefore store chunk 0 once while a traveller
 * at chunk 100 sees it immediately beyond the circumference seam.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPlayNetworkHandlerMixin {
    /**
     * A whole-chart jump cannot rely on the following canonical unload
     * packets: by then their X values are projected around the new player
     * position, so they no longer address chunks stored around the old
     * logical center. Explicitly evict that old client chart first. Small
     * center changes use vanilla's incremental unload/load path unchanged.
     */
    @Inject(method = "handleSetChunkCacheCenter", at = @At("HEAD"))
    private void ringworld$evictPreviousChunkChart(ClientboundSetChunkCacheCenterPacket packet,
                                                    CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        // This injection runs before vanilla's packet-thread guard. NeoForge
        // exposes that ordering because its block-entity model-data teardown
        // rejects work from Netty. The main-thread replay performs the re-key.
        if (!client.isSameThread()) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.player == null || client.level == null) return;

        int nextX = mapChunkX(packet.getX());
        rekeyClientChart(client, nextX, packet.getZ());
    }

    private void rekeyClientChart(Minecraft client, int nextX, int nextZ) {
        if (client.level == null) return;
        RingClientChunkMapAccess access = RingClientChunkMaps.get(client.level.getChunkSource());
        if (access == null) return;

        int viewDistance = client.options.renderDistance().get();
        int overlapDiameter = viewDistance * 2 + 2;
        if (Math.abs(nextX - access.ringworld$centerChunkX()) <= overlapDiameter
                && Math.abs(nextZ - access.ringworld$centerChunkZ()) <= overlapDiameter) return;

        int previousX = access.ringworld$centerChunkX();
        int previousZ = access.ringworld$centerChunkZ();
        access.ringworld$clearAllChunks();
        // Position packets and chunk-centre packets are separate. A large
        // teleport can deliver them in either order, so make the acceptance
        // window authoritative here instead of leaving it on the old chart.
        client.level.getChunkSource().updateViewCenter(nextX, nextZ);
        client.levelRenderer.needsUpdate();
        RingWorldMod.LOGGER.debug("Re-keyed client chunk chart from {},{} to {},{}",
                previousX, previousZ, nextX, nextZ);
    }

    @ModifyVariable(method = "handleAddEntity", at = @At("HEAD"), argsOnly = true)
    private ClientboundAddEntityPacket ringworld$projectEntitySpawn(ClientboundAddEntityPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return packet;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.player == null) return packet;
        double logicalX = geometry.nearestImageX(packet.getX(), client.player.getX());
        if (logicalX == packet.getX()) return packet;
        return new ClientboundAddEntityPacket(packet.getId(), packet.getUUID(),
                logicalX, packet.getY(), packet.getZ(), packet.getXRot(), packet.getYRot(),
                packet.getType(), packet.getData(),
                new Vec3(packet.getXa(), packet.getYa(), packet.getZa()),
                packet.getYHeadRot());
    }

    @Redirect(
            method = "handleTeleportEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundTeleportEntityPacket;getX()D"))
    private double ringworld$projectEntityTeleportX(ClientboundTeleportEntityPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.player == null) return packet.getX();
        return mapX(packet.getX());
    }

    /** Keep authoritative vehicle corrections in the rider's current visual chart. */
    @Redirect(
            method = "handleMoveVehicle",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundMoveVehiclePacket;getX()D"))
    private double ringworld$projectVehicleCorrectionX(ClientboundMoveVehiclePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.player == null) return packet.getX();
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return packet.getX();
        return geometry.nearestImageX(packet.getX(), client.player.getRootVehicle().getX());
    }

    /** Keep the client chunk chart aligned with explicit server teleports. */
    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ClientboundPlayerPositionPacket ringworld$logicalTeleport(ClientboundPlayerPositionPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return packet;
        LocalPlayer player = client.player;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || player == null) return packet;

        Set<RelativeMovement> flags = packet.getRelativeArguments();
        double targetX = flags.contains(RelativeMovement.X) ? player.getX() + packet.getX() : packet.getX();
        double targetZ = flags.contains(RelativeMovement.Z) ? player.getZ() + packet.getZ() : packet.getZ();
        double presentationX = geometry.nearestImageX(targetX, player.getX());

        if (presentationX != targetX) {
            EnumSet<RelativeMovement> projected = flags.isEmpty()
                    ? EnumSet.noneOf(RelativeMovement.class) : EnumSet.copyOf(flags);
            projected.remove(RelativeMovement.X);
            packet = new ClientboundPlayerPositionPacket(presentationX, packet.getY(), packet.getZ(),
                    packet.getYRot(), packet.getXRot(), Set.copyOf(projected), packet.getId());
        }

        rekeyClientChart(client,
                Math.floorDiv((int)Math.floor(presentationX), 16),
                Math.floorDiv((int)Math.floor(targetZ), 16));
        return packet;
    }

    @Redirect(
            method = "handleLevelChunkWithLight",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;getX()I"))
    private int ringworld$mapChunkDataX(ClientboundLevelChunkWithLightPacket packet) {
        return mapChunkX(packet.getX());
    }

    /** Incremental light packets arrive independently of full chunk data. */
    @Redirect(
            method = "handleLightUpdatePacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacket;getX()I"))
    private int ringworld$mapLightUpdateX(ClientboundLightUpdatePacket packet) {
        return mapChunkX(packet.getX());
    }

    /** Keep biome-only refreshes on the same client chart as their chunks. */
    @ModifyVariable(method = "handleChunksBiomes", at = @At("HEAD"), argsOnly = true)
    private ClientboundChunksBiomesPacket ringworld$mapChunkBiomeData(ClientboundChunksBiomesPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return packet;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.player == null) return packet;
        List<ClientboundChunksBiomesPacket.ChunkBiomeData> mapped = packet.chunkBiomeData().stream()
                .map(data -> new ClientboundChunksBiomesPacket.ChunkBiomeData(
                        new ChunkPos(mapChunkX(data.pos().x), data.pos().z), data.buffer()))
                .toList();
        return new ClientboundChunksBiomesPacket(mapped);
    }

    /** Keep ClientChunkManager's acceptance window on the same presentation chart. */
    @Redirect(
            method = "handleSetChunkCacheCenter",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheCenterPacket;getX()I"))
    private int ringworld$mapChunkCenterX(ClientboundSetChunkCacheCenterPacket packet) {
        return mapChunkX(packet.getX());
    }

    @Redirect(
            method = {"handleForgetLevelChunk", "queueLightRemoval"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket;pos()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos ringworld$mapUnloadChunk(ClientboundForgetLevelChunkPacket packet) {
        ChunkPos pos = packet.pos();
        return new ChunkPos(mapChunkX(pos.x), pos.z);
    }

    @Redirect(
            method = "handleChunkBlocksUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;runUpdates(Ljava/util/function/BiConsumer;)V"))
    private void ringworld$mapChunkDelta(ClientboundSectionBlocksUpdatePacket packet,
                                         BiConsumer<BlockPos, BlockState> consumer) {
        packet.runUpdates((pos, state) -> {
            RingGeometry geometry = ClientRingState.geometry();
            Minecraft client = Minecraft.getInstance();
            if (geometry == null || client.player == null) {
                consumer.accept(pos, state);
                return;
            }
            double nearestX = geometry.nearestImageX(pos.getX(), client.player.getX());
            BlockPos mapped = new BlockPos((int) Math.floor(nearestX), pos.getY(), pos.getZ());
            if (!System.getProperty("ringworld.multiplayerTestRole", "").isEmpty()
                    && pos.getX() == 1 && pos.getY() == 119 && pos.getZ() == 0) {
                RingWorldMod.LOGGER.info("[multiplayer:{}] seam block delta {} -> {} state={} playerX={}",
                        System.getProperty("ringworld.multiplayerTestRole"), pos, mapped,
                        state.getBlock(), client.player.getX());
            }
            consumer.accept(mapped, state);
        });
    }

    @Redirect(
            method = "handleBlockUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld$mapBlockUpdate(ClientboundBlockUpdatePacket packet) {
        BlockPos mapped = mapBlockPos(packet.getPos());
        if (!System.getProperty("ringworld.multiplayerTestRole", "").isEmpty()
                && packet.getPos().equals(new BlockPos(1, 119, 0))) {
            Minecraft client = Minecraft.getInstance();
            RingWorldMod.LOGGER.info("[multiplayer:{}] seam block update {} -> {} state={} playerX={}",
                    System.getProperty("ringworld.multiplayerTestRole"), packet.getPos(), mapped,
                    packet.getBlockState().getBlock(), client.player == null ? Double.NaN : client.player.getX());
        }
        return mapped;
    }

    @Redirect(
            method = "handleBlockEntityData",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld$mapBlockEntityUpdate(ClientboundBlockEntityDataPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    /** The sign screen immediately resolves this position back to its block entity. */
    @Redirect(
            method = "handleOpenSignEditor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundOpenSignEditorPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld$mapOpenSignEditor(ClientboundOpenSignEditorPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @Redirect(
            method = "handleBlockDestruction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundBlockDestructionPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld$mapBlockBreakingProgress(ClientboundBlockDestructionPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @Redirect(
            method = "handleBlockEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundBlockEventPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld$mapBlockEvent(ClientboundBlockEventPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @Redirect(
            method = "handleLevelEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundLevelEventPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld$mapWorldEvent(ClientboundLevelEventPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @ModifyVariable(method = "handleParticleEvent", at = @At("HEAD"), argsOnly = true)
    private ClientboundLevelParticlesPacket ringworld$mapParticle(ClientboundLevelParticlesPacket packet) {
        if (!Minecraft.getInstance().isSameThread()) return packet;
        double x = mapX(packet.getX());
        if (x == packet.getX()) return packet;
        return new ClientboundLevelParticlesPacket(packet.getParticle(), packet.isOverrideLimiter(),
                x, packet.getY(), packet.getZ(), packet.getXDist(), packet.getYDist(), packet.getZDist(),
                packet.getMaxSpeed(), packet.getCount());
    }

    @ModifyVariable(method = "handleExplosion", at = @At("HEAD"), argsOnly = true)
    private ClientboundExplodePacket ringworld$mapExplosion(ClientboundExplodePacket packet) {
        if (!Minecraft.getInstance().isSameThread()) return packet;
        double x = mapX(packet.getX());
        if (x == packet.getX()) return packet;
        return new ClientboundExplodePacket(x, packet.getY(), packet.getZ(), packet.getPower(),
                packet.getToBlow(),
                new Vec3(packet.getKnockbackX(), packet.getKnockbackY(), packet.getKnockbackZ()),
                packet.getBlockInteraction(), packet.getSmallExplosionParticles(),
                packet.getLargeExplosionParticles(), packet.getExplosionSound());
    }

    @ModifyVariable(method = "handleSoundEvent", at = @At("HEAD"), argsOnly = true)
    private ClientboundSoundPacket ringworld$mapSound(ClientboundSoundPacket packet) {
        if (!Minecraft.getInstance().isSameThread()) return packet;
        double x = mapX(packet.getX());
        if (x == packet.getX()) return packet;
        return new ClientboundSoundPacket(packet.getSound(), packet.getSource(), x, packet.getY(), packet.getZ(),
                packet.getVolume(), packet.getPitch(), packet.getSeed());
    }

    /** Preserve the apparent source direction for position-only damage sources. */
    @ModifyVariable(method = "handleDamageEvent", at = @At("HEAD"), argsOnly = true)
    private ClientboundDamageEventPacket ringworld$mapDamageSource(ClientboundDamageEventPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return packet;
        Optional<Vec3> source = packet.sourcePosition();
        if (source.isEmpty()) return packet;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.level == null || client.player == null) return packet;
        Vec3 position = source.get();
        var damagedEntity = client.level.getEntity(packet.entityId());
        double referenceX = damagedEntity == null ? client.player.getX() : damagedEntity.getX();
        double logicalX = geometry.nearestImageX(position.x, referenceX);
        if (logicalX == position.x) return packet;
        return new ClientboundDamageEventPacket(packet.entityId(), packet.sourceType(),
                packet.sourceCauseId(), packet.sourceDirectId(),
                Optional.of(new Vec3(logicalX, position.y, position.z)));
    }

    /** `/look` coordinate targets and entity anchors share this resolved position path. */
    @Redirect(
            method = "handleLookAt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerLookAtPacket;getPosition(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$mapLookTarget(ClientboundPlayerLookAtPacket packet,
                                         net.minecraft.world.level.Level level) {
        Vec3 position = packet.getPosition(level);
        double logicalX = mapX(position.x);
        return logicalX == position.x ? position : new Vec3(logicalX, position.y, position.z);
    }

    private BlockPos mapBlockPos(BlockPos canonicalPos) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        if (geometry == null || client.player == null) return canonicalPos;
        double nearestX = geometry.nearestImageX(canonicalPos.getX(), client.player.getX());
        return new BlockPos((int) Math.floor(nearestX), canonicalPos.getY(), canonicalPos.getZ());
    }

    private double mapX(double canonicalX) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        if (geometry == null || client.player == null) return canonicalX;
        return geometry.nearestImageX(canonicalX, client.player.getX());
    }

    private int mapChunkX(int canonicalChunkX) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        if (geometry == null || client.player == null) return canonicalChunkX;
        return RingChunkCoordinates.nearestImageChunkX(canonicalChunkX,
                Math.floorDiv((int) Math.floor(client.player.getX()), 16), geometry);
    }
}
