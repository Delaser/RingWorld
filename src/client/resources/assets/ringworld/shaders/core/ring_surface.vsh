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

const float TAU = 6.28318530717958647692;
const float FAR_BACKGROUND_DEPTH = 0.9999;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
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
    texCoord0 = UV0;
    vertexColor = Color;
}
