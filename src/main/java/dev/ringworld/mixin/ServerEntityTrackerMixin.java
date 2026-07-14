package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Treats entities on opposite canonical sides of the seam as neighbours. */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
abstract class ServerEntityTrackerMixin {
    @Redirect(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;subtract(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d ringworld$periodicTrackingDelta(Vec3d playerPosition, Vec3d entityPosition,
                                                  ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return playerPosition.subtract(entityPosition);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return new Vec3d(geometry.shortestCircumferenceDelta(entityPosition.x, playerPosition.x),
                playerPosition.y - entityPosition.y,
                playerPosition.z - entityPosition.z);
    }
}
