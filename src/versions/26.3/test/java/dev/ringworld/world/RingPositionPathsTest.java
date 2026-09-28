package dev.ringworld.world;

import java.util.List;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingPositionPathsTest {
    @Test
    void linearPathMovesOnlyItsPresentationX() {
        var original = new PositionPath.Linear(new Vec3(1, 70, -8));
        var shifted = RingPositionPaths.shift(original, 2048);
        assertInstanceOf(PositionPath.Linear.class, shifted);
        assertEquals(new Vec3(2049, 70, -8), shifted.endPosition());
        assertEquals(new Vec3(1, 70, -8), original.endPosition());
    }

    @Test
    void steppedPathPreservesEveryWaypointAndTimingAcrossNegativeLap() {
        var original = new PositionPath.Stepped(new Vec3(2051, 71, -9), List.of(
                new PositionStep(new Vec3(2047, 70, -8), 2),
                new PositionStep(new Vec3(2049, 70.5, -8.5), 5)));
        var shifted = (PositionPath.Stepped) RingPositionPaths.shift(original, -2048);
        assertEquals(new Vec3(3, 71, -9), shifted.endPosition());
        assertEquals(List.of(new PositionStep(new Vec3(-1, 70, -8), 2),
                new PositionStep(new Vec3(1, 70.5, -8.5), 5)), shifted.steps());
        assertEquals(new Vec3(2047, 70, -8), original.steps().getFirst().position());
    }
}
