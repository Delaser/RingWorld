package dev.ringworld.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access used by the opt-in multiplayer fixture's failure trace. */
@Mixin(Mob.class)
public interface MobFixtureGoalsAccessor {
    @Accessor("goalSelector") GoalSelector ringworld$fixtureGoals();
    @Accessor("targetSelector") GoalSelector ringworld$fixtureTargetGoals();
}
