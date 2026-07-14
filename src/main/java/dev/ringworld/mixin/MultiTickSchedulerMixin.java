package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.tick.MultiTickScheduler;
import net.minecraft.world.tick.OrderedTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Canonicalizes block and fluid ticks recorded during periodic worldgen. */
@Mixin(MultiTickScheduler.class)
abstract class MultiTickSchedulerMixin<T> implements RingTickSchedulerAccess {
    @Unique private RingGeometry ringworld$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        ringworld$geometry = geometry;
    }

    @ModifyArg(
            method = "scheduleTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/tick/BasicTickScheduler;scheduleTick(Lnet/minecraft/world/tick/OrderedTick;)V"),
            index = 0)
    private OrderedTick<T> ringworld$canonicalScheduledTick(OrderedTick<T> tick) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null) return tick;
        BlockPos pos = tick.pos();
        int x = geometry.wrapBlockX(pos.getX());
        if (x == pos.getX()) return tick;
        return new OrderedTick<>(tick.type(), new BlockPos(x, pos.getY(), pos.getZ()),
                tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }

    @ModifyArg(
            method = "isQueued",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/tick/BasicTickScheduler;isQueued(Lnet/minecraft/util/math/BlockPos;Ljava/lang/Object;)Z"),
            index = 0)
    private BlockPos ringworld$canonicalQueuedPosition(BlockPos pos) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null) return pos;
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }
}
