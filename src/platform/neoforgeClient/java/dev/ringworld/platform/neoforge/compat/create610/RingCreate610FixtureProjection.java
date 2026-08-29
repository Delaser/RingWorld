package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.world.RingGeometry;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Curved physical-ring aiming and projection math for disposable Create fixtures. */
final class RingCreate610FixtureProjection {
    private static final double NEAR_DEPTH = 0.05;

    private RingCreate610FixtureProjection() { }

    static Aim aim(
            RingGeometry geometry, Vec3 cameraIntrinsic, List<Vec3> targetIntrinsic,
            double yawOffsetDegrees, int viewportWidth, int viewportHeight,
            double verticalFovDegrees) {
        if (targetIntrinsic.isEmpty()) throw new IllegalArgumentException("target points are empty");
        List<Vec3> local = targetIntrinsic.stream()
                .map(point -> geometry.toCameraLocal(point, cameraIntrinsic))
                .toList();
        Vec3 center = boundsCenter(local);
        float yaw = (float) (Math.toDegrees(Math.atan2(-center.x, center.z)) + yawOffsetDegrees);
        float pitch = (float) -Math.toDegrees(Math.atan2(center.y, Math.hypot(center.x, center.z)));
        return new Aim(yaw, pitch, projectCameraLocal(local, center, yaw, pitch,
                viewportWidth, viewportHeight, verticalFovDegrees));
    }

    static Projection projectCameraLocal(
            List<Vec3> localPoints, Vec3 center, float yawDegrees, float pitchDegrees,
            int width, int height, double verticalFovDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 up = new Vec3(
                -Math.sin(yaw) * Math.sin(pitch),
                Math.cos(pitch),
                Math.cos(yaw) * Math.sin(pitch));
        double tanVertical = Math.tan(Math.toRadians(verticalFovDegrees) * 0.5);
        double tanHorizontal = tanVertical * ((double) width / height);

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minDepth = Double.POSITIVE_INFINITY;
        double maxDepth = Double.NEGATIVE_INFINITY;
        int inViewport = 0;
        int projected = 0;
        for (Vec3 point : localPoints) {
            ScreenPoint screen = screen(point, forward, right, up, tanHorizontal, tanVertical, width, height);
            if (screen == null) continue;
            projected++;
            minX = Math.min(minX, screen.x);
            minY = Math.min(minY, screen.y);
            maxX = Math.max(maxX, screen.x);
            maxY = Math.max(maxY, screen.y);
            minDepth = Math.min(minDepth, screen.depth);
            maxDepth = Math.max(maxDepth, screen.depth);
            if (screen.x >= 0.0 && screen.x < width && screen.y >= 0.0 && screen.y < height) {
                inViewport++;
            }
        }
        ScreenPoint centerScreen = screen(
                center, forward, right, up, tanHorizontal, tanVertical, width, height);
        boolean centerInViewport = centerScreen != null
                && centerScreen.x >= 0.0 && centerScreen.x < width
                && centerScreen.y >= 0.0 && centerScreen.y < height;
        return new Projection(
                minX, minY, maxX, maxY, minDepth, maxDepth,
                inViewport, projected, localPoints.size(), centerInViewport,
                centerScreen == null ? Double.NaN : centerScreen.x,
                centerScreen == null ? Double.NaN : centerScreen.y,
                centerScreen == null ? Double.NaN : centerScreen.depth);
    }

    private static ScreenPoint screen(
            Vec3 point, Vec3 forward, Vec3 right, Vec3 up,
            double tanHorizontal, double tanVertical, int width, int height) {
        double depth = point.dot(forward);
        if (!Double.isFinite(depth) || depth <= NEAR_DEPTH) return null;
        double ndcX = point.dot(right) / (depth * tanHorizontal);
        double ndcY = point.dot(up) / (depth * tanVertical);
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) return null;
        return new ScreenPoint(
                (ndcX * 0.5 + 0.5) * width,
                (0.5 - ndcY * 0.5) * height,
                depth);
    }

    private static Vec3 boundsCenter(List<Vec3> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }

    record Aim(float yaw, float pitch, Projection projection) { }

    record Projection(
            double minX, double minY, double maxX, double maxY,
            double minDepth, double maxDepth, int pointsInViewport,
            int projectedPoints, int totalPoints, boolean centerInViewport,
            double centerX, double centerY, double centerDepth) {
        double width() { return maxX - minX; }
        double height() { return maxY - minY; }
        boolean intersectsViewport(int width, int height) {
            return projectedPoints > 0 && maxX >= 0.0 && minX < width && maxY >= 0.0 && minY < height;
        }
        String logValue() {
            return String.format(java.util.Locale.ROOT,
                    "%.2f/%.2f/%.2f/%.2f depth=%.3f/%.3f center=%.2f/%.2f/%.3f centerIn=%s cornersIn=%d/%d projected=%d",
                    minX, minY, maxX, maxY, minDepth, maxDepth,
                    centerX, centerY, centerDepth, centerInViewport,
                    pointsInViewport, totalPoints, projectedPoints);
        }
    }

    private record ScreenPoint(double x, double y, double depth) { }
}
