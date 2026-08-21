package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingInteractionCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
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
                    hit.isInside());
            return new ServerboundUseItemOnPacket(interaction.getHand(), canonicalHit, interaction.getSequence());
        }
        if (packet instanceof ServerboundSignUpdatePacket sign) {
            String[] lines = sign.getLines();
            return new ServerboundSignUpdatePacket(canonical(sign.getPos(), geometry), sign.isFrontText(),
                    lines[0], lines[1], lines[2], lines[3]);
        }
        if (packet instanceof ServerboundBlockEntityTagQueryPacket query) {
            return new ServerboundBlockEntityTagQueryPacket(
                    query.getTransactionId(), canonical(query.getPos(), geometry));
        }
        if (packet instanceof ServerboundSetCommandBlockPacket commandBlock) {
            return new ServerboundSetCommandBlockPacket(
                    canonical(commandBlock.getPos(), geometry), commandBlock.getCommand(),
                    commandBlock.getMode(), commandBlock.isTrackOutput(),
                    commandBlock.isConditional(), commandBlock.isAutomatic());
        }
        if (packet instanceof ServerboundSetStructureBlockPacket structureBlock) {
            return new ServerboundSetStructureBlockPacket(
                    canonical(structureBlock.getPos(), geometry), structureBlock.getUpdateType(),
                    structureBlock.getMode(), structureBlock.getName(), structureBlock.getOffset(),
                    structureBlock.getSize(), structureBlock.getMirror(), structureBlock.getRotation(),
                    structureBlock.getData(), structureBlock.isIgnoreEntities(),
                    structureBlock.isShowAir(), structureBlock.isShowBoundingBox(),
                    structureBlock.getIntegrity(), structureBlock.getSeed());
        }
        if (packet instanceof ServerboundSetJigsawBlockPacket jigsawBlock) {
            return new ServerboundSetJigsawBlockPacket(
                    canonical(jigsawBlock.getPos(), geometry), jigsawBlock.getName(),
                    jigsawBlock.getTarget(), jigsawBlock.getPool(), jigsawBlock.getFinalState(),
                    jigsawBlock.getJoint(), jigsawBlock.getSelectionPriority(),
                    jigsawBlock.getPlacementPriority());
        }
        if (packet instanceof ServerboundJigsawGeneratePacket jigsawGenerate) {
            return new ServerboundJigsawGeneratePacket(
                    canonical(jigsawGenerate.getPos(), geometry),
                    jigsawGenerate.levels(), jigsawGenerate.keepJigsaws());
        }
        return packet;
    }

    private static BlockPos canonical(BlockPos pos, RingGeometry geometry) {
        return new BlockPos(geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ());
    }
}
