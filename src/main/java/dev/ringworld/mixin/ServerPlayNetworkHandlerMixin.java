package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.server.RingWorldMultiplayerTest;
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
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayer player;
    @Shadow private double firstGoodX;
    @Shadow private double lastGoodX;
    @Shadow private double vehicleFirstGoodX;
    @Shadow private double vehicleLastGoodX;

    @ModifyVariable(
            method = "handleMovePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"),
            argsOnly = true)
    private ServerboundMovePlayerPacket ringworld$projectPlayerMovement(ServerboundMovePlayerPacket packet) {
        ServerLevel world = player.level();
        if (!packet.hasPosition() || world.dimension() != Level.OVERWORLD) return packet;
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
                player.setPos(localSourceX, player.getY(), player.getZ());
                firstGoodX += sourceShift;
                lastGoodX += sourceShift;
                RingWorldServer.recordPlayerCanonicalWrap(player);
            }
        }

        if (canonicalTargetX == presentationX) return packet;
        return new ServerboundMovePlayerPacket.PosRot(canonicalTargetX,
                packet.getY(player.getY()), packet.getZ(player.getZ()),
                packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()),
                packet.isOnGround(), packet.horizontalCollision());
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void ringworld$foldPlayerAfterMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerLevel world = player.level();
        if (world.dimension() != Level.OVERWORLD) return;
        double shift = RingWorldServer.canonicalizeEntityPosition(player, RingWorldServer.geometryFor(world));
        if (shift == 0.0) return;
        RingWorldServer.recordPlayerCanonicalWrap(player);
        // Vanilla's anti-cheat baselines must move to the same coordinate
        // chart or the next perfectly ordinary packet looks C blocks long.
        firstGoodX += shift;
        lastGoodX += shift;
    }

    @ModifyVariable(method = "handleMoveVehicle", at = @At("HEAD"), argsOnly = true)
    private ServerboundMoveVehiclePacket ringworld$projectVehicleMovement(ServerboundMoveVehiclePacket packet) {
        ServerLevel world = player.level();
        Entity vehicle = player.getRootVehicle();
        if (world.dimension() != Level.OVERWORLD || vehicle == player) return packet;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        Vec3 position = packet.position();
        double nearestX = geometry.nearestImageX(position.x, vehicle.getX());
        if (nearestX == position.x) return packet;
        return new ServerboundMoveVehiclePacket(
                new Vec3(nearestX, position.y, position.z),
                packet.yRot(), packet.xRot(), packet.onGround());
    }

    @Inject(method = "handleMoveVehicle", at = @At("TAIL"))
    private void ringworld$foldVehicleAfterMovement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        ServerLevel world = player.level();
        Entity vehicle = player.getRootVehicle();
        if (world.dimension() != Level.OVERWORLD || vehicle == player) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double vehicleShift = RingWorldServer.canonicalizeEntityPosition(vehicle, geometry);
        if (vehicleShift != 0.0) {
            vehicleFirstGoodX += vehicleShift;
            vehicleLastGoodX += vehicleShift;
        }
        // Passenger poses were calculated before the vehicle folded.
        vehicle.getSelfAndPassengers().skip(1).forEach(passenger -> {
            double passengerShift = RingWorldServer.canonicalizeEntityPosition(passenger, geometry);
            if (passenger == player && passengerShift != 0.0) {
                firstGoodX += passengerShift;
                lastGoodX += passengerShift;
            }
        });
    }
}
