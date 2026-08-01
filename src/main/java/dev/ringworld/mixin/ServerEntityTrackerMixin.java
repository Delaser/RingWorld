package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingEntityTracking;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldConfig;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Treats entities on opposite canonical sides of the seam as neighbours. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class ServerEntityTrackerMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Redirect(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ringworld$periodicTrackingDelta(Vec3 playerPosition, Vec3 entityPosition,
                                                  ServerPlayer player) {
        ServerLevel world = player.level();
        if (world.dimension() != Level.OVERWORLD) return playerPosition.subtract(entityPosition);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return new Vec3(geometry.shortestCircumferenceDelta(entityPosition.x, playerPosition.x),
                playerPosition.y - entityPosition.y,
                playerPosition.z - entityPosition.z);
    }

    /**
     * A canonical high-edge to zero fold changes the entity section. Minecraft
     * 26.1 immediately re-evaluates the pairing, but its chunk sender can
     * transiently report the destination as pending. Removing the existing
     * pairing in that one tick loses a now-stationary vehicle permanently
     * because no later section change retries {@code updatePlayer}.
     *
     * Initial pairing still requires vanilla chunk readiness. Existing
     * pairings survive only while the canonical destination remains inside
     * the same configured periodic watch window; tracking range was already
     * checked immediately before this call.
     */
    @Redirect(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean ringworld$preservePairingDuringCanonicalFold(ChunkMap chunkMap,
                                                                 ServerPlayer player,
                                                                 int chunkX,
                                                                 int chunkZ) {
        boolean vanillaChunkTracked = chunkMap.isChunkTracked(player, chunkX, chunkZ);
        ServerLevel world = player.level();
        if (vanillaChunkTracked || world.dimension() != Level.OVERWORLD) {
            return vanillaChunkTracked;
        }

        RingGeometry geometry = RingWorldServer.geometryFor(world);
        int canonicalChunkX = RingChunkCoordinates.wrapChunkX(chunkX, geometry);
        boolean canonicalChunkWatched =
                player.getChunkTrackingView().contains(canonicalChunkX, chunkZ);
        boolean remainPaired = RingEntityTracking.shouldRemainPaired(
                false, seenBy.contains(player.connection), canonicalChunkWatched);
        if (remainPaired && RingWorldConfig.load().testMode()) {
            RingWorldMod.LOGGER.info(
                    "[multiplayer] preserved pending entity pairing id={} type={} canonicalChunk={},{} player={}",
                    entity.getId(), entity.getType(), canonicalChunkX, chunkZ,
                    player.getName().getString());
        }
        return remainPaired;
    }
}
