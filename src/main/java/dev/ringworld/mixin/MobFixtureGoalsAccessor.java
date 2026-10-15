package dev.ringworld.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Goal access for the opt-in tagged navigator fixture and its read-only trace. */
@Mixin(Mob.class)
public interface MobFixtureGoalsAccessor {
    @Accessor("goalSelector") GoalSelector ringworld$fixtureGoals();
    @Accessor("targetSelector") GoalSelector ringworld$fixtureTargetGoals();
}
