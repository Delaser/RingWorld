#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

// RingWorld
uniform int RingWorldActive;
uniform float RingWorldCircumference;
uniform float RingWorldSurfaceY;
uniform vec3 RingWorldCameraPos;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

const float PI = 3.14159265358979323846;

vec3 ring_position(vec3 vertexWorld, vec3 cameraWorld) {
    float radius = RingWorldCircumference / (2.0 * PI);

    float deltaAngle =
    2.0 * PI *
    (vertexWorld.x - cameraWorld.x) /
    RingWorldCircumference;

    float vertexRadius =
    radius + RingWorldSurfaceY - vertexWorld.y;

    float cameraRadius =
    radius + RingWorldSurfaceY - cameraWorld.y;

    return vec3(
    vertexRadius * sin(deltaAngle),
    cameraRadius - vertexRadius * cos(deltaAngle),
    vertexWorld.z - cameraWorld.z
    );
}

void main() {
    vec3 vanillaPos = Position + ChunkOffset;
    vec3 pos = vanillaPos;

    if (RingWorldActive != 0) {
    vec3 vertexWorld = vanillaPos + RingWorldCameraPos;
    pos = ring_position(vertexWorld, RingWorldCameraPos);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}