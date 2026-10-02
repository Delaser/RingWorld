#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ModelOffset;
uniform ivec4 RingWorldLayout;

out vec2 texCoord0;
out vec4 vertexColor;
out float intrinsicDistance;
out float intrinsicHeight;
out float intrinsicWidth;
flat out vec3 depthMapping;

const float TAU = 6.28318530717958647692;
const float FAR_BACKGROUND_DEPTH = 0.9999;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
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
