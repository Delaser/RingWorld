package dev.ringworld.server;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingFixtureNavigationGoalTest {
    private static final class CompetingGoal extends Goal {
        int starts;
        CompetingGoal(Flag flag) { setFlags(EnumSet.of(flag)); }
        @Override public boolean canUse() { return true; }
        @Override public void start() { starts++; }
    }

    @Test
    void vanillaSelectorReproducesUnownedMoveInputBeingTakenByAnotherGoal() {
        var selector = new GoalSelector(() -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        var stroll = new CompetingGoal(Goal.Flag.MOVE);
        selector.addGoal(7, stroll);
        selector.tick();
        assertEquals(1, stroll.starts,
                "an external navigation command does not reserve GoalSelector MOVE");
    }

    @Test
    void fixtureOwnsMoveWithoutDisablingOtherAiOrRetryingThePath() {
        var selector = new GoalSelector(() -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        var input = new RingFixtureNavigationGoal();
        var stroll = new CompetingGoal(Goal.Flag.MOVE);
        var look = new CompetingGoal(Goal.Flag.LOOK);
        selector.addGoal(0, input);
        selector.addGoal(7, stroll);
        selector.addGoal(8, look);
        for (int tick = 0; tick < 1200; tick++) selector.tick();
        assertEquals(0, stroll.starts);
        assertEquals(1, look.starts);
        assertEquals(EnumSet.of(Goal.Flag.MOVE), input.getFlags());
        assertTrue(selector.getAvailableGoals().stream().anyMatch(
                goal -> goal.getGoal() == input && goal.isRunning()));
        selector.removeGoal(input);
        selector.tick();
        assertEquals(1, stroll.starts);
    }
}
