package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stores block and fluid ticks under the same canonical chunk as their block data. */
@Mixin(LevelTicks.class)
abstract class WorldTickSchedulerMixin<T> implements RingTickSchedulerAccess {
    @Unique private RingGeometry ringworld$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        ringworld$geometry = geometry;
    }

    @ModifyVariable(method = {"hasScheduledTick", "willTickThisTick"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalTickPosition(BlockPos pos) {
        return ringworld$wrapTickPosition(pos);
    }

    @Unique
    private BlockPos ringworld$wrapTickPosition(BlockPos pos) {
        return ringworld$geometry == null ? pos
                : dev.ringworld.world.RingBlockCoordinates.canonicalBlockPos(pos, ringworld$geometry);
    }

    @ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true)
    private ScheduledTick<T> ringworld$canonicalScheduledTick(ScheduledTick<T> tick) {
        BlockPos pos = ringworld$wrapTickPosition(tick.pos());
        if (pos == tick.pos()) return tick;
        return new ScheduledTick<>(tick.type(), pos,
                tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }
}
