package dev.ringworld.world;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;
/** Translate the entire interpolation path together, retaining step timings. */
public final class RingPositionPaths {
    private RingPositionPaths() { }
    public static PositionPath shift(PositionPath path, double x) {
        Vec3 delta = new Vec3(x, 0, 0);
        if (path instanceof PositionPath.Linear linear) return new PositionPath.Linear(linear.endPosition().add(delta));
        if (path instanceof PositionPath.Stepped stepped) return new PositionPath.Stepped(
                stepped.endPosition().add(delta), stepped.steps().stream().map(step -> step.addDelta(delta)).toList());
        throw new IllegalArgumentException("Unknown movement path: " + path.type());
    }
}
