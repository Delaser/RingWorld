#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out float intrinsicDistance;
layout(location = 3) out float intrinsicHeight;
layout(location = 4) out float intrinsicWidth;
layout(location = 5) flat out vec3 depthMapping;

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
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Unclipped perspective depth is offset + scale / clipW. Derive the
    // compression join from the actual clip boundary to avoid any band of
    // equal depths before the correction starts.
    // Projection uniforms are vertex-only in 26.3.
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
    float vertexAngle = atan(Position.x, -Position.y);
    float deltaAngle = atan(
        sin(vertexAngle - ModelOffset.x),
        cos(vertexAngle - ModelOffset.x)
    );
    float surfaceDistance = abs(deltaAngle) * float(RingWorldLayout.y) / TAU;
    intrinsicDistance = length(vec2(surfaceDistance, Position.z - ModelOffset.y));
    intrinsicHeight = length(Position.xy);
    intrinsicWidth = Position.z;
    texCoord0 = UV0;
    vertexColor = Color;
}
