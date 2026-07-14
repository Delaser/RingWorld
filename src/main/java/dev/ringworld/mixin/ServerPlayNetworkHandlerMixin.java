package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.server.RingWorldMultiplayerTest;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accepts a client's continuous presentation coordinate while keeping the
 * authoritative server entity in the single canonical circumference plane.
 * The fold happens after vanilla validates the small local step and does not
 * send a corrective teleport back to the client.
 */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private double lastTickX;
    @Shadow private double updatedX;
    @Shadow private double lastTickRiddenX;
    @Shadow private double updatedRiddenX;

    @ModifyVariable(
            method = "onPlayerMove",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;canInteractWithGame()Z"),
            argsOnly = true)
    private PlayerMoveC2SPacket ringworld$projectPlayerMovement(PlayerMoveC2SPacket packet) {
        ServerWorld world = player.getEntityWorld();
        if (!packet.changesPosition() || world.getRegistryKey() != World.OVERWORLD) return packet;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double presentationX = packet.getX(player.getX());
        double nearestX = geometry.nearestImageX(presentationX, player.getX());
        RingWorldMultiplayerTest.recordPlayerMovementPacket(player, nearestX, geometry);
        double canonicalTargetX = geometry.wrapX(nearestX);

        // Validate a seam crossing entirely within the destination chart.
        // Letting vanilla move the bounding box from C-epsilon to C makes its
        // collision reconciliation inspect a box spanning two periodic block
        // charts, which can reject the otherwise ordinary final step. Shift
        // the source pose and anti-cheat baselines first, then give vanilla a
        // local epsilon-sized move ending directly in canonical storage.
        if (canonicalTargetX != nearestX) {
            double localSourceX = geometry.nearestImageX(player.getX(), canonicalTargetX);
            double sourceShift = localSourceX - player.getX();
            if (sourceShift != 0.0) {
                player.setPosition(localSourceX, player.getY(), player.getZ());
                lastTickX += sourceShift;
                updatedX += sourceShift;
                RingWorldServer.recordPlayerCanonicalWrap(player);
            }
        }

        if (canonicalTargetX == presentationX) return packet;
        return new PlayerMoveC2SPacket.Full(canonicalTargetX,
                packet.getY(player.getY()), packet.getZ(player.getZ()),
                packet.getYaw(player.getYaw()), packet.getPitch(player.getPitch()),
                packet.isOnGround(), packet.horizontalCollision());
    }

    @Inject(method = "onPlayerMove", at = @At("TAIL"))
    private void ringworld$foldPlayerAfterMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        ServerWorld world = player.getEntityWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return;
        double shift = RingWorldServer.canonicalizeEntityPosition(player, RingWorldServer.geometryFor(world));
        if (shift == 0.0) return;
        RingWorldServer.recordPlayerCanonicalWrap(player);
        // Vanilla's anti-cheat baselines must move to the same coordinate
        // chart or the next perfectly ordinary packet looks C blocks long.
        lastTickX += shift;
        updatedX += shift;
    }

    @ModifyVariable(method = "onVehicleMove", at = @At("HEAD"), argsOnly = true)
    private VehicleMoveC2SPacket ringworld$projectVehicleMovement(VehicleMoveC2SPacket packet) {
        ServerWorld world = player.getEntityWorld();
        Entity vehicle = player.getRootVehicle();
        if (world.getRegistryKey() != World.OVERWORLD || vehicle == player) return packet;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        Vec3d position = packet.position();
        double nearestX = geometry.nearestImageX(position.x, vehicle.getX());
        if (nearestX == position.x) return packet;
        return new VehicleMoveC2SPacket(
                new Vec3d(nearestX, position.y, position.z),
                packet.yaw(), packet.pitch(), packet.onGround());
    }

    @Inject(method = "onVehicleMove", at = @At("TAIL"))
    private void ringworld$foldVehicleAfterMovement(VehicleMoveC2SPacket packet, CallbackInfo ci) {
        ServerWorld world = player.getEntityWorld();
        Entity vehicle = player.getRootVehicle();
        if (world.getRegistryKey() != World.OVERWORLD || vehicle == player) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double vehicleShift = RingWorldServer.canonicalizeEntityPosition(vehicle, geometry);
        if (vehicleShift != 0.0) {
            lastTickRiddenX += vehicleShift;
            updatedRiddenX += vehicleShift;
        }
        // Passenger poses were calculated before the vehicle folded.
        vehicle.streamSelfAndPassengers().skip(1).forEach(passenger -> {
            double passengerShift = RingWorldServer.canonicalizeEntityPosition(passenger, geometry);
            if (passenger == player && passengerShift != 0.0) {
                lastTickX += passengerShift;
                updatedX += passengerShift;
            }
        });
    }
}
