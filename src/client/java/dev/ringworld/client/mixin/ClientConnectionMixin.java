package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingInteractionCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sends canonical ring coordinates while the client keeps logical ones. */
@Mixin(Connection.class)
abstract class ClientConnectionMixin {
    @ModifyVariable(
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> ringworld$canonicalizeOutboundPosition(Packet<?> packet) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return packet;
        if (packet instanceof ServerboundPlayerActionPacket action) {
            return new ServerboundPlayerActionPacket(action.getAction(), canonical(action.getPos(), geometry),
                    action.getDirection(), action.getSequence());
        }
        if (packet instanceof ServerboundUseItemOnPacket interaction) {
            BlockHitResult hit = interaction.getHitResult();
            Vec3 hitPosition = hit.getLocation();
            RingInteractionCoordinates.CanonicalBlockHit canonical =
                    RingInteractionCoordinates.canonicalizeBlockHit(
                            geometry, hit.getBlockPos().getX(), hitPosition.x);
            BlockHitResult canonicalHit = new BlockHitResult(
                    new Vec3(canonical.hitX(), hitPosition.y, hitPosition.z),
                    hit.getDirection(), new BlockPos(canonical.blockX(),
                            hit.getBlockPos().getY(), hit.getBlockPos().getZ()),
                    hit.isInside(), hit.isWorldBorderHit());
            return new ServerboundUseItemOnPacket(interaction.getHand(), canonicalHit, interaction.getSequence());
        }
        if (packet instanceof ServerboundSignUpdatePacket sign) {
            String[] lines = sign.getLines();
            return new ServerboundSignUpdatePacket(canonical(sign.getPos(), geometry), sign.isFrontText(),
                    lines[0], lines[1], lines[2], lines[3]);
        }
        if (packet instanceof ServerboundPickItemFromBlockPacket pick) {
            return new ServerboundPickItemFromBlockPacket(canonical(pick.pos(), geometry), pick.includeData());
        }
        if (packet instanceof ServerboundBlockEntityTagQueryPacket query) {
            return new ServerboundBlockEntityTagQueryPacket(
                    query.getTransactionId(), canonical(query.getPos(), geometry));
        }
        return packet;
    }

    private static BlockPos canonical(BlockPos pos, RingGeometry geometry) {
        return new BlockPos(geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ());
    }
}
