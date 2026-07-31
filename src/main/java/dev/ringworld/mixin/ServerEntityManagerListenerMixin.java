package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps moving entities indexed in canonical sections during seam motion. */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
abstract class ServerEntityManagerListenerMixin {
    @Shadow @Final private EntityAccess entity;

    @Redirect(
            method = "onMove",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long ringworld$canonicalMovingSection(BlockPos pos) {
        if (!(entity instanceof Entity minecraftEntity)
                || !(minecraftEntity.level() instanceof ServerLevel world)
                || world.dimension() != Level.OVERWORLD) {
            return SectionPos.asLong(pos);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return SectionPos.asLong(
                Math.floorDiv(geometry.wrapBlockX(pos.getX()), 16),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
