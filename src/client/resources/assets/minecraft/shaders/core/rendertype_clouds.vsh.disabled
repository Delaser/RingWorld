#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>

const int FLAG_MASK_DIR = 7;
const int FLAG_INSIDE_FACE = 1 << 4;
const int FLAG_USE_TOP_COLOR = 1 << 5;
const int FLAG_EXTRA_Z = 1 << 6;
const int FLAG_EXTRA_X = 1 << 7;
layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    vec3 CellSize;
};

uniform isamplerBuffer CloudFaces;

out float vertexDistance;
out vec4 vertexColor;
out float ringIntrinsicWorldZ;

const vec3[] vertices = vec3[](
    vec3(1, 0, 0), vec3(1, 0, 1), vec3(0, 0, 1), vec3(0, 0, 0),
    vec3(0, 1, 0), vec3(0, 1, 1), vec3(1, 1, 1), vec3(1, 1, 0),
    vec3(0, 0, 0), vec3(0, 1, 0), vec3(1, 1, 0), vec3(1, 0, 0),
    vec3(1, 0, 1), vec3(1, 1, 1), vec3(0, 1, 1), vec3(0, 0, 1),
    vec3(0, 0, 1), vec3(0, 1, 1), vec3(0, 1, 0), vec3(0, 0, 0),
    vec3(1, 0, 0), vec3(1, 1, 0), vec3(1, 1, 1), vec3(1, 0, 1)
);

const vec4[] faceColors = vec4[](
    vec4(0.7, 0.7, 0.7, 1.0), vec4(1.0, 1.0, 1.0, 1.0),
    vec4(0.8, 0.8, 0.8, 1.0), vec4(0.8, 0.8, 0.8, 1.0),
    vec4(0.9, 0.9, 0.9, 1.0), vec4(0.9, 0.9, 0.9, 1.0)
);

float ring_circumference() {
    return float(RingWorldLayout.y);
}

bool ring_active() {
    return RingWorldLayout.x != 0;
}

vec3 curve_cloud_position(vec3 pos) {
    float circumference = ring_circumference();
    float baseRadius = circumference / (2.0 * 3.14159265358979323846);
    float cameraX = float(CameraBlockPos.x) - CameraOffset.x;
    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;
    float cameraRadius = baseRadius + RingWorldVertical.x - cameraY;
    float vertexRadius = cameraRadius - pos.y;

    // Reconstruct canonical world phase before bending the cell. Reducing the
    // two phases to their shortest periodic delta keeps the seam exact and
    // makes every client project the same cylinder centred on the ring,
    // instead of creating an exaggerated camera-centred barrel.
    float cameraPhase = mod(cameraX, circumference);
    if (cameraPhase < 0.0) cameraPhase += circumference;
    float vertexPhase = mod(cameraX + pos.x, circumference);
    if (vertexPhase < 0.0) vertexPhase += circumference;
    float delta = vertexPhase - cameraPhase;
    if (delta > circumference * 0.5) delta -= circumference;
    if (delta < -circumference * 0.5) delta += circumference;
    float angle = 6.28318530717958647692 * delta / circumference;
    vec3 ringPosition = vec3(
        vertexRadius * sin(angle),
        cameraRadius - vertexRadius * cos(angle),
        pos.z
    );

    // This is the same exact radial model as terrain. The cloud base stays
    // attached to the wall-top plane while its curvature closes around the
    // physical ring centre rather than the current point of view.
    return ringPosition;
}

float ring_cloud_visibility(vec3 flatPos) {
    float horizontalDistance = length(flatPos.xz);
    return 1.0 - smoothstep(
        RingWorldAtmosphere.w,
        RingWorldAtmosphere2.x,
        horizontalDistance
    );
}

void main() {
    int quadVertex = gl_VertexID % 4;
    int index = (gl_VertexID / 4) * 3;

    int cellX = texelFetch(CloudFaces, index).r;
    int cellZ = texelFetch(CloudFaces, index + 1).r;
    int dirAndFlags = texelFetch(CloudFaces, index + 2).r;
    int direction = dirAndFlags & FLAG_MASK_DIR;
    bool isInsideFace = (dirAndFlags & FLAG_INSIDE_FACE) == FLAG_INSIDE_FACE;
    bool useTopColor = (dirAndFlags & FLAG_USE_TOP_COLOR) == FLAG_USE_TOP_COLOR;
    cellX = (cellX << 1) | ((dirAndFlags & FLAG_EXTRA_X) >> 7);
    cellZ = (cellZ << 1) | ((dirAndFlags & FLAG_EXTRA_Z) >> 6);
    vec3 faceVertex = vertices[(direction * 4) + (isInsideFace ? 3 - quadVertex : quadVertex)];
    vec3 pos = (faceVertex * CellSize) + (vec3(cellX, 0, cellZ) * CellSize) + CloudOffset;
    if (ring_active()) {
        float cameraY = float(CameraBlockPos.y) - CameraOffset.y;
        // Preserve vanilla's cell thickness while replacing only the deck's
        // base altitude. CloudOffset.y is the original camera-relative base.
        pos.y += (RingWorldVertical.z - cameraY) - CloudOffset.y;
    }
    vec3 flatPos = pos;
    ringIntrinsicWorldZ = ring_active()
        ? (float(CameraBlockPos.z) - CameraOffset.z) + flatPos.z
        : 0.0;
    if (ring_active()) {
        pos = curve_cloud_position(pos);
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    // Use the unbent deck for the normal radial cloud fade. Physical chord
    // distance shrinks again around a cylinder and was keeping the barrel's
    // far wall visible much longer than an ordinary cloud horizon.
    vertexDistance = fog_spherical_distance(flatPos);
    vertexColor = (useTopColor ? faceColors[1] : faceColors[direction]) * CloudColor;
    if (ring_active()) {
        vertexColor.a *= ring_cloud_visibility(flatPos);
    }
}
