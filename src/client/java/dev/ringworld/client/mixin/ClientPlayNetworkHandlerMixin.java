package dev.ringworld.client.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.chunk.RingClientChunkMapAccess;
import dev.ringworld.client.chunk.RingClientChunkMaps;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkBiomeDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Converts canonical chunk packets back into the nearest logical copy around
 * the client. The server can therefore store chunk 0 once while a traveller
 * at chunk 100 sees it immediately beyond the circumference seam.
 */
@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {
    /**
     * A whole-chart jump cannot rely on the following canonical unload
     * packets: by then their X values are projected around the new player
     * position, so they no longer address chunks stored around the old
     * logical center. Explicitly evict that old client chart first. Small
     * center changes use vanilla's incremental unload/load path unchanged.
     */
    @Inject(method = "onChunkRenderDistanceCenter", at = @At("HEAD"))
    private void ringworld$evictPreviousChunkChart(ChunkRenderDistanceCenterS2CPacket packet,
                                                    CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.player == null || client.world == null) return;

        int nextX = mapChunkX(packet.getChunkX());
        rekeyClientChart(client, nextX, packet.getChunkZ());
    }

    private void rekeyClientChart(MinecraftClient client, int nextX, int nextZ) {
        if (client.world == null) return;
        RingClientChunkMapAccess access = RingClientChunkMaps.get(client.world.getChunkManager());
        if (access == null) return;

        int viewDistance = client.options.getViewDistance().getValue();
        int overlapDiameter = viewDistance * 2 + 2;
        if (Math.abs(nextX - access.ringworld$centerChunkX()) <= overlapDiameter
                && Math.abs(nextZ - access.ringworld$centerChunkZ()) <= overlapDiameter) return;

        int previousX = access.ringworld$centerChunkX();
        int previousZ = access.ringworld$centerChunkZ();
        access.ringworld$clearAllChunks();
        // Position packets and chunk-centre packets are separate. A large
        // teleport can deliver them in either order, so make the acceptance
        // window authoritative here instead of leaving it on the old chart.
        client.world.getChunkManager().setChunkMapCenter(nextX, nextZ);
        client.worldRenderer.scheduleTerrainUpdate();
        RingWorldMod.LOGGER.debug("Re-keyed client chunk chart from {},{} to {},{}",
                previousX, previousZ, nextX, nextZ);
    }

    @ModifyVariable(method = "onEntitySpawn", at = @At("HEAD"), argsOnly = true)
    private EntitySpawnS2CPacket ringworld$projectEntitySpawn(EntitySpawnS2CPacket packet) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.player == null) return packet;
        double logicalX = geometry.nearestImageX(packet.getX(), client.player.getX());
        if (logicalX == packet.getX()) return packet;
        return new EntitySpawnS2CPacket(packet.getEntityId(), packet.getUuid(),
                logicalX, packet.getY(), packet.getZ(), packet.getPitch(), packet.getYaw(),
                packet.getEntityType(), packet.getEntityData(), packet.getVelocity(), packet.getHeadYaw());
    }

    @ModifyVariable(method = "onEntityPositionSync", at = @At("HEAD"), argsOnly = true)
    private EntityPositionSyncS2CPacket ringworld$projectEntitySync(EntityPositionSyncS2CPacket packet) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.world == null || client.player == null) return packet;
        EntityPosition values = packet.values();
        double logicalX = geometry.nearestImageX(values.position().x, client.player.getX());
        if (logicalX == values.position().x) return packet;
        EntityPosition logical = new EntityPosition(
                new Vec3d(logicalX, values.position().y, values.position().z),
                values.deltaMovement(), values.yaw(), values.pitch());
        return new EntityPositionSyncS2CPacket(packet.id(), logical, packet.onGround());
    }

    @ModifyVariable(method = "onEntityPosition", at = @At("HEAD"), argsOnly = true)
    private EntityPositionS2CPacket ringworld$projectEntityTeleport(EntityPositionS2CPacket packet) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.world == null || client.player == null
                || packet.relatives().contains(PositionFlag.X)) return packet;
        EntityPosition change = packet.change();
        double logicalX = geometry.nearestImageX(change.position().x, client.player.getX());
        if (logicalX == change.position().x) return packet;
        EntityPosition logical = new EntityPosition(
                new Vec3d(logicalX, change.position().y, change.position().z),
                change.deltaMovement(), change.yaw(), change.pitch());
        return new EntityPositionS2CPacket(packet.entityId(), logical, packet.relatives(), packet.onGround());
    }

    /** Keep authoritative vehicle corrections in the rider's current visual chart. */
    @ModifyVariable(method = "onVehicleMove", at = @At("HEAD"), argsOnly = true)
    private VehicleMoveS2CPacket ringworld$projectVehicleCorrection(VehicleMoveS2CPacket packet) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.player == null) return packet;
        Vec3d position = packet.position();
        double logicalX = geometry.nearestImageX(position.x, client.player.getRootVehicle().getX());
        if (logicalX == position.x) return packet;
        return new VehicleMoveS2CPacket(
                new Vec3d(logicalX, position.y, position.z),
                packet.yaw(), packet.pitch());
    }

    /** Keep the client chunk chart aligned with explicit server teleports. */
    @ModifyVariable(method = "onPlayerPositionLook", at = @At("HEAD"), argsOnly = true)
    private PlayerPositionLookS2CPacket ringworld$logicalTeleport(PlayerPositionLookS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || player == null) return packet;

        // The server target is canonical, but the client must stay on the
        // nearest periodic image. Applying a seam-adjacent command target as
        // raw canonical X can make a two-block teleport look C blocks long,
        // clear the client chart, and discard chunks that the server still
        // considers continuously watched.
        EntityPosition current = EntityPosition.fromEntity(player);
        EntityPosition target = EntityPosition.apply(current, packet.change(), packet.relatives());
        double presentationX = geometry.nearestImageX(target.position().x, player.getX());
        if (presentationX != target.position().x) {
            EnumSet<PositionFlag> projectedRelatives = packet.relatives().isEmpty()
                    ? EnumSet.noneOf(PositionFlag.class)
                    : EnumSet.copyOf(packet.relatives());
            projectedRelatives.remove(PositionFlag.X);
            EntityPosition change = packet.change();
            packet = new PlayerPositionLookS2CPacket(
                    packet.teleportId(),
                    new EntityPosition(
                            new Vec3d(presentationX, change.position().y, change.position().z),
                            change.deltaMovement(), change.yaw(), change.pitch()),
                    Set.copyOf(projectedRelatives));
            target = EntityPosition.apply(current, packet.change(), packet.relatives());
        }
        rekeyClientChart(client,
                Math.floorDiv((int) Math.floor(target.position().x), 16),
                Math.floorDiv((int) Math.floor(target.position().z), 16));
        // Natural seam folds do not use this packet. Explicit commands,
        // portals and respawns remain server-authoritative while using the
        // equivalent presentation image nearest the current camera.
        return packet;
    }

    @Redirect(
            method = "onChunkData",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;getChunkX()I"))
    private int ringworld$mapChunkDataX(ChunkDataS2CPacket packet) {
        return mapChunkX(packet.getChunkX());
    }

    /** Incremental light packets arrive independently of full chunk data. */
    @Redirect(
            method = "onLightUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/LightUpdateS2CPacket;getChunkX()I"))
    private int ringworld$mapLightUpdateX(LightUpdateS2CPacket packet) {
        return mapChunkX(packet.getChunkX());
    }

    /** Keep biome-only refreshes on the same client chart as their chunks. */
    @ModifyVariable(method = "onChunkBiomeData", at = @At("HEAD"), argsOnly = true)
    private ChunkBiomeDataS2CPacket ringworld$mapChunkBiomeData(ChunkBiomeDataS2CPacket packet) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.player == null) return packet;
        List<ChunkBiomeDataS2CPacket.Serialized> mapped = packet.chunkBiomeData().stream()
                .map(data -> new ChunkBiomeDataS2CPacket.Serialized(
                        new ChunkPos(mapChunkX(data.pos().x), data.pos().z), data.buffer()))
                .toList();
        return new ChunkBiomeDataS2CPacket(mapped);
    }

    /** Keep ClientChunkManager's acceptance window on the same presentation chart. */
    @Redirect(
            method = "onChunkRenderDistanceCenter",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/ChunkRenderDistanceCenterS2CPacket;getChunkX()I"))
    private int ringworld$mapChunkCenterX(ChunkRenderDistanceCenterS2CPacket packet) {
        return mapChunkX(packet.getChunkX());
    }

    @Redirect(
            method = {"onUnloadChunk", "unloadChunk"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/UnloadChunkS2CPacket;pos()Lnet/minecraft/util/math/ChunkPos;"))
    private ChunkPos ringworld$mapUnloadChunk(UnloadChunkS2CPacket packet) {
        ChunkPos pos = packet.pos();
        return new ChunkPos(mapChunkX(pos.x), pos.z);
    }

    @Redirect(
            method = "onChunkDeltaUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/ChunkDeltaUpdateS2CPacket;visitUpdates(Ljava/util/function/BiConsumer;)V"))
    private void ringworld$mapChunkDelta(ChunkDeltaUpdateS2CPacket packet,
                                         BiConsumer<BlockPos, BlockState> consumer) {
        packet.visitUpdates((pos, state) -> {
            RingGeometry geometry = ClientRingState.geometry();
            MinecraftClient client = MinecraftClient.getInstance();
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
            method = "onBlockUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/BlockUpdateS2CPacket;getPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos ringworld$mapBlockUpdate(BlockUpdateS2CPacket packet) {
        BlockPos mapped = mapBlockPos(packet.getPos());
        if (!System.getProperty("ringworld.multiplayerTestRole", "").isEmpty()
                && packet.getPos().equals(new BlockPos(1, 119, 0))) {
            MinecraftClient client = MinecraftClient.getInstance();
            RingWorldMod.LOGGER.info("[multiplayer:{}] seam block update {} -> {} state={} playerX={}",
                    System.getProperty("ringworld.multiplayerTestRole"), packet.getPos(), mapped,
                    packet.getState().getBlock(), client.player == null ? Double.NaN : client.player.getX());
        }
        return mapped;
    }

    @Redirect(
            method = "onBlockEntityUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/BlockEntityUpdateS2CPacket;getPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos ringworld$mapBlockEntityUpdate(BlockEntityUpdateS2CPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @Redirect(
            method = "onBlockBreakingProgress",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/BlockBreakingProgressS2CPacket;getPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos ringworld$mapBlockBreakingProgress(BlockBreakingProgressS2CPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @Redirect(
            method = "onBlockEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/s2c/play/BlockEventS2CPacket;getPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos ringworld$mapBlockEvent(BlockEventS2CPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @Redirect(
            method = "onWorldEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/s2c/play/WorldEventS2CPacket;getPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos ringworld$mapWorldEvent(WorldEventS2CPacket packet) {
        return mapBlockPos(packet.getPos());
    }

    @ModifyVariable(method = "onParticle", at = @At("HEAD"), argsOnly = true)
    private ParticleS2CPacket ringworld$mapParticle(ParticleS2CPacket packet) {
        double x = mapX(packet.getX());
        if (x == packet.getX()) return packet;
        return new ParticleS2CPacket(packet.getParameters(), packet.shouldForceSpawn(), packet.isImportant(),
                x, packet.getY(), packet.getZ(), packet.getOffsetX(), packet.getOffsetY(), packet.getOffsetZ(),
                packet.getSpeed(), packet.getCount());
    }

    @ModifyVariable(method = "onExplosion", at = @At("HEAD"), argsOnly = true)
    private ExplosionS2CPacket ringworld$mapExplosion(ExplosionS2CPacket packet) {
        double x = mapX(packet.center().x);
        if (x == packet.center().x) return packet;
        return new ExplosionS2CPacket(new Vec3d(x, packet.center().y, packet.center().z),
                packet.radius(), packet.blockCount(), packet.playerKnockback(), packet.explosionParticle(),
                packet.explosionSound(), packet.blockParticles());
    }

    @ModifyVariable(method = "onPlaySound", at = @At("HEAD"), argsOnly = true)
    private PlaySoundS2CPacket ringworld$mapSound(PlaySoundS2CPacket packet) {
        double x = mapX(packet.getX());
        if (x == packet.getX()) return packet;
        return new PlaySoundS2CPacket(packet.getSound(), packet.getCategory(), x, packet.getY(), packet.getZ(),
                packet.getVolume(), packet.getPitch(), packet.getSeed());
    }

    private BlockPos mapBlockPos(BlockPos canonicalPos) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.player == null) return canonicalPos;
        double nearestX = geometry.nearestImageX(canonicalPos.getX(), client.player.getX());
        return new BlockPos((int) Math.floor(nearestX), canonicalPos.getY(), canonicalPos.getZ());
    }

    private double mapX(double canonicalX) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.player == null) return canonicalX;
        return geometry.nearestImageX(canonicalX, client.player.getX());
    }

    private int mapChunkX(int canonicalChunkX) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.player == null) return canonicalChunkX;
        return RingChunkCoordinates.nearestImageChunkX(canonicalChunkX,
                Math.floorDiv((int) Math.floor(client.player.getX()), 16), geometry);
    }
}
