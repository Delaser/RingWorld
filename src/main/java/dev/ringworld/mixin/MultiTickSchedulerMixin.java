package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Canonicalizes block and fluid ticks recorded during periodic worldgen. */
@Mixin(WorldGenTickAccess.class)
abstract class MultiTickSchedulerMixin<T> implements RingTickSchedulerAccess {
    @Unique private RingGeometry ringworld$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        ringworld$geometry = geometry;
    }

    @ModifyArg(
            method = "schedule",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/ticks/TickContainerAccess;schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V"),
            index = 0)
    private ScheduledTick<T> ringworld$canonicalScheduledTick(ScheduledTick<T> tick) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null) return tick;
        BlockPos pos = tick.pos();
        int x = geometry.wrapBlockX(pos.getX());
        if (x == pos.getX()) return tick;
        return new ScheduledTick<>(tick.type(), new BlockPos(x, pos.getY(), pos.getZ()),
                tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }

    @ModifyArg(
            method = "hasScheduledTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/ticks/TickContainerAccess;hasScheduledTick(Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Z"),
            index = 0)
    private BlockPos ringworld$canonicalQueuedPosition(BlockPos pos) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null) return pos;
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }
}
