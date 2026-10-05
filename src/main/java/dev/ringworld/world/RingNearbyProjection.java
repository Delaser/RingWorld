package dev.ringworld.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Trial-only visual mapping. Never use this for generation, collision or storage. */
public record RingNearbyProjection(RingGeometry geometry, double referenceRadius,
                                   double cameraY, double coreBlocks) {
    public static RingNearbyProjection create(RingGeometry geometry, double cameraY,
                                               double worldTopY, int chunks) {
        double core = Math.min(chunks * 16.0, geometry.circumferenceBlocks() / 8.0);
        double radius = Math.max(geometry.radius(),
                Math.max(worldTopY, cameraY) - geometry.surfaceReferenceY() + core * 2.0);
        return new RingNearbyProjection(geometry, radius, cameraY, core);
    }

    public double radiusAt(double y) { return referenceRadius + geometry.surfaceReferenceY() - y; }
    public double cameraRadius() { return radiusAt(cameraY); }
    public double blendEnd() { return coreBlocks * 2.0; }
    private double nearSlope() { return 1.0 / cameraRadius(); }
    private double farSlope() {
        double midpoint = (coreBlocks + blendEnd()) * 0.5;
        return (Math.PI - nearSlope() * midpoint)
                / (geometry.circumferenceBlocks() * 0.5 - midpoint);
    }

    /** Integral of positive angular spacing, closing exactly after one circumference. */
    public double angle(double delta) {
        double circumference = geometry.circumferenceBlocks();
        double turns = Math.floor((delta + circumference * 0.5) / circumference);
        double wrapped = delta - turns * circumference;
        double u = Math.abs(wrapped), near = nearSlope(), far = farSlope();
        double length = blendEnd() - coreBlocks;
        double value;
        if (u <= coreBlocks) value = near * u;
        else if (u < blendEnd()) {
            double t = (u - coreBlocks) / length;
            double integral = t * t * t - 0.5 * t * t * t * t;
            value = near * u + (far - near) * length * integral;
        } else value = near * (coreBlocks + length * 0.5)
                + far * (length * 0.5 + u - blendEnd());
        return Math.copySign(value, wrapped) + turns * Math.PI * 2.0;
    }

    public double angularSlope(double delta) {
        double u = Math.abs(geometry.shortestCircumferenceDelta(0.0, delta));
        double t = Math.clamp((u - coreBlocks) / (blendEnd() - coreBlocks), 0.0, 1.0);
        return nearSlope() + (farSlope() - nearSlope()) * t * t * (3.0 - 2.0 * t);
    }

    public Vec3 position(Vec3 point, Vec3 camera) {
        double theta = angle(geometry.shortestCircumferenceDelta(camera.x, point.x));
        double radius = radiusAt(point.y);
        return new Vec3(radius * Math.sin(theta), cameraRadius() - radius * Math.cos(theta),
                point.z - camera.z);
    }

    public RingObjectTransform object(Vec3 camera, double x, double y, double z) {
        Vec3 point = camera.add(x, y, z);
        double delta = geometry.shortestCircumferenceDelta(camera.x, point.x);
        return new RingObjectTransform(position(point, camera), angle(delta),
                radiusAt(point.y) * angularSlope(delta));
    }

    public AABB bounds(AABB box, Vec3 camera) {
        double start = geometry.shortestCircumferenceDelta(camera.x, box.minX);
        double a = angle(start), b = angle(start + box.maxX - box.minX);
        double minX = Double.POSITIVE_INFINITY, minY = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        int first = (int)Math.ceil(a / (Math.PI / 2.0));
        int last = (int)Math.floor(b / (Math.PI / 2.0));
        for (int i = -2; i <= last - first; i++) {
            double theta = i == -2 ? a : i == -1 ? b : (first + i) * Math.PI / 2.0;
            for (double y : new double[]{box.minY, box.maxY}) {
                double x = radiusAt(y) * Math.sin(theta);
                double py = cameraRadius() - radiusAt(y) * Math.cos(theta);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
            }
        }
        return new AABB(minX, minY, box.minZ - camera.z, maxX, maxY, box.maxZ - camera.z);
    }
}
