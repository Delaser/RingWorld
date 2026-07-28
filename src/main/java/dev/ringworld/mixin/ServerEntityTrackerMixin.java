package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Treats entities on opposite canonical sides of the seam as neighbours. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class ServerEntityTrackerMixin {
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
}
