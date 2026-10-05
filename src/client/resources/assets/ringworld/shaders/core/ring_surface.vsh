#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:ringworld_projection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;
out float intrinsicDistance;
out float handoffDistance;
out float intrinsicHeight;
out float intrinsicWidth;
flat out vec3 depthMapping;

const float TAU = 6.28318530717958647692;
// The complete-ring surface is visual sky LOD, not ordinary world geometry.
// Keep its physical X/Y perspective, but prevent Minecraft's chunk-derived
// far plane from clipping large rings. A 16,384-block circumference has an
// approximately 4,950-block diameter while the normal 28-chunk level far
// plane is only about 1,792 blocks. Clamping clip-space Z leaves X/Y/W (and
// therefore apparent curvature) untouched. Vertices behind the eye retain
// normal frustum clipping. The 26.2 adapter supplies the reversed far boundary
// in ModelOffset.z, using the active backend's [-1,1] or [0,1] depth range.
const float FAR_BACKGROUND_DEPTH = 0.9999;

void main() {
    vec3 projected = Position;
    if (RingWorldDistortion.x > 0.5) {
        // U carries the original phase even where the old signed radius inverted.
        float phase = UV0.x * TAU;
        float signedRadius = dot(Position.xy, vec2(sin(phase), -cos(phase)));
        vec3 point = vec3(UV0.x * float(RingWorldLayout.y),
                float(RingWorldLayout.y) / TAU + RingWorldVertical.x - signedRadius, Position.z);
        projected = ring_trial_position(point, vec3(CameraBlockPos) - CameraOffset);
    }
    gl_Position = ProjMat * ModelViewMat * vec4(projected, 1.0);
    // Join perspective depth to an ordered far tail at the clipping boundary.
    float ndcOffset = ProjMat[2][2] / ProjMat[2][3];
    float ndcScale = ProjMat[3][2] - ndcOffset * ProjMat[3][3];
#ifdef RINGWORLD_REVERSED_DEPTH
    float windowScale = ModelOffset.z < 0.0 ? 0.5 : 1.0;
    float windowOffset = ModelOffset.z < 0.0 ? 0.5 : 0.0;
    float boundaryDepth = ModelOffset.z * windowScale + windowOffset;
    const float farDepth = 0.0;
#else
    const float windowScale = 0.5;
    const float windowOffset = 0.5;
    const float boundaryDepth = FAR_BACKGROUND_DEPTH * 0.5 + 0.5;
    const float farDepth = 1.0;
#endif
    vec2 nativeDepth = vec2(ndcOffset * windowScale + windowOffset,
                           ndcScale * windowScale);
    float boundaryW = nativeDepth.y / (boundaryDepth - nativeDepth.x);
    depthMapping = vec3(nativeDepth, (boundaryDepth - farDepth) * boundaryW);
    if (gl_Position.w > 0.0) {
#ifdef RINGWORLD_REVERSED_DEPTH
        gl_Position.z = max(gl_Position.z, gl_Position.w * ModelOffset.z);
#else
        gl_Position.z = min(
            gl_Position.z,
            gl_Position.w * FAR_BACKGROUND_DEPTH
        );
#endif
    }

    // Position.xy is the global cylinder and ModelOffset.x is the canonical
    // camera angle. atan(sin, cos) produces the shortest periodic angle even
    // at U=0/1, so the handoff cannot acquire a second seam.
    float vertexAngle = RingWorldDistortion.x > 0.5 ? UV0.x * TAU : atan(Position.x, -Position.y);
    float deltaAngle = atan(
        sin(vertexAngle - ModelOffset.x),
        cos(vertexAngle - ModelOffset.x)
    );
    float surfaceDistance = abs(deltaAngle) * float(RingWorldLayout.y) / TAU;
    intrinsicDistance = length(vec2(surfaceDistance, Position.z - ModelOffset.y));
    intrinsicHeight = RingWorldDistortion.x > 0.5
            ? dot(Position.xy, vec2(sin(UV0.x * TAU), -cos(UV0.x * TAU)))
            : length(Position.xy);
    // Native section traversal has a vertical range as well as an X/Z range.
    // Keep horizontal distance for materials; use canonical height for coverage.
    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;
    float worldY = RingWorldVertical.w - intrinsicHeight;
    handoffDistance = length(vec2(intrinsicDistance, worldY - cameraY));
    intrinsicWidth = Position.z;
    texCoord0 = UV0;
    vertexColor = Color;
}
