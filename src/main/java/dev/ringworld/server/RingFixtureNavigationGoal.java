package dev.ringworld.server;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Owns the tagged fixture zombie's MOVE input while its single commanded path runs.
 * Vanilla still computes, follows and folds that path. This goal never issues or
 * retries movement, changes velocity, or declares arrival; it only prevents an
 * unrelated random-stroll/attack goal from replacing the test's input.
 */
final class RingFixtureNavigationGoal extends Goal {
    RingFixtureNavigationGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

    @Override public boolean canUse() { return true; }
    @Override public boolean canContinueToUse() { return true; }
    @Override public boolean isInterruptable() { return false; }
}
