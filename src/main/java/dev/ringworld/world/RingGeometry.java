package dev.ringworld.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    /** Authoritative intrinsic surface elevation for settings format 2. */
    public double surfaceReferenceY() {
        return SURFACE_Y;
    }

    public int circumferenceChunks() {
        return circumferenceBlocks / 16;
    }

    public int widthChunks() {
        return widthBlocks / 16;
    }

    /** Intrinsic Y coordinate occupied by the physical centre of the ring. */
    public double physicalCenterY() {
        return radius() + surfaceReferenceY();
    }

    /** Physical cylindrical radius of an intrinsic horizontal plane. */
    public double physicalRadiusAt(double intrinsicY) {
        return physicalCenterY() - intrinsicY;
    }

    /** Physical distance through the ring centre to the opposite reference surface. */
    public double oppositeReferenceSurfaceDistance(double observerY) {
        return physicalRadiusAt(observerY) + radius();
    }

    /**
     * Conservative distance from an observer to the opposite reference
     * surface at the farther finite-width edge.
     */
    public double maximumReferenceSurfaceDistance(double observerY, double observerZ) {
        double maximumWidthDelta = Math.max(
                Math.abs(observerZ - minWidthZ()),
                Math.abs(observerZ - (maxWidthZ() + 1.0)));
        return Math.hypot(
                oppositeReferenceSurfaceDistance(observerY),
                maximumWidthDelta);
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

    /** Next presentation-chart seam strictly ahead while travelling in +X. */
    public double nextPositiveSeamX(double presentationX) {
        if (!Double.isFinite(presentationX)) {
            throw new IllegalArgumentException("presentation X must be finite");
        }
        return (Math.floor(presentationX / circumferenceBlocks) + 1.0)
                * circumferenceBlocks;
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

    public int minChunkZ() {
        return Math.floorDiv(minWidthZ(), 16);
    }

    public int maxChunkZ() {
        return Math.floorDiv(maxWidthZ(), 16);
    }

    /** True only for chunk rows outside the finite playable band. */
    public boolean isExteriorChunkZ(int chunkZ) {
        return chunkZ < minChunkZ() || chunkZ > maxChunkZ();
    }

    /** Position in physical ring space: X is lateral width; Y/Z form the ring. */
    public Vec3 toPhysical(double x, double y, double z) {
        double angle = angleAt(x);
        double radialDistance = physicalRadiusAt(y);
        return new Vec3(z, radialDistance * Math.cos(angle), radialDistance * Math.sin(angle));
    }

    /** Returns ring-space coordinates in the supplied camera's local Minecraft axes. */
    public Vec3 toCameraLocal(Vec3 canonicalPosition, Vec3 cameraCanonicalPosition) {
        double deltaAngle = tangentFrameAngle(cameraCanonicalPosition.x, canonicalPosition.x);
        double positionRadius = physicalRadiusAt(canonicalPosition.y);
        double cameraRadius = physicalRadiusAt(cameraCanonicalPosition.y);
        return new Vec3(
                positionRadius * Math.sin(deltaAngle),
                cameraRadius - positionRadius * Math.cos(deltaAngle),
                canonicalPosition.z - cameraCanonicalPosition.z);
    }

    /**
     * Physical centre of the ring expressed in the camera's local Minecraft
     * axes. The centre has zero radial distance and lies on the band-width
     * midline, so this remains one authoritative point for every client X chart.
     */
    public Vec3 ringCenterInCameraFrame(Vec3 cameraCanonicalPosition) {
        Vec3 centerAtCameraAngle = new Vec3(
                cameraCanonicalPosition.x,
                physicalCenterY(),
                0.0);
        return toCameraLocal(centerAtCameraAngle, cameraCanonicalPosition);
    }

    /** Unit view direction from the camera to the physical ring centre. */
    public Vec3 directionToRingCenter(Vec3 cameraCanonicalPosition) {
        Vec3 center = ringCenterInCameraFrame(cameraCanonicalPosition);
        return center.lengthSqr() < 1.0e-12
                ? new Vec3(0.0, 1.0, 0.0)
                : center.normalize();
    }

    /**
     * Conservative camera-local bounds for a canonical, axis-aligned volume
     * after it has been bent onto the ring. Minecraft performs CPU frustum
     * culling before the terrain vertex shader runs, so the renderer uses
     * this exact cylindrical envelope instead of the original flat box.
     */
    public AABB toCameraLocalBounds(AABB canonicalBounds, Vec3 cameraCanonicalPosition) {
        double startAngle = tangentFrameAngle(cameraCanonicalPosition.x, canonicalBounds.minX);
        double endAngle = startAngle + Math.PI * 2.0
                * (canonicalBounds.maxX - canonicalBounds.minX) / circumferenceBlocks;
        double minAngle = Math.min(startAngle, endAngle);
        double maxAngle = Math.max(startAngle, endAngle);
        double cameraRadius = physicalRadiusAt(cameraCanonicalPosition.y);

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
                double pointRadius = physicalRadiusAt(y);
                double localX = pointRadius * Math.sin(angle);
                double localY = cameraRadius - pointRadius * Math.cos(angle);
                minX = Math.min(minX, localX);
                maxX = Math.max(maxX, localX);
                minY = Math.min(minY, localY);
                maxY = Math.max(maxY, localY);
            }
        }

        return new AABB(minX, minY,
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
    public Vec3 gravityAt(double canonicalX) {
        double angle = angleAt(canonicalX);
        return new Vec3(0.0, Math.cos(angle), Math.sin(angle));
    }

    public double shortestCircumferenceDelta(double fromX, double toX) {
        double delta = wrapX(toX) - wrapX(fromX);
        if (delta > circumferenceBlocks / 2.0) delta -= circumferenceBlocks;
        if (delta < -circumferenceBlocks / 2.0) delta += circumferenceBlocks;
        return delta;
    }

    /** Axis-aligned reach test whose X component uses the nearest periodic image. */
    public boolean isWithinPeriodicBox(double sourceX, double sourceY, double sourceZ,
                                       double targetX, double targetY, double targetZ,
                                       double maxX, double maxY, double maxZ) {
        return Math.abs(shortestCircumferenceDelta(sourceX, targetX)) <= maxX
                && Math.abs(sourceY - targetY) <= maxY
                && Math.abs(sourceZ - targetZ) <= maxZ;
    }

    /** Angular width of the opposite band surface from the supplied width coordinate. */
    public double oppositeAngularWidthRadians(double cameraZ) {
        double distance = radius() * 2.0;
        double lower = Math.atan2(minWidthZ() - cameraZ, distance);
        double upper = Math.atan2(maxWidthZ() + 1.0 - cameraZ, distance);
        return upper - lower;
    }

    public double oppositeAngularWidthDegrees(double cameraZ) {
        return Math.toDegrees(oppositeAngularWidthRadians(cameraZ));
    }

    /**
     * Minecraft camera pitch required to look from one intrinsic pose toward
     * another after both points are embedded on the cylinder.
     *
     * <p>Negative pitch looks upward. The circumference delta is reduced to
     * its nearest periodic image, while Z remains an ordinary finite-width
     * offset.</p>
     */
    public double pitchDegreesToIntrinsic(double observerY, double targetY,
                                          double circumferenceDelta,
                                          double widthDelta) {
        double delta = shortestCircumferenceDelta(0.0, circumferenceDelta);
        double angle = Math.PI * 2.0 * delta / circumferenceBlocks;
        double observerRadius = physicalRadiusAt(observerY);
        double targetRadius = physicalRadiusAt(targetY);
        double tangent = targetRadius * Math.sin(angle);
        double vertical = observerRadius - targetRadius * Math.cos(angle);
        double horizontal = Math.hypot(tangent, widthDelta);
        return -Math.toDegrees(Math.atan2(vertical, horizontal));
    }
}
