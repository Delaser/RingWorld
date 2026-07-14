package dev.ringworld.world;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Pure coordinate mathematics shared by client, server, and compatibility
 * integrations. Canonical X is the periodic circumference; canonical Z is
 * the finite width. Canonical Y points towards the ring centre (local up).
 */
public record RingGeometry(int widthBlocks, int circumferenceBlocks) {
    public static final double SURFACE_Y = 64.0;
    public RingGeometry {
        if (widthBlocks < RingWorldSettings.MIN_WIDTH || widthBlocks % 16 != 0) {
            throw new IllegalArgumentException("width must be a positive 16-block multiple");
        }
        if (circumferenceBlocks < RingWorldSettings.MIN_CIRCUMFERENCE || circumferenceBlocks % 16 != 0) {
            throw new IllegalArgumentException("circumference must be a safe 16-block multiple");
        }
    }

    public double radius() {
        return circumferenceBlocks / (2.0 * Math.PI);
    }

    public double angleAt(double canonicalX) {
        return Math.PI * 2.0 * wrapX(canonicalX) / circumferenceBlocks;
    }

    public double wrapX(double canonicalX) {
        double result = canonicalX % circumferenceBlocks;
        return result < 0.0 ? result + circumferenceBlocks : result;
    }

    public int wrapBlockX(int canonicalX) {
        return Math.floorMod(canonicalX, circumferenceBlocks);
    }

    /** Selects the periodic copy of {@code canonicalX} nearest {@code referenceX}. */
    public double nearestImageX(double canonicalX, double referenceX) {
        double wrapped = wrapX(canonicalX);
        double imageIndex = Math.rint((referenceX - wrapped) / circumferenceBlocks);
        return wrapped + imageIndex * circumferenceBlocks;
    }

    /** True only when a position has left the server's canonical storage range. */
    public boolean needsCanonicalWrap(double x) {
        return x < 0.0 || x >= circumferenceBlocks;
    }

    public boolean isInsideWidth(double canonicalZ) {
        return canonicalZ >= minWidthZ() + 1.0 && canonicalZ <= maxWidthZ() - 1.0;
    }

    /** Centres the finite band on vanilla's usual world-spawn region at Z=0. */
    public int minWidthZ() {
        return -widthBlocks / 2;
    }

    public int maxWidthZ() {
        return minWidthZ() + widthBlocks - 1;
    }

    /** Position in physical ring space: X is lateral width; Y/Z form the ring. */
    public Vec3d toPhysical(double x, double y, double z) {
        double angle = angleAt(x);
        double radialDistance = radius() + SURFACE_Y - y;
        return new Vec3d(z, radialDistance * Math.cos(angle), radialDistance * Math.sin(angle));
    }

    /** Returns ring-space coordinates in the supplied camera's local Minecraft axes. */
    public Vec3d toCameraLocal(Vec3d canonicalPosition, Vec3d cameraCanonicalPosition) {
        double deltaAngle = tangentFrameAngle(cameraCanonicalPosition.x, canonicalPosition.x);
        double positionRadius = radius() + SURFACE_Y - canonicalPosition.y;
        double cameraRadius = radius() + SURFACE_Y - cameraCanonicalPosition.y;
        return new Vec3d(
                positionRadius * Math.sin(deltaAngle),
                cameraRadius - positionRadius * Math.cos(deltaAngle),
                canonicalPosition.z - cameraCanonicalPosition.z);
    }

    /**
     * Physical centre of the ring expressed in the camera's local Minecraft
     * axes. The centre has zero radial distance and lies on the band-width
     * midline, so this remains one authoritative point for every client X chart.
     */
    public Vec3d ringCenterInCameraFrame(Vec3d cameraCanonicalPosition) {
        Vec3d centerAtCameraAngle = new Vec3d(
                cameraCanonicalPosition.x,
                radius() + SURFACE_Y,
                0.0);
        return toCameraLocal(centerAtCameraAngle, cameraCanonicalPosition);
    }

    /** Unit view direction from the camera to the physical ring centre. */
    public Vec3d directionToRingCenter(Vec3d cameraCanonicalPosition) {
        Vec3d center = ringCenterInCameraFrame(cameraCanonicalPosition);
        return center.lengthSquared() < 1.0e-12
                ? new Vec3d(0.0, 1.0, 0.0)
                : center.normalize();
    }

    /**
     * Conservative camera-local bounds for a canonical, axis-aligned volume
     * after it has been bent onto the ring. Minecraft performs CPU frustum
     * culling before the terrain vertex shader runs, so the renderer uses
     * this exact cylindrical envelope instead of the original flat box.
     */
    public Box toCameraLocalBounds(Box canonicalBounds, Vec3d cameraCanonicalPosition) {
        double startAngle = tangentFrameAngle(cameraCanonicalPosition.x, canonicalBounds.minX);
        double endAngle = startAngle + Math.PI * 2.0
                * (canonicalBounds.maxX - canonicalBounds.minX) / circumferenceBlocks;
        double minAngle = Math.min(startAngle, endAngle);
        double maxAngle = Math.max(startAngle, endAngle);
        double cameraRadius = radius() + SURFACE_Y - cameraCanonicalPosition.y;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        // Endpoints plus every sine/cosine extremum inside the interval give
        // the exact XY envelope for both radial endpoints of the volume.
        int firstCardinal = (int)Math.ceil(minAngle / (Math.PI / 2.0));
        int lastCardinal = (int)Math.floor(maxAngle / (Math.PI / 2.0));
        int sampleCount = 2 + Math.max(0, lastCardinal - firstCardinal + 1);
        for (int sample = 0; sample < sampleCount; sample++) {
            double angle = sample == 0 ? startAngle
                    : sample == 1 ? endAngle
                    : (firstCardinal + sample - 2) * (Math.PI / 2.0);
            for (double y : new double[]{canonicalBounds.minY, canonicalBounds.maxY}) {
                double pointRadius = radius() + SURFACE_Y - y;
                double localX = pointRadius * Math.sin(angle);
                double localY = cameraRadius - pointRadius * Math.cos(angle);
                minX = Math.min(minX, localX);
                maxX = Math.max(maxX, localX);
                minY = Math.min(minY, localY);
                maxY = Math.max(maxY, localY);
            }
        }

        return new Box(minX, minY,
                canonicalBounds.minZ - cameraCanonicalPosition.z,
                maxX, maxY,
                canonicalBounds.maxZ - cameraCanonicalPosition.z);
    }

    /**
     * Rotation around the camera's local Z axis that carries its upright frame
     * into the tangent frame at {@code positionX}. The shortest periodic delta
     * keeps the value numerically stable after arbitrarily many seam crossings.
     */
    public double tangentFrameAngle(double cameraX, double positionX) {
        return Math.PI * 2.0 * shortestCircumferenceDelta(cameraX, positionX)
                / circumferenceBlocks;
    }

    /**
     * Physical outward direction for renderers and compatibility integrations.
     * Gameplay gravity remains vanilla -Y in intrinsic surface coordinates.
     */
    public Vec3d gravityAt(double canonicalX) {
        double angle = angleAt(canonicalX);
        return new Vec3d(0.0, Math.cos(angle), Math.sin(angle));
    }

    public double shortestCircumferenceDelta(double fromX, double toX) {
        double delta = wrapX(toX) - wrapX(fromX);
        if (delta > circumferenceBlocks / 2.0) delta -= circumferenceBlocks;
        if (delta < -circumferenceBlocks / 2.0) delta += circumferenceBlocks;
        return delta;
    }
}
