package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.tick.OrderedTick;
import net.minecraft.world.tick.WorldTickScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stores block and fluid ticks under the same canonical chunk as their block data. */
@Mixin(WorldTickScheduler.class)
abstract class WorldTickSchedulerMixin<T> implements RingTickSchedulerAccess {
    @Unique private RingGeometry ringworld$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        ringworld$geometry = geometry;
    }

    @ModifyVariable(method = "scheduleTick", at = @At("HEAD"), argsOnly = true)
    private OrderedTick<T> ringworld$canonicalScheduledTick(OrderedTick<T> tick) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null) return tick;
        BlockPos pos = tick.pos();
        int canonicalX = geometry.wrapBlockX(pos.getX());
        if (canonicalX == pos.getX()) return tick;
        return new OrderedTick<>(tick.type(), new BlockPos(canonicalX, pos.getY(), pos.getZ()),
                tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }
}
