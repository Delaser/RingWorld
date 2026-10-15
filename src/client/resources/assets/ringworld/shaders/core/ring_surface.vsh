#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldVertical;
uniform vec4 RingWorldCamera;
uniform vec4 RingWorldDistortion;

#moj_import <ringworld_projection.glsl>

out vec2 texCoord0;
out vec4 vertexColor;
out float intrinsicDistance;
out float handoffDistance;
out float intrinsicHeight;
out float intrinsicWidth;
flat out vec3 depthMapping;

const float TAU = 6.28318530717958647692;
const float FAR_BACKGROUND_DEPTH = 0.9999;

void main() {
    vec3 projected = ring_project_position(Position, RingWorldCamera.xyz);
    gl_Position = ProjMat * ModelViewMat * vec4(projected, 1.0);
    // Join perspective depth to an ordered far tail at the clipping boundary.
    float ndcOffset = ProjMat[2][2] / ProjMat[2][3];
    float ndcScale = ProjMat[3][2] - ndcOffset * ProjMat[3][3];

    const float windowScale = 0.5;
    const float windowOffset = 0.5;
    const float boundaryDepth = FAR_BACKGROUND_DEPTH * 0.5 + 0.5;
    const float farDepth = 1.0;

    vec2 nativeDepth = vec2(ndcOffset * windowScale + windowOffset,
                           ndcScale * windowScale);
    float boundaryW = nativeDepth.y / (boundaryDepth - nativeDepth.x);
    depthMapping = vec3(nativeDepth, (boundaryDepth - farDepth) * boundaryW);
    if (gl_Position.w > 0.0) {
        gl_Position.z = min(gl_Position.z, gl_Position.w * FAR_BACKGROUND_DEPTH);
    }

    float circumference = float(RingWorldLayout.y);
    float deltaX = mod(Position.x - RingWorldCamera.x + circumference * 0.5, circumference)
            - circumference * 0.5;
    intrinsicDistance = length(vec2(deltaX, Position.z - RingWorldCamera.z));
    intrinsicHeight = circumference / TAU + RingWorldVertical.x - Position.y;
    handoffDistance = length(vec2(intrinsicDistance, Position.y - RingWorldCamera.y));
    intrinsicWidth = Position.z;
    texCoord0 = UV0;
    vertexColor = Color;
}
