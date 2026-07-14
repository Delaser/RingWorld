package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps moving entities indexed in canonical sections during seam motion. */
@Mixin(targets = "net.minecraft.server.world.ServerEntityManager$Listener")
abstract class ServerEntityManagerListenerMixin {
    @Shadow @Final private EntityLike entity;

    @Redirect(
            method = "updateEntityPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkSectionPos;toLong(Lnet/minecraft/util/math/BlockPos;)J"))
    private long ringworld$canonicalMovingSection(BlockPos pos) {
        if (!(entity instanceof Entity minecraftEntity)
                || !(minecraftEntity.getEntityWorld() instanceof ServerWorld world)
                || world.getRegistryKey() != World.OVERWORLD) {
            return ChunkSectionPos.toLong(pos);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return ChunkSectionPos.asLong(
                Math.floorDiv(geometry.wrapBlockX(pos.getX()), 16),
                ChunkSectionPos.getSectionCoord(pos.getY()),
                ChunkSectionPos.getSectionCoord(pos.getZ()));
    }
}
