package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sends canonical ring coordinates while the client keeps logical ones. */
@Mixin(ClientConnection.class)
abstract class ClientConnectionMixin {
    @ModifyVariable(
            method = "send(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> ringworld$canonicalizeOutboundPosition(Packet<?> packet) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return packet;
        if (packet instanceof PlayerActionC2SPacket action) {
            return new PlayerActionC2SPacket(action.getAction(), canonical(action.getPos(), geometry),
                    action.getDirection(), action.getSequence());
        }
        if (packet instanceof PlayerInteractBlockC2SPacket interaction) {
            BlockHitResult hit = interaction.getBlockHitResult();
            Vec3d hitPosition = hit.getPos();
            BlockHitResult canonicalHit = new BlockHitResult(
                    new Vec3d(geometry.wrapX(hitPosition.x), hitPosition.y, hitPosition.z),
                    hit.getSide(), canonical(hit.getBlockPos(), geometry),
                    hit.isInsideBlock(), hit.isAgainstWorldBorder());
            return new PlayerInteractBlockC2SPacket(interaction.getHand(), canonicalHit, interaction.getSequence());
        }
        return packet;
    }

    private static BlockPos canonical(BlockPos pos, RingGeometry geometry) {
        return new BlockPos(geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ());
    }
}
