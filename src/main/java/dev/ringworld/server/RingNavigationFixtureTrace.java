package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.mixin.MobFixtureGoalsAccessor;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Bounded observation only; never recomputes a path, changes AI or loads a chunk. */
final class RingNavigationFixtureTrace {
    private String previous;
    private int records;

    void reset() { previous = null; records = 0; }

    void record(ServerLevel world, RingGeometry geometry, Zombie mob, int tick, String event) {
        if (mob == null) return;
        var navigation = mob.getNavigation();
        var path = navigation.getPath();
        int next = path == null ? -1 : path.getNextNodeIndex();
        int count = path == null ? 0 : path.getNodeCount();
        String state = System.identityHashCode(path) + ":" + next + ":" + count
                + ":" + navigation.isDone() + ":" + navigation.getTargetPos();
        boolean terminal = event.equals("failure") || event.equals("arrival");
        if (!terminal && (records >= 32 || (state.equals(previous) && tick % 100 != 0))) return;
        previous = state;
        records++;
        var goals = (MobFixtureGoalsAccessor) mob;
        var move = mob.getMoveControl();
        int currentX = geometry.wrapBlockX(mob.blockPosition().getX()) >> 4;
        int currentZ = mob.blockPosition().getZ() >> 4;
        var chunks = world.getChunkSource();
        RingWorldMod.LOGGER.info(
                "[navigation-trace] event={} tick={} position={} velocity={} alive={} onGround={} "
                        + "navigationDone={} target={} pathReachable={} pathDistance={} node={}/{} next={} "
                        + "moveWanted={} moveTarget=({},{},{}) moveSpeed={} mobTarget={} "
                        + "goals={} targetGoals={} currentChunkLoaded={} sourceChunkLoaded={} targetChunkLoaded={}",
                event, tick, mob.position(), mob.getDeltaMovement(), mob.isAlive(), mob.onGround(),
                navigation.isDone(), navigation.getTargetPos(), path != null && path.canReach(),
                path == null ? Float.NaN : path.getDistToTarget(), next, count,
                path == null || path.isDone() ? null : path.getNextNodePos(),
                move.hasWanted(), move.getWantedX(), move.getWantedY(), move.getWantedZ(),
                move.getSpeedModifier(), mob.getTarget() == null ? null : mob.getTarget().getType(),
                running(goals.ringworld$fixtureGoals()), running(goals.ringworld$fixtureTargetGoals()),
                chunks.getChunkNow(currentX, currentZ) != null,
                chunks.getChunkNow(geometry.wrapBlockX(geometry.circumferenceBlocks() - 6) >> 4, 0) != null,
                chunks.getChunkNow(0, 0) != null);
    }

    private static String running(GoalSelector selector) {
        return selector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).limit(8)
                .map(goal -> goal.getPriority() + ":" + goal.getGoal().getClass().getSimpleName()
                        + goal.getFlags()).toList().toString();
    }
}
