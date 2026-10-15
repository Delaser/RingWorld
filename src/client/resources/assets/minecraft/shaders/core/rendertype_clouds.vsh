#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform ivec4 RingWorldLayout;
uniform vec4 RingWorldVertical;
uniform vec4 RingWorldCamera;
uniform vec4 RingWorldAtmosphere;
uniform vec4 RingWorldAtmosphere2;
uniform vec4 RingWorldCloudOffset;
uniform vec4 RingWorldDistortion;

#moj_import <ringworld_projection.glsl>

out vec2 texCoord0;
out float vertexDistance;
out vec4 vertexColor;
out float ringIntrinsicWorldZ;

const float TAU = 6.28318530717958647692;

void main() {
    vec3 shaderPosition = Position;
    vec3 flatPos = Position;
    ringIntrinsicWorldZ = 0.0;
    vertexColor = Color;
    if (RingWorldLayout.x != 0) {
        // Undo LevelRenderer's scale(12,1,12)/fractional translation,
        // curve in camera-local block coordinates, then invert the result so
        // the unchanged model-view matrix produces that curved position.
        flatPos = vec3(
            (Position.x - RingWorldCloudOffset.x) * 12.0,
            Position.y + RingWorldCloudOffset.y,
            (Position.z - RingWorldCloudOffset.z) * 12.0
        );
        vec3 curved = ring_project_position(RingWorldCamera.xyz + flatPos, RingWorldCamera.xyz);
        shaderPosition = vec3(
            curved.x / 12.0 + RingWorldCloudOffset.x,
            curved.y - RingWorldCloudOffset.y,
            curved.z / 12.0 + RingWorldCloudOffset.z
        );
        ringIntrinsicWorldZ = RingWorldCamera.z + flatPos.z;
        float visibility = 1.0 - smoothstep(
            RingWorldAtmosphere.w, RingWorldAtmosphere2.x,
            length(flatPos.xz));
        vertexColor.a *= visibility;
    }

    gl_Position = ProjMat * ModelViewMat * vec4(shaderPosition, 1.0);
    texCoord0 = UV0;
    vertexDistance = fog_distance(flatPos, FogShape);
}
