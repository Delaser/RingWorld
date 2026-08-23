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
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldVertical;
uniform vec4 RingWorldCamera;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float ringIntrinsicDistance;

const float TAU = 6.28318530717958647692;
const float RING_FOG_DISTANCE_SCALE = 1.02;

vec3 cameraLocalRingPosition(vec3 vertexCanonical, vec3 cameraCanonical) {
    float circumference = float(RingWorldLayout.y);
    float baseRadius = circumference / TAU;
    float deltaAngle = TAU * (vertexCanonical.x - cameraCanonical.x) / circumference;
    float vertexRadius = baseRadius + RingWorldVertical.x - vertexCanonical.y;
    float cameraRadius = baseRadius + RingWorldVertical.x - cameraCanonical.y;
    return vec3(
        vertexRadius * sin(deltaAngle),
        cameraRadius - vertexRadius * cos(deltaAngle),
        vertexCanonical.z - cameraCanonical.z
    );
}

void main() {
    vec3 vanillaPos = Position + ChunkOffset;
    vec3 pos = vanillaPos;
    ringIntrinsicDistance = -1.0;
    if (RingWorldLayout.x != 0) {
        vec3 cameraCanonical = RingWorldCamera.xyz;
        vec3 vertexCanonical = cameraCanonical + vanillaPos;
        pos = cameraLocalRingPosition(vertexCanonical, cameraCanonical);
        ringIntrinsicDistance = length(vanillaPos.xz);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexDistance = RingWorldLayout.x == 0
        ? fog_distance(vanillaPos, FogShape)
        : RING_FOG_DISTANCE_SCALE * max(
            fog_distance(pos, FogShape), fog_distance(vanillaPos, FogShape));
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
