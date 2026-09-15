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
